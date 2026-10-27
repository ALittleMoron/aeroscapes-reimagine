"""Command-line boundary: arguments, progress and expected error presentation."""

import argparse
import sys
import tempfile
from collections.abc import Sequence
from pathlib import Path

from aeroscapes_pack.artifacts import audit_jars, fetch_artifacts
from aeroscapes_pack.data import PackError
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.files import digest
from aeroscapes_pack.models import Project, load_artifacts
from aeroscapes_pack.pack import SourcePack, check_project, verify_mrpack
from aeroscapes_pack.release import archive_fingerprint
from aeroscapes_pack.runtime import install_server, packwiz, run_packwiz
from aeroscapes_pack.source_archive import write_source_archive


class Options(argparse.Namespace):
    command: str
    root: Path | None = None
    target: Path | None = None
    java: str | None = None
    directory: Path | None = None
    fetch: bool = False
    file: Path | None = None


def arguments(argv: Sequence[str] | None) -> Options:
    parser = argparse.ArgumentParser(description="Build and verify pinned modpack sources")
    parser.add_argument("--root", type=Path, help="Project root (otherwise discover from cwd)")
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("bootstrap", "refresh", "check", "build", "source-archive"):
        commands.add_parser(name)
    server = commands.add_parser("test-server", help="Install a disposable loopback data test")
    server.add_argument("--target", type=Path, required=True)
    server.add_argument("--java")
    audit = commands.add_parser("audit", help="Verify artifacts and declared JAR dependencies")
    location = audit.add_mutually_exclusive_group(required=True)
    location.add_argument("--directory", type=Path)
    location.add_argument("--fetch", action="store_true")
    exported = commands.add_parser("verify-mrpack", help="Compare an export with source locks")
    exported.add_argument("file", type=Path)
    options = Options()
    parser.parse_args(argv, namespace=options)
    return options


def summary(source: SourcePack) -> str:
    return (
        f"OK: lock hashes, data syntax, {len(source.dependencies)} exact artifacts "
        "and dependency closure"
    )


def build(project: Project, source: SourcePack) -> None:
    output = project.root / "dist" / f"aeroscapes-reimagine-{source.manifest.version}.mrpack"
    output.parent.mkdir(exist_ok=True)
    artifacts = load_artifacts(project)
    checksum_path = output.with_suffix(".mrpack.sha256")
    with tempfile.TemporaryDirectory(dir=output.parent, prefix=".build-") as scratch:
        candidate = Path(scratch) / output.name
        # Native host restrictions bundle reviewed external files with their declared side.
        print(
            run_packwiz(project, "modrinth", "export", "--output", str(candidate)),
            end="",
        )
        verify_mrpack(candidate, source, artifacts)
        fingerprint = archive_fingerprint(candidate)
        checksum = digest(candidate)
        staged_checksum = candidate.with_suffix(".mrpack.sha256")
        staged_checksum.write_text(f"{checksum}  {output.name}\n", encoding="utf-8")
        # On publication failure a missing checksum is safer than a stale one.
        checksum_path.unlink(missing_ok=True)
        candidate.replace(output)
        staged_checksum.replace(checksum_path)
    print(f"Built: {output}\nSHA-256: {checksum}\nContent SHA-256: {fingerprint}")


def execute(options: Options, project: Project) -> None:
    if options.command == "source-archive":
        print(f"Corresponding source: {write_source_archive(project)}")
        return
    if options.command == "bootstrap":
        print(packwiz(project))
        return
    if options.command == "refresh":
        print(run_packwiz(project, "refresh"), end="")
    source = check_project(project)
    print(summary(source))
    match options.command:
        case "build":
            build(project, source)
        case "test-server" if options.target is not None:
            target = install_server(project, options.target, options.java)
            print(
                f"Server installed: {target}\n"
                "Owner EULA acceptance is required before a full launch."
            )
        case "audit":
            audit(project, source, options)
        case "verify-mrpack" if options.file is not None:
            artifacts = load_artifacts(project)
            verify_mrpack(options.file, source, artifacts)
            print(
                f"OK: exported archive, {len(source.dependencies)} dependencies "
                f"and {len(source.overrides)} exact overrides"
            )


def audit(project: Project, source: SourcePack, options: Options) -> None:
    directory = fetch_artifacts(project, source) if options.fetch else options.directory
    if directory is None:
        raise PackError("Choose --directory or --fetch")
    artifacts = load_artifacts(project)
    for side, count in audit_jars(directory, artifacts, source.overrides).items():
        print(f"OK: {side}: {count} declared mod IDs including bundled libraries")
    print("Artifact hashes and dependency presence verified; NeoForge checks ranges at launch.")


def main(argv: Sequence[str] | None = None) -> int:
    try:
        options = arguments(argv)
        project = (
            Project(options.root.expanduser().resolve())
            if options.root
            else Project.discover(Path.cwd())
        )
        execute(options, project)
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130
    return 0
