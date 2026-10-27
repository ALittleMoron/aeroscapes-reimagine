"""Build a complete CurseForge client ZIP only from reviewed channel pins."""

import argparse
import hashlib
import json
import re
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

from aeroscapes_pack.data import PackError, array, field, integer, read_json, read_toml, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.models import Project
from aeroscapes_pack.pack import SourcePack, check_project


@dataclass(frozen=True, slots=True)
class HostedFile:
    slug: str
    primary_project_id: str
    primary_sha512: str
    project_id: int
    file_id: int
    filename: str
    sha512: str
    sha1: str
    size: int


@dataclass(frozen=True, slots=True)
class BlockedFile:
    slug: str
    primary_project_id: str
    primary_sha512: str
    reason: str


@dataclass(frozen=True, slots=True)
class CurseForgeChannel:
    files: tuple[HostedFile | BlockedFile, ...]
    loader_id: str
    author: str

    def require_ready(self) -> None:
        blockers = [item for item in self.files if isinstance(item, BlockedFile)]
        if blockers:
            details = "\n".join(f"- {item.slug}: {item.reason}" for item in blockers)
            raise PackError(
                f"CurseForge distribution is blocked for {len(blockers)} files:\n{details}"
            )


def hash_value(record: dict[str, object], key: str, length: int) -> str:
    value = field(record, key, "CurseForge pin")
    if not re.fullmatch(f"[0-9a-f]{{{length}}}", value):
        raise PackError(f"CurseForge pin {key} must be an exact hexadecimal hash")
    return value


def parse_file(value: object) -> HostedFile | BlockedFile:
    record = table(value, "CurseForge artifact")
    identity = {
        "slug": field(record, "slug", "CurseForge artifact"),
        "primary_project_id": field(record, "primary_project_id", "CurseForge artifact"),
        "primary_sha512": hash_value(record, "primary_sha512", 128),
    }
    if record.get("status") == "blocked":
        return BlockedFile(**identity, reason=field(record, "reason", "CurseForge blocker"))
    if record.get("status") != "hosted":
        raise PackError("CurseForge artifact status must be hosted or blocked")
    project_id = integer(record.get("project_id"), "CurseForge project_id")
    file_id = integer(record.get("file_id"), "CurseForge file_id")
    size = integer(record.get("file_size"), "CurseForge file_size")
    filename = field(record, "filename", "CurseForge file")
    if (
        project_id == 0
        or file_id == 0
        or size == 0
        or PurePosixPath(filename).name != filename
        or filename in {".", ".."}
        or "\\" in filename
        or ":" in filename
        or any(ord(character) < 32 for character in filename)
    ):
        raise PackError("CurseForge pin needs positive IDs/size and a portable filename")
    return HostedFile(
        **identity,
        project_id=project_id,
        file_id=file_id,
        filename=filename,
        sha512=hash_value(record, "sha512", 128),
        sha1=hash_value(record, "sha1", 40),
        size=size,
    )


