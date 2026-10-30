"""Typed views of fields used by the tooling; source documents are never rewritten."""

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Self

from aeroscapes_pack.data import PackError, Table, array, field, integer, read_json, strings, table

type Side = Literal["client", "server", "both"]


def installation_side(value: object, context: str) -> Side:
    match value:
        case "client":
            return "client"
        case "server":
            return "server"
        case "both":
            return "both"
        case _:
            raise PackError(f"{context}: invalid installation side: {value!r}")


@dataclass(frozen=True, slots=True)
class Project:
    root: Path

    @property
    def pack(self) -> Path:
        return self.root / "pack"

    @property
    def cache(self) -> Path:
        return self.root / ".cache"

    @property
    def runtime(self) -> Path:
        return self.root / ".runtime"

    @classmethod
    def discover(cls, start: Path) -> Self:
        for candidate in (start.resolve(), *start.resolve().parents):
            if (candidate / "pack/pack.toml").is_file() and (
                candidate / "toolchain.json"
            ).is_file():
                return cls(candidate)
        raise PackError(f"No modpack project found above {start}; pass --root")


@dataclass(frozen=True, slots=True)
class Manifest:
    name: str
    version: str
    index_file: str
    index_algorithm: str
    index_hash: str
    minecraft: str
    neoforge: str

    @classmethod
    def parse(cls, record: Table) -> Self:
        index = table(record.get("index"), "pack.index")
        versions = table(record.get("versions"), "pack.versions")
        return cls(
            name=field(record, "name", "pack"),
            version=field(record, "version", "pack"),
            index_file=field(index, "file", "pack.index"),
            index_algorithm=field(index, "hash-format", "pack.index"),
            index_hash=field(index, "hash", "pack.index"),
            minecraft=field(versions, "minecraft", "pack.versions"),
            neoforge=field(versions, "neoforge", "pack.versions"),
        )


@dataclass(frozen=True, slots=True)
class IndexEntry:
    path: str
    algorithm: str
    checksum: str
    metafile: bool

    @classmethod
    def parse(cls, value: object, default_algorithm: str) -> Self:
        record = table(value, "index.files[]")
        metafile = record.get("metafile", False)
        if not isinstance(metafile, bool):
            raise PackError("index.files[].metafile: expected a boolean")
        return cls(
            path=field(record, "file", "index"),
            algorithm=field(record, "hash-format", "index")
            if "hash-format" in record
            else default_algorithm,
            checksum=field(record, "hash", "index"),
            metafile=metafile,
        )


@dataclass(frozen=True, slots=True)
class PinnedFile:
    name: str
    filename: str
    side: Side
    url: str
    sha512: str
    project_id: str
    version_id: str

    @classmethod
    def parse(cls, record: Table, context: str) -> Self:
        side = installation_side(record.get("side"), context)
        download = table(record.get("download"), f"{context}.download")
        if download.get("hash-format") != "sha512":
            raise PackError(f"{context}: dependency needs an exact SHA-512")
        update = table(record.get("update", {}), f"{context}.update")
        source = table(record.get("source", {}), f"{context}.source")
        if sum(("modrinth" in update, "curseforge" in update, "maven" in source)) != 1:
            raise PackError(
                f"{context}: need exactly one Modrinth, CurseForge or Maven source identity"
            )
        if "modrinth" in update:
            modrinth = table(update.get("modrinth"), f"{context}.update.modrinth")
            project_id = field(modrinth, "mod-id", context)
            version_id = field(modrinth, "version", context)
        elif "curseforge" in update:
            curseforge = table(update.get("curseforge"), f"{context}.update.curseforge")
            project = integer(curseforge.get("project-id"), f"{context}: CurseForge project-id")
            version = integer(curseforge.get("file-id"), f"{context}: CurseForge file-id")
            if project == 0 or version == 0:
                raise PackError(f"{context}: CurseForge project-id and file-id must be positive")
            project_id = f"curseforge:{project}"
            version_id = str(version)
        else:
            maven = table(source.get("maven"), f"{context}.source.maven")
            group = field(maven, "group", context)
            artifact = field(maven, "artifact", context)
            version_id = field(maven, "version", context)
            if not re.fullmatch(r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*", group) or not re.fullmatch(
                r"[A-Za-z0-9_][A-Za-z0-9_.-]*", artifact
            ):
                raise PackError(f"{context}: invalid Maven group or artifact")
            if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.+-]*", version_id):
                raise PackError(f"{context}: invalid Maven version")
            project_id = f"maven:{group}:{artifact}"
        return cls(
            name=field(record, "name", context),
            filename=field(record, "filename", context),
            side=side,
            url=field(download, "url", context),
            sha512=field(download, "hash", context),
            project_id=project_id,
            version_id=version_id,
        )


