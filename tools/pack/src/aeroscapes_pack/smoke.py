"""Bounded POSIX headless load/save check; not an interactive gameplay test."""

import argparse
import json
import os
import re
import selectors

# The headless check owns this child and its session; all shutdown paths are bounded.
import subprocess  # nosec B404
import sys
import time
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

from aeroscapes_pack.data import PackError, read_toml
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.models import Manifest, PinnedFile, Project, Toolchain
from aeroscapes_pack.runtime import java_binary, terminate_group

READY_MESSAGE = re.compile(rb'Done \([0-9.]+s\)! For help, type "help"')


@dataclass(frozen=True, slots=True)
class CaptureSettings:
    timeout_seconds: float = 180
    stop_grace_seconds: float = 15
    max_log_bytes: int = 32 * 1024 * 1024
    ready_marker: str | None = None


@dataclass(frozen=True, slots=True)
class CaptureResult:
    ready: bool
    exit_code: int
    output: str


def eula_accepted(path: Path) -> bool:
    settings: dict[str, str] = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.lstrip().startswith(("#", "!")):
                continue
            key, separator, value = line.partition("=")
            if separator:
                settings[key.strip()] = value.strip().lower()
    return settings.get("eula") == "true"


def stop_process(process: subprocess.Popen[bytes], grace_seconds: float) -> None:
    if process.poll() is None:
        try:
            if process.stdin is not None:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
            process.wait(timeout=grace_seconds)
        except BrokenPipeError, BlockingIOError, subprocess.TimeoutExpired:
            pass  # Escalate only this owned group after graceful shutdown failed.
    terminate_group(process)


def capture_process(
    arguments: Sequence[str], directory: Path, commands: Sequence[str], settings: CaptureSettings
) -> CaptureResult:
    if os.name != "posix":
        raise PackError("The headless pipe/process-group check currently supports POSIX only")
    # Fixed/resolved Java arguments in production; fake children exercise this boundary in tests.
    process = subprocess.Popen(  # nosec B603
        arguments,
        cwd=directory,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        bufsize=0,
        start_new_session=True,
    )
    output = bytearray()
    try:
        ready = capture_output(process, output, commands, settings)
    finally:
        try:
            stop_process(process, settings.stop_grace_seconds)
        finally:
            for stream in (process.stdin, process.stdout):
                if stream is not None:
                    stream.close()
            (directory / "smoke.log").write_bytes(output)
    return CaptureResult(ready, process.returncode, output.decode(errors="replace"))


def capture_output(
    process: subprocess.Popen[bytes],
    output: bytearray,
    commands: Sequence[str],
    settings: CaptureSettings,
) -> bool:
    reader, writer = process.stdout, process.stdin
    if reader is None or writer is None:
        raise PackError("Capture requires stdin/stdout pipes")
    ready = False
    server_ready = False
    marker = settings.ready_marker.encode() if settings.ready_marker is not None else None
    marker_ready = marker is None
    pending_line = 0
    command_bytes = b""
    command_offset = 0
    deadline = time.monotonic() + settings.timeout_seconds
    os.set_blocking(writer.fileno(), False)
    with selectors.DefaultSelector() as selector:
        selector.register(reader, selectors.EVENT_READ)
        while selector.get_map() or process.poll() is None:
            if time.monotonic() > deadline:
                raise PackError(f"Smoke check exceeded {settings.timeout_seconds}s")
            for key, _events in selector.select(timeout=0.1):
                if key.fileobj is writer:
                    try:
                        written = writer.write(
                            command_bytes[command_offset : command_offset + 65536]
                        )
                    except BrokenPipeError:
                        selector.unregister(writer)
                        continue  # Exit code and save marker determine the child's failure.
                    except BlockingIOError:
                        continue
                    command_offset += written or 0
                    if command_offset == len(command_bytes):
                        selector.unregister(writer)
                else:
                    chunk = reader.read(min(65536, settings.max_log_bytes - len(output) + 1))
                    if chunk:
                        output.extend(chunk)
                    else:
                        selector.unregister(reader)
            if len(output) > settings.max_log_bytes:
                raise PackError(f"Smoke log exceeds {settings.max_log_bytes} bytes")
            server_ready = server_ready or READY_MESSAGE.search(output, pending_line) is not None
            marker_ready = marker_ready or (marker is not None and marker in output[pending_line:])
            if not ready and server_ready and marker_ready:
                ready = True
                command_bytes = ("\n".join([*commands, "save-all flush", "stop"]) + "\n").encode()
                selector.register(writer, selectors.EVENT_WRITE)
            pending_line = output.rfind(b"\n") + 1
    if len(output) > settings.max_log_bytes:
        raise PackError(f"Smoke log exceeds {settings.max_log_bytes} bytes")
    return ready


