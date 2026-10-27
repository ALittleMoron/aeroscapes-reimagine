"""Bounded external tools and disposable local test installation."""

import contextlib
import functools
import http.server
import os
import re
import shutil
import signal

# Required to run pinned build tools; reviewed argv/timeout boundaries below.
import subprocess  # nosec B404
import tempfile
import threading
import time
from collections.abc import Generator, Mapping, Sequence
from pathlib import Path

from aeroscapes_pack.data import PackError
from aeroscapes_pack.files import download, new_target, safe_path
from aeroscapes_pack.models import Project, Toolchain

TOOL_TIMEOUT_SECONDS = 600


def group_alive(process_id: int) -> bool:
    try:
        os.killpg(process_id, 0)
    except ProcessLookupError:
        return False
    return True


def terminate_group(process: subprocess.Popen[bytes]) -> None:
    """Clean up the owned session even if its leader has already exited."""
    for termination_signal in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(process.pid, termination_signal)
        except ProcessLookupError:
            return
        deadline = time.monotonic() + 2
        while group_alive(process.pid) and time.monotonic() < deadline:
            if process.poll() is None:
                try:
                    process.wait(timeout=0.05)
                except subprocess.TimeoutExpired:
                    continue
            else:
                time.sleep(0.05)
        if not group_alive(process.pid):
            break
    process.wait(timeout=2)


def run_command(
    arguments: Sequence[str],
    *,
    directory: Path | None = None,
    environment: Mapping[str, str] | None = None,
    timeout_seconds: float = TOOL_TIMEOUT_SECONDS,
) -> subprocess.CompletedProcess[str]:
    if os.name != "posix":
        raise PackError("External build tools currently support POSIX only")
    # Pinned tools can be verbose: stream to disk rather than collecting output in memory.
    # Controlled argv arrays, no shell expansion; executable paths are resolved explicitly.
    with (
        tempfile.TemporaryFile() as output,
        tempfile.TemporaryFile() as errors,
    ):
        process = subprocess.Popen(  # nosec B603
            arguments,
            cwd=directory,
            env=environment,
            stdout=output,
            stderr=errors,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
        )
        try:
            process.wait(timeout=timeout_seconds)
        finally:
            terminate_group(process)
        output.seek(0)
        errors.seek(0)
        result = subprocess.CompletedProcess(
            arguments,
            process.returncode,
            stdout=output.read().decode(errors="replace"),
            stderr=errors.read().decode(errors="replace"),
        )
        result.check_returncode()
        return result


def executable(name: str) -> str:
    resolved = shutil.which(name)
    if resolved is None:
        raise PackError(f"Required executable not found: {name}")
    return resolved


def java_binary(value: str | None, major: int) -> str:
    requested = str(Path(value).expanduser()) if value is not None else "java"
    binary = str(Path(executable(requested)).resolve())
    result = run_command([binary, "-version"], timeout_seconds=15)
    if not re.search(rf'version "{major}[.\"]', result.stdout + result.stderr):
        raise PackError(f"Use Java {major} for this pack")
    return binary


def packwiz(project: Project) -> Path:
    tools = Toolchain.load(project)
    module = tools.packwiz_module
    commit = tools.packwiz_commit
    binary = project.cache / "tools" / "packwiz"
    go = executable("go")
    if not binary.exists():
        binary.parent.mkdir(parents=True, exist_ok=True)
        environment = os.environ | {
            "GOBIN": str(binary.parent),
            "GOCACHE": str(project.cache / "go-build"),
            "GOPATH": str(project.cache / "go-path"),
        }
        run_command([go, "install", f"{module}@{commit}"], environment=environment)
    info = run_command([go, "version", "-m", str(binary)]).stdout
    if commit[:12] not in info:
        raise PackError(
            f"Wrong packwiz revision in {binary}; remove this cached binary and bootstrap"
        )
    return binary


def run_packwiz(project: Project, *arguments: str) -> str:
    result = run_command(
        [
            str(packwiz(project)),
            "--cache",
            str(project.cache / "packwiz"),
            "--pack-file",
            str(project.pack / "pack.toml"),
            *arguments,
        ],
        directory=project.pack,
    )
    return result.stdout + result.stderr


@contextlib.contextmanager
def serve_pack(project: Project) -> Generator[str]:
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(project.pack))
    # Deliberate HTTP exception: ephemeral loopback URL supplied only to the local Java installer.
    with http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}/pack.toml"
        finally:
            server.shutdown()
            thread.join(timeout=5)


def download_tools(project: Project, specs: Toolchain) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for name, spec in (
        ("bootstrap", specs.bootstrap),
        ("installer", specs.installer),
        ("neoforge", specs.neoforge),
    ):
        target = safe_path(project.cache / "tools", spec.filename)
        result[name] = download(spec.url, target, spec.sha256)
    return result


def install_server(project: Project, target: Path, java: str | None) -> Path:
    if not target.expanduser().resolve().is_relative_to(project.runtime.resolve()):
        raise PackError(
            "Test-server installation is restricted to disposable directories under .runtime"
        )
    specs = Toolchain.load(project)
    binary = java_binary(java, specs.java_major)
    tools = download_tools(project, specs)
    target = new_target(target)
    with serve_pack(project) as url:
        run_command(
            [
                binary,
                "-Djava.awt.headless=true",
                "-jar",
                str(tools["bootstrap"]),
                "--bootstrap-no-update",
                "--bootstrap-main-jar",
                str(tools["installer"]),
                "-g",
                "-s",
                "server",
                url,
            ],
            directory=target,
        )
    run_command([binary, "-jar", str(tools["neoforge"]), "--installServer"], directory=target)
    (target / "user_jvm_args.txt").write_text("-Xms1G\n-Xmx6G\n", encoding="utf-8")
    (target / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\n"
        "level-name=world\nlevel-type=minecraft:normal\ngamemode=survival\n"
        "difficulty=normal\nallow-flight=true\nview-distance=8\nsimulation-distance=6\n",
        encoding="utf-8",
    )
    return target