def load_channel(project: Project, source: SourcePack) -> CurseForgeChannel:
    record = table(read_json(project.root / "metadata/curseforge.json"), "CurseForge channel")
    if (
        integer(record.get("schema_version"), "CurseForge schema_version") != 1
        or record.get("minecraft") != source.manifest.minecraft
        or record.get("neoforge") != source.manifest.neoforge
        or record.get("loader_id") != f"neoforge-{source.manifest.neoforge}"
    ):
        raise PackError("CurseForge channel schema/platform differs from the reviewed source")
    files = tuple(
        parse_file(item) for item in array(record.get("artifacts"), "CurseForge artifacts")
    )
    by_project = {item.primary_project_id: item for item in files}
    dependencies = {item.project_id: item for item in source.dependencies.values()}
    if len(by_project) != len(files) or by_project.keys() != dependencies.keys():
        raise PackError("CurseForge mapping must cover every selected dependency exactly once")
    hosted_ids: set[int] = set()
    destinations: set[str] = set(source.overrides)
    for item in files:
        dependency = dependencies[item.primary_project_id]
        if item.primary_sha512 != dependency.sha512:
            raise PackError(f"CurseForge pin is stale for selected artifact: {item.slug}")
        if not isinstance(item, HostedFile):
            continue
        if item.project_id in hosted_ids:
            raise PackError("CurseForge manifest cannot reference a project more than once")
        hosted_ids.add(item.project_id)
        original = next(
            name
            for name, value in source.dependencies.items()
            if value.project_id == dependency.project_id
        )
        # CurseForge installs hosted mods/shaders by project class, not arbitrary pack paths.
        if PurePosixPath(original).parent.as_posix() not in {"mods", "shaderpacks"}:
            raise PackError(f"CurseForge cannot preserve this hosted installation path: {original}")
        destination = (PurePosixPath(original).parent / item.filename).as_posix()
        if destination in destinations:
            raise PackError(f"CurseForge installed filenames collide: {destination}")
        destinations.add(destination)
    return CurseForgeChannel(
        files=files,
        loader_id=field(record, "loader_id", "CurseForge channel"),
        author=field(read_toml(project.pack / "pack.toml"), "author", "pack"),
    )


def client_manifest(
    source: SourcePack, channel: CurseForgeChannel, project_id: int | None = None
) -> dict[str, object]:
    channel.require_ready()
    if project_id is not None and (isinstance(project_id, bool) or project_id <= 0):
        raise PackError("CurseForge project ID must be positive")
    dependencies = {item.project_id: item for item in source.dependencies.values()}
    manifest: dict[str, object] = {
        "minecraft": {
            "version": source.manifest.minecraft,
            "modLoaders": [{"id": channel.loader_id, "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": source.manifest.name,
        "version": source.manifest.version,
        "author": channel.author,
        "files": [
            {"projectID": item.project_id, "fileID": item.file_id, "required": True}
            for item in sorted(channel.files, key=lambda item: item.primary_project_id)
            if isinstance(item, HostedFile)
            and dependencies[item.primary_project_id].side != "server"
        ],
        "overrides": "overrides",
    }
    if project_id is not None:
        manifest["projectID"] = project_id
    return manifest


def archive_entries(
    source: SourcePack, channel: CurseForgeChannel, project_id: int | None
) -> dict[str, bytes]:
    manifest = client_manifest(source, channel, project_id)
    entries = {"overrides/" + name: content for name, content in source.overrides.items()}
    entries["manifest.json"] = (
        json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    ).encode()
    return entries


def verify_curseforge(
    path: Path, source: SourcePack, channel: CurseForgeChannel, project_id: int | None = None
) -> None:
    expected = archive_entries(source, channel, project_id)
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)) or set(names) != expected.keys():
            raise PackError("CurseForge archive contains extra, missing or duplicate entries")
        for name, content in expected.items():
            if archive.read(name) != content:
                raise PackError(f"CurseForge archive entry differs from reviewed sources: {name}")


def build_client_archive(project: Project, output: Path, project_id: int | None = None) -> Path:
    source = check_project(project)
    channel = load_channel(project, source)
    entries = archive_entries(source, channel, project_id)
    if output.exists():
        raise PackError(f"CurseForge output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".curseforge-", dir=output.parent) as directory:
        temporary = Path(directory) / "pack.zip"
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, content in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, content)
        verify_curseforge(temporary, source, channel, project_id)
        temporary.replace(output)
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Write a complete reviewed client ZIP")
    arguments = parser.parse_args()
    try:
        project = Project.discover(Path.cwd())
        source = check_project(project)
        channel = load_channel(project, source)
        channel.require_ready()
        if arguments.output is None:
            print(f"CurseForge client mapping ready for {source.manifest.version}")
        else:
            path = build_client_archive(project, arguments.output)
            checksum = hashlib.sha256(path.read_bytes()).hexdigest()
            print(f"Verified CurseForge client ZIP: {path} ({checksum})")
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