def expected_packs(project: Project) -> tuple[str, ...]:
    directory = project.pack / "global_packs/required_data"
    folders = [
        path.name
        for path in directory.iterdir()
        if path.is_dir() and (path / "pack.mcmeta").is_file()
    ]
    files = [
        PinnedFile.parse(read_toml(path), str(path)).filename
        for path in directory.glob("*.pw.toml")
    ]
    return tuple(folders + files)


def write_result(project: Project, directory: Path, capture: CaptureResult) -> bool:
    """Report readiness, saving and every logged error without exceptions."""
    errors = [
        line for line in capture.output.splitlines() if "/ERROR]" in line or "/FATAL]" in line
    ]
    listed = "\n".join(
        line for line in capture.output.splitlines() if "data pack(s) enabled:" in line
    )
    missing = [name for name in expected_packs(project) if f"[{name} (Global)]" not in listed]
    saved = "All dimensions are saved" in capture.output
    report = {
        "ready": capture.ready,
        "jvm_exit": capture.exit_code,
        "saved_all_dimensions": saved,
        "errors": errors,
        "missing_datapacks": missing,
    }
    (directory / "smoke-result.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )
    return capture.ready and capture.exit_code == 0 and saved and not errors and not missing


def run(
    project: Project,
    directory: Path,
    java: str | None,
    commands: Sequence[str],
    settings: CaptureSettings,
) -> bool:
    directory = directory.expanduser().resolve()
    if not directory.is_relative_to(project.runtime.resolve()):
        raise PackError("Use a disposable directory under .runtime; never a player save")
    (directory / "smoke-result.json").unlink(missing_ok=True)
    if not eula_accepted(directory / "eula.txt"):
        raise PackError("The owner must accept the Minecraft EULA before a complete launch")
    manifest = Manifest.parse(read_toml(project.pack / "pack.toml"))
    arguments = directory / f"libraries/net/neoforged/neoforge/{manifest.neoforge}/unix_args.txt"
    capture = capture_process(
        [
            java_binary(java, Toolchain.load(project).java_major),
            "@user_jvm_args.txt",
            "@" + str(arguments),
            "nogui",
        ],
        directory,
        ["datapack list enabled", *commands],
        settings,
    )
    return write_result(project, directory, capture)


class Options(argparse.Namespace):
    root: Path | None = None
    directory: Path
    java: str | None = None
    commands: Path | None = None
    timeout: int = 180
    ready_marker: str | None = None


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--java")
    parser.add_argument("--commands", type=Path, help="Commands for a disposable test world")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument(
        "--ready-marker", help="Wait for this additional log marker before commands and shutdown"
    )
    options = Options()
    parser.parse_args(argv, namespace=options)
    try:
        project = (
            Project(options.root.expanduser().resolve())
            if options.root
            else Project.discover(Path.cwd())
        )
        commands = (
            options.commands.read_text(encoding="utf-8").splitlines() if options.commands else []
        )
        success = run(
            project,
            options.directory,
            options.java,
            commands,
            CaptureSettings(timeout_seconds=options.timeout, ready_marker=options.ready_marker),
        )
        print(
            f"Log: {options.directory / 'smoke.log'}\n"
            f"Result: {options.directory / 'smoke-result.json'}"
        )
        if not success:
            raise PackError(
                "Headless load/save check failed or logged errors; "
                "inspect smoke-result.json and smoke.log"
            )
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130
    return 0
