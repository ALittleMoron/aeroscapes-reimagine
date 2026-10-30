"""Read-only source validation and comparison with an official packwiz export."""

import hashlib
import re
import zipfile
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from types import MappingProxyType
from urllib.parse import urlsplit

from aeroscapes_pack.adoption import check_adoption
from aeroscapes_pack.data import (
    PackError,
    array,
    field,
    json_value,
    read_json,
    read_toml,
    strings,
    table,
)
from aeroscapes_pack.files import digest, require_https, safe_path
from aeroscapes_pack.models import (
    Artifact,
    IndexEntry,
    Manifest,
    PinnedFile,
    Project,
    Toolchain,
    load_artifacts,
)
from aeroscapes_pack.native_templates import check_native_template_upgrades
from aeroscapes_pack.runtime_patches import check_runtime_patches
from aeroscapes_pack.source_archive import check_source_archive

# Download-host policy of the pinned packwiz Modrinth exporter.
MODRINTH_DOWNLOAD_HOSTS = frozenset(
    {"cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com"}
)

# Owner selected these exact experimental files; approval does not extend to updates.
RELEASE_EXCEPTIONS = frozenset(
    {
        ("kkmrDlKT", "6e8GCrLb", "beta"),
        ("YL57xq9U", "KduFYu4t", "beta"),
        ("COlSi5iR", "avwWYPbG", "alpha"),
        ("qtPMklut", "7rX3dlFf", "alpha"),
        ("qyVF9oeo", "85fA7L1W", "beta"),
        ("xFCYuAs8", "Js4y2r4d", "beta"),
        ("u58R1TMW", "IITF0PRC", "beta"),
    }
)
COMPAT_DIRECTORY = "global_packs/required_data/aeroscapes_reimagine_compat"


@dataclass(frozen=True, slots=True)
class SourcePack:
    manifest: Manifest
    dependencies: Mapping[str, PinnedFile]
    overrides: Mapping[str, bytes]


def read_index(directory: Path, manifest: Manifest) -> tuple[IndexEntry, ...]:
    path = safe_path(directory, manifest.index_file)
    if digest(path, manifest.index_algorithm) != manifest.index_hash:
        raise PackError("Stale pack.toml index hash; refresh after intentional edits")
    index = read_toml(path)
    algorithm = field(index, "hash-format", "index")
    return tuple(
        IndexEntry.parse(item, algorithm) for item in array(index.get("files"), "index.files")
    )


def validate_dependency(dependency: PinnedFile, context: str) -> None:
    require_https(dependency.url)
    if not re.fullmatch("[0-9a-f]{128}", dependency.sha512):
        raise PackError(f"{context}: dependency needs an exact SHA-512")


def read_source(directory: Path) -> SourcePack:
    manifest = Manifest.parse(read_toml(directory / "pack.toml"))
    dependencies: dict[str, PinnedFile] = {}
    overrides: dict[str, bytes] = {}
    indexed: set[str] = set()
    projects: set[str] = set()
    for entry in read_index(directory, manifest):
        path = safe_path(directory, entry.path)
        if entry.path in indexed:
            raise PackError(f"Duplicate index entry: {entry.path}")
        indexed.add(entry.path)
        if digest(path, entry.algorithm) != entry.checksum:
            raise PackError(f"Stale file hash: {entry.path}")
        if entry.metafile:
            dependency = PinnedFile.parse(read_toml(path), entry.path)
            validate_dependency(dependency, entry.path)
            destination = str(PurePosixPath(entry.path).parent / dependency.filename)
            safe_path(directory, destination)
            if destination in dependencies or dependency.project_id in projects:
                raise PackError(f"Duplicate dependency destination or project: {destination}")
            dependencies[destination] = dependency
            projects.add(dependency.project_id)
        else:
            validate_data_file(path)
            overrides[entry.path] = path.read_bytes()
    collisions = dependencies.keys() & overrides.keys()
    if collisions:
        raise PackError(f"Dependency and override destinations overlap: {sorted(collisions)}")
    destinations = dependencies.keys() | overrides.keys()
    for destination in sorted(destinations):
        for parent in PurePosixPath(destination).parents:
            if parent.as_posix() in destinations:
                raise PackError(
                    f"Installation destinations require both a file and a directory: "
                    f"{parent} and {destination}"
                )
    actual = {
        path.relative_to(directory).as_posix()
        for path in directory.rglob("*")
        if path.is_file()
        and path.relative_to(directory).as_posix()
        not in {"pack.toml", manifest.index_file, ".packwizignore"}
    }
    if indexed != actual:
        raise PackError(f"Unindexed or missing files: {sorted(indexed ^ actual)}")
    source = SourcePack(manifest, MappingProxyType(dependencies), MappingProxyType(overrides))
    validate_resourcepacks(source)
    return source


