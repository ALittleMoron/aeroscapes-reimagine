"""Inspect published JAR metadata, including bundled NeoForge libraries."""

import io
import zipfile
from collections.abc import Mapping
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from dataclasses import field as dataclass_field
from pathlib import Path

from aeroscapes_pack.data import PackError, array, field, json_value, table, toml_table
from aeroscapes_pack.files import digest, download, safe_path
from aeroscapes_pack.models import Artifact, PinnedFile, Project
from aeroscapes_pack.pack import SourcePack

MAX_NESTED_DEPTH = 8
MAX_JAR_ENTRY_BYTES = 256 * 1024 * 1024
PLATFORM_IDS = frozenset({"minecraft", "neoforge", "java"})


@dataclass(frozen=True, slots=True)
class JarRequirement:
    owner: str
    dependency: str
    side: str


def read_entry(archive: zipfile.ZipFile, name: str) -> bytes:
    try:
        entry = archive.getinfo(name)
        if entry.file_size > MAX_JAR_ENTRY_BYTES:
            raise PackError(f"JAR entry exceeds {MAX_JAR_ENTRY_BYTES} bytes: {name}")
        with archive.open(entry) as stream:
            content = stream.read(MAX_JAR_ENTRY_BYTES + 1)
        if len(content) > MAX_JAR_ENTRY_BYTES:
            raise PackError(f"Decompressed JAR entry too large: {name}")
        return content
    except (KeyError, NotImplementedError, zipfile.BadZipFile) as error:
        raise PackError(f"Invalid or missing JAR entry {name}: {error}") from error


def jar_mods(content: bytes, depth: int = 0) -> tuple[set[str], list[JarRequirement]]:
    if depth > MAX_NESTED_DEPTH:
        raise PackError("Excessive nested JAR depth")
    identifiers: set[str] = set()
    requirements: list[JarRequirement] = []
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        manifest = "META-INF/neoforge.mods.toml"
        if manifest in archive.namelist():
            data = toml_table(read_entry(archive, manifest).decode("utf-8"), manifest)
            identifiers, requirements = declared_mods(data)
        nested_metadata = "META-INF/jarjar/metadata.json"
        if nested_metadata in archive.namelist():
            nested = table(
                json_value(read_entry(archive, nested_metadata), nested_metadata), nested_metadata
            )
            for item in array(nested.get("jars"), nested_metadata):
                name = field(table(item, nested_metadata), "path", nested_metadata)
                nested_ids, nested_requirements = jar_mods(read_entry(archive, name), depth + 1)
                identifiers.update(nested_ids)
                requirements.extend(nested_requirements)
    return identifiers, requirements


def declared_mods(data: dict[str, object]) -> tuple[set[str], list[JarRequirement]]:
    identifiers = {
        field(table(item, "mods[]"), "modId", "mods[]")
        for item in array(data.get("mods", []), "mods")
    }
    dependencies = table(data.get("dependencies", {}), "dependencies")
    requirements: list[JarRequirement] = []
    for owner in identifiers:
        entries = dependencies.get(owner, [])
        for item in array(entries, f"dependencies.{owner}"):
            entry = table(item, f"dependencies.{owner}[]")
            # FML 4 defaults an omitted type to required; legacy mandatory is ignored.
            dependency_type = field(entry, "type", owner) if "type" in entry else "required"
            if dependency_type.lower() != "required":
                continue
            side = field(entry, "side", owner).upper() if "side" in entry else "BOTH"
            if side not in {"BOTH", "CLIENT", "SERVER"}:
                raise PackError(f"Invalid dependency side for {owner}: {side}")
            requirements.append(
                JarRequirement(owner=owner, dependency=field(entry, "modId", owner), side=side)
            )
    return identifiers, requirements


@dataclass(slots=True)
class InstallationSide:
    identifiers: set[str] = dataclass_field(default_factory=lambda: set(PLATFORM_IDS))
    requirements: list[JarRequirement] = dataclass_field(default_factory=list)


def add_jar(
    content: bytes, name: str, side: str, installations: Mapping[str, InstallationSide]
) -> None:
    try:
        identifiers, requirements = jar_mods(content)
    except (PackError, zipfile.BadZipFile, UnicodeError) as error:
        raise PackError(f"Invalid JAR {name}: {error}") from error
    for installed_side, installed in installations.items():
        if side in {installed_side, "both"}:
            installed.identifiers.update(identifiers)
            installed.requirements.extend(requirements)


def audit_jars(
    directory: Path, artifacts: tuple[Artifact, ...], overrides: Mapping[str, bytes]
) -> dict[str, int]:
    installations = {side: InstallationSide() for side in ("client", "server")}
    for artifact in artifacts:
        path = safe_path(directory, artifact.filename)
        if not path.exists():
            path = safe_path(directory, artifact.sha512 + "/" + artifact.filename)
        if digest(path, "sha512") != artifact.sha512:
            raise PackError(f"Artifact SHA-512 mismatch: {path}")
        if artifact.kind != "mod":
            continue
        add_jar(path.read_bytes(), str(path), artifact.side, installations)
    # Owned mods ship as packwiz overrides; their bytes are pinned by the pack index.
    for name, content in overrides.items():
        if name.startswith("mods/") and name.endswith(".jar"):
            add_jar(content, name, "both", installations)
    for side, installed in installations.items():
        missing = [
            (requirement.owner, requirement.dependency)
            for requirement in installed.requirements
            if requirement.side in {"BOTH", side.upper()}
            and requirement.dependency not in installed.identifiers
        ]
        if missing:
            raise PackError(f"Missing declared JAR dependencies on {side}: {missing}")
    return {
        side: len(installed.identifiers - PLATFORM_IDS) for side, installed in installations.items()
    }


def fetch_artifacts(project: Project, source: SourcePack) -> Path:
    directory = project.cache / "artifacts"

    def fetch(dependency: PinnedFile) -> Path:
        target = safe_path(directory, dependency.sha512 + "/" + dependency.filename)
        return download(dependency.url, target, dependency.sha512, "sha512")

    with ThreadPoolExecutor(max_workers=6) as executor:
        list(executor.map(fetch, source.dependencies.values()))
    return directory