@dataclass(frozen=True, slots=True)
class ArtifactRequirement:
    project_id: str | None
    version_id: str | None

    @classmethod
    def parse(cls, record: Table) -> Self:
        project = record.get("project_id")
        version = record.get("version_id")
        if project is not None and (not isinstance(project, str) or not project):
            raise PackError("Artifact requirement project_id must be a string or null")
        if version is not None and (not isinstance(version, str) or not version):
            raise PackError("Artifact requirement version_id must be a string or null")
        return cls(project_id=project, version_id=version)


@dataclass(frozen=True, slots=True)
class Artifact:
    project_id: str
    version_id: str
    filename: str
    sha512: str
    sha1: str
    size: int
    side: Side
    kind: str
    release_type: str
    loaders: tuple[str, ...]
    game_versions: tuple[str, ...]
    requirements: tuple[ArtifactRequirement, ...]
    exception: str | None = None
    distribution: Literal["download", "bundle"] = "download"

    @classmethod
    def parse(cls, record: Table) -> Self:
        context = "artifact " + field(record, "project_id", "artifact")
        side = installation_side(record.get("side"), context)
        requirements = [table(item, context) for item in array(record.get("dependencies"), context)]
        distribution = record.get("distribution", "download")
        if not isinstance(distribution, str) or distribution not in {"download", "bundle"}:
            raise PackError(f"{context}: invalid export distribution")
        return cls(
            project_id=field(record, "project_id", context),
            version_id=field(record, "version_id", context),
            filename=field(record, "filename", context),
            sha512=field(record, "sha512", context),
            sha1=field(record, "sha1", context),
            size=integer(record.get("file_size"), context + ".file_size"),
            side=side,
            kind=field(record, "kind", context),
            release_type=field(record, "release_type", context),
            loaders=strings(record.get("loaders"), context + ".loaders"),
            game_versions=strings(record.get("game_versions"), context + ".game_versions"),
            requirements=tuple(
                ArtifactRequirement.parse(item)
                for item in requirements
                if item.get("dependency_type") == "required"
            ),
            exception=field(record, "exception", context) if "exception" in record else None,
            distribution="bundle" if distribution == "bundle" else "download",
        )


def included_artifacts(value: object) -> tuple[Artifact, ...]:
    records = [table(item, "artifacts[]") for item in array(value, "artifacts")]
    return tuple(Artifact.parse(record) for record in records if record.get("status") == "included")


def load_artifacts(project: Project) -> tuple[Artifact, ...]:
    return included_artifacts(read_json(project.root / "metadata/artifacts.json"))


@dataclass(frozen=True, slots=True)
class ToolDownload:
    filename: str
    url: str
    sha256: str

    @classmethod
    def parse(cls, value: object, context: str) -> Self:
        spec = table(value, context)
        return cls(
            filename=field(spec, "filename", context),
            url=field(spec, "url", context),
            sha256=field(spec, "sha256", context),
        )


@dataclass(frozen=True, slots=True)
class Toolchain:
    java_major: int
    packwiz_module: str
    packwiz_commit: str
    bootstrap: ToolDownload
    installer: ToolDownload
    neoforge: ToolDownload

    @classmethod
    def load(cls, project: Project) -> Self:
        record = table(read_json(project.root / "toolchain.json"), "toolchain")
        packwiz = table(record.get("packwiz"), "toolchain.packwiz")
        java = table(record.get("java"), "toolchain.java")
        java_major = integer(java.get("major"), "toolchain.java.major")
        if java_major == 0:
            raise PackError("toolchain.java.major must be positive")
        return cls(
            java_major=java_major,
            packwiz_module=field(packwiz, "module", "toolchain.packwiz"),
            packwiz_commit=field(packwiz, "commit", "toolchain.packwiz"),
            bootstrap=ToolDownload.parse(record.get("bootstrap"), "toolchain.bootstrap"),
            installer=ToolDownload.parse(record.get("installer"), "toolchain.installer"),
            neoforge=ToolDownload.parse(record.get("neoforge"), "toolchain.neoforge"),
        )