def validate_resourcepacks(source: SourcePack) -> None:
    """Reject stale default resourcepack selections before exporting a new profile."""
    try:
        lines = source.overrides.get("options.txt", b"").decode("utf-8").splitlines()
    except UnicodeError as error:
        raise PackError(f"options.txt: invalid UTF-8: {error}") from error
    for line in lines:
        key, _, value = line.partition(":")
        if key != "resourcePacks":
            continue
        context = "options.txt resourcePacks"
        for identifier in strings(json_value(value, context), context):
            if not identifier.startswith("file/"):
                continue
            destination = "resourcepacks/" + identifier.removeprefix("file/")
            dependency = source.dependencies.get(destination)
            if dependency is not None and dependency.side in {"client", "both"}:
                continue
            if destination in source.overrides or destination + "/pack.mcmeta" in source.overrides:
                continue
            raise PackError(f"Selected resource pack is not installed on the client: {identifier}")


def validate_data_file(path: Path) -> None:
    if path.suffix == ".json" or path.name == "pack.mcmeta":
        read_json(path)
    elif path.suffix == ".toml":
        read_toml(path)


def audit_metadata(source: SourcePack, artifacts: tuple[Artifact, ...]) -> None:
    included = {artifact.project_id: artifact for artifact in artifacts}
    if len(included) != len(artifacts):
        raise PackError("Duplicate project in artifact metadata")
    dependencies = {
        dependency.project_id: dependency for dependency in source.dependencies.values()
    }
    if included.keys() != dependencies.keys():
        raise PackError("Artifact audit and packwiz metadata disagree")
    for project_id, dependency in dependencies.items():
        artifact = included[project_id]
        if (artifact.version_id, artifact.sha512, artifact.side, artifact.filename) != (
            dependency.version_id,
            dependency.sha512,
            dependency.side,
            dependency.filename,
        ):
            raise PackError(f"Artifact identity/hash/side mismatch: {dependency.name}")
        bundled = artifact.distribution == "bundle"
        if bundled != (urlsplit(dependency.url).hostname not in MODRINTH_DOWNLOAD_HOSTS):
            raise PackError(f"Re-evaluate export distribution for download host: {dependency.name}")
        validate_release(artifact)
        if artifact.kind == "mod" and "neoforge" not in artifact.loaders:
            bridge = included.get("u58R1TMW")
            api = included.get("Aqlf1Shp")
            if (
                "fabric" not in artifact.loaders
                or (artifact.project_id, artifact.version_id)
                not in {("Effrh3ze", "9It7IoUE"), ("yd14I2wN", "m4E3YdvX")}
                or artifact.side != "client"
                or bridge is None
                or bridge.version_id != "IITF0PRC"
                or bridge.side not in {"client", "both"}
                or api is None
                or api.side not in {"client", "both"}
            ):
                raise PackError(f"Fabric mod without its client bridge: {dependency.name}")
        if source.manifest.minecraft not in artifact.game_versions:
            raise PackError(f"Unsupported Minecraft version: {dependency.name}")
        validate_requirements(artifact, included, dependency.name)


def validate_requirements(artifact: Artifact, included: Mapping[str, Artifact], name: str) -> None:
    for requirement in artifact.requirements:
        if requirement.project_id is None:
            if requirement.version_id is None:
                raise PackError(f"Unresolved required dependency of {name}: no project or version")
            if not any(target.version_id == requirement.version_id for target in included.values()):
                raise PackError(f"Missing required version of {name}: {requirement.version_id}")
            continue
        target = included.get(requirement.project_id)
        if target is None:
            raise PackError(f"Missing dependency of {name}: {requirement.project_id}")
        if requirement.version_id is not None and target.version_id != requirement.version_id:
            raise PackError(f"Wrong exact dependency of {name}: {requirement.project_id}")


def validate_release(artifact: Artifact) -> None:
    if artifact.release_type == "release":
        return
    approved = (
        artifact.project_id,
        artifact.version_id,
        artifact.release_type,
    ) in RELEASE_EXCEPTIONS
    if not approved or not bool(artifact.exception):
        raise PackError(f"Unapproved experimental dependency: {artifact.project_id}")


def check_compat(
    directory: Path,
    fixes: object,
    artifacts: tuple[Artifact, ...],
    *,
    owned: Mapping[str, str] | None = None,
    sources: Mapping[str, str] | None = None,
) -> None:
    versions = {artifact.project_id: artifact.version_id for artifact in artifacts}
    versions.update(owned or {})
    covered: set[str] = set()
    for item in array(fixes, "compat_fixes"):
        fix = table(item, "compat_fixes[]")
        targets = table(fix.get("targets"), "compat.targets")
        if not targets:
            raise PackError("Compatibility fix must bind at least one exact target version")
        for project_id in targets:
            field(targets, project_id, "compat.targets")
        if any(versions.get(project_id) != version for project_id, version in targets.items()):
            raise PackError(f"Re-evaluate compatibility fix: {field(fix, 'reason', 'compat')}")
        source_targets = table(fix.get("source_targets", {}), "compat.source_targets")
        for project_id in source_targets:
            field(source_targets, project_id, "compat.source_targets")
        if any(
            (sources or {}).get(project_id) != version
            for project_id, version in source_targets.items()
        ):
            raise PackError(f"Re-evaluate compatibility source: {field(fix, 'reason', 'compat')}")
        for name in strings(fix.get("files"), "compat.files"):
            safe_path(directory, name)
            if name in covered:
                raise PackError(f"Duplicate compatibility binding: {name}")
            covered.add(name)
    actual = {
        path.relative_to(directory).as_posix()
        for path in directory.rglob("*")
        if path.is_file()
        and (path.suffix in {".json", ".mcfunction", ".nbt"} or path.name == "pack.mcmeta")
    }
    if actual != covered:
        raise PackError(f"Unbound compatibility data: {sorted(actual ^ covered)}")


def check_project(project: Project) -> SourcePack:
    source = read_source(project.pack)
    artifacts = load_artifacts(project)
    audit_metadata(source, artifacts)
    owned, sources = check_adoption(project, source.overrides)
    check_source_archive(project, source.overrides)
    check_compat(
        project.pack / COMPAT_DIRECTORY,
        read_json(project.root / "metadata/compat_fixes.json"),
        artifacts,
        owned=owned,
        sources=sources,
    )
    check_native_template_upgrades(project, source.manifest, artifacts)
    check_runtime_patches(project, source.manifest, artifacts, source.overrides)
    installer = Toolchain.load(project).neoforge
    version = source.manifest.neoforge
    filename = f"neoforge-{version}-installer.jar"
    expected_url = (
        f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/{filename}"
    )
    if installer.filename != filename or installer.url != expected_url:
        raise PackError("NeoForge installer and pack.toml versions disagree")
    return source


def exported_dependencies(source: SourcePack, artifacts: tuple[Artifact, ...]) -> dict[str, object]:
    records = {artifact.project_id: artifact for artifact in artifacts}
    result: dict[str, object] = {}
    for destination, dependency in source.dependencies.items():
        artifact = records[dependency.project_id]
        if artifact.distribution == "bundle":
            continue
        result[destination] = {
            "hashes": {"sha512": dependency.sha512, "sha1": artifact.sha1},
            "fileSize": artifact.size,
            "env": {
                "client": "unsupported" if dependency.side == "server" else "required",
                "server": "unsupported" if dependency.side == "client" else "required",
            },
            "downloads": [dependency.url],
        }
    return result


def verify_mrpack(path: Path, source: SourcePack, artifacts: tuple[Artifact, ...]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        overrides = {"overrides/" + name: content for name, content in source.overrides.items()}
        bundled: dict[str, Artifact] = {}
        records = {artifact.project_id: artifact for artifact in artifacts}
        for destination, dependency in source.dependencies.items():
            artifact = records[dependency.project_id]
            if artifact.distribution == "bundle":
                prefix = {
                    "client": "client-overrides",
                    "server": "server-overrides",
                    "both": "overrides",
                }[artifact.side]
                bundled[f"{prefix}/{destination}"] = artifact
        if overrides.keys() & bundled.keys():
            raise PackError("Bundled dependency and override destinations overlap")
        expected = {"modrinth.index.json", *overrides, *bundled}
        if len(names) != len(set(names)) or set(names) != expected:
            raise PackError("Export contains extra, missing or duplicate archive entries")
        manifest = table(
            json_value(archive.read("modrinth.index.json"), str(path)), "mrpack manifest"
        )
        verify_export_manifest(manifest, source, artifacts)
        for name, content in overrides.items():
            if archive.read(name) != content:
                raise PackError(f"Exported override differs: {name}")
        for name, artifact in bundled.items():
            if archive.getinfo(name).file_size != artifact.size:
                raise PackError(f"Bundled artifact size differs: {name}")
            content = archive.read(name)
            if (
                hashlib.sha512(content).hexdigest() != artifact.sha512
                or hashlib.sha1(content, usedforsecurity=False).hexdigest() != artifact.sha1
            ):
                raise PackError(f"Bundled artifact hash differs: {name}")


def verify_export_manifest(
    manifest: dict[str, object], source: SourcePack, artifacts: tuple[Artifact, ...]
) -> None:
    expected_platform = {
        "minecraft": source.manifest.minecraft,
        "neoforge": source.manifest.neoforge,
    }
    if manifest.get("dependencies") != expected_platform:
        raise PackError("Exported platform differs from pack.toml")
    identity = (
        manifest.get("formatVersion"),
        manifest.get("game"),
        manifest.get("name"),
        manifest.get("versionId"),
    )
    if identity != (1, "minecraft", source.manifest.name, source.manifest.version):
        raise PackError("Exported format or pack identity differs")
    files = [
        table(value, "mrpack.files[]") for value in array(manifest.get("files"), "mrpack.files")
    ]
    exported = {
        field(item, "path", "mrpack.files[]"): {
            key: value for key, value in item.items() if key != "path"
        }
        for item in files
    }
    if len(exported) != len(files) or exported != exported_dependencies(source, artifacts):
        raise PackError("Exported files, hashes, sizes, URLs or sides differ from the lock")
