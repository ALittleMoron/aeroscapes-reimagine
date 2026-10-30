"""Inspect published JAR metadata, including bundled NeoForge libraries."""

import io
import re
import zipfile
from collections.abc import Mapping
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from dataclasses import field as dataclass_field
from pathlib import Path

from aeroscapes_pack.data import PackError, array, field, json_value, table, toml_table
from aeroscapes_pack.files import digest, download, safe_path
from aeroscapes_pack.models import Artifact, Manifest, PinnedFile, Project
from aeroscapes_pack.pack import SourcePack

MAX_NESTED_DEPTH = 8
MAX_JAR_ENTRY_BYTES = 256 * 1024 * 1024
PLATFORM_IDS = frozenset({"minecraft", "neoforge", "java"})


@dataclass(frozen=True, slots=True)
class JarRequirement:
    owner: str
    dependency: str
    side: str
    version_range: str | None = None


def neoforge_version_key(value: str) -> tuple[tuple[int, ...], bool]:
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)*)(-beta)?", value)
    if match is None:
        raise PackError(f"Unsupported NeoForge version endpoint: {value!r}")
    components = [int(component) for component in match[1].split(".")]
    while len(components) > 1 and components[-1] == 0:
        components.pop()
    return tuple(components), match[2] is None


def neoforge_range_matches(expression: str | None, versions: tuple[str, ...]) -> bool:
    """Check numeric/beta Maven intervals used by the pinned NeoForge descriptors."""
    if not versions:
        raise PackError("NeoForge range check needs the installed version")
    candidates = tuple(neoforge_version_key(version) for version in versions)
    if expression is None:
        return True
    if not expression:
        return False
    remaining = expression.strip()
    if not remaining or remaining[0] not in "[(":
        # Maven treats a bare recommended version, including '*', as unrestricted.
        return True
    accepted = False
    seen_range = False
    previous_upper = None
    previous_upper_inclusive = False
    while remaining:
        match = re.match(r"([\[(])([^\[\]()]*)([\])])", remaining)
        if match is None:
            raise PackError(f"Unsupported NeoForge range: {expression!r}")
        bounds = match[2].split(",")
        lower_inclusive = match[1] == "["
        upper_inclusive = match[3] == "]"
        if len(bounds) == 1 and match[1] == "[" and match[3] == "]":
            lower = upper = neoforge_version_key(bounds[0].strip())
        elif len(bounds) == 2:
            lower_text, upper_text = (bound.strip() for bound in bounds)
            lower = neoforge_version_key(lower_text) if lower_text else None
            upper = neoforge_version_key(upper_text) if upper_text else None
        else:
            raise PackError(f"Unsupported NeoForge range: {expression!r}")
        if (
            lower is not None
            and upper is not None
            and (lower > upper or lower == upper and not (lower_inclusive and upper_inclusive))
        ):
            raise PackError(f"Invalid NeoForge range: {expression!r}")
        if seen_range and (
            previous_upper is None
            or lower is None
            or previous_upper > lower
            or previous_upper == lower
            and previous_upper_inclusive
            and lower_inclusive
        ):
            raise PackError(f"Invalid overlapping NeoForge ranges: {expression!r}")
        accepted |= any(
            (lower is None or candidate > lower or candidate == lower and lower_inclusive)
            and (upper is None or candidate < upper or candidate == upper and upper_inclusive)
            for candidate in candidates
        )
        previous_upper = upper
        previous_upper_inclusive = upper_inclusive
        seen_range = True
        remaining = remaining[match.end() :].strip()
        if remaining.startswith(","):
            remaining = remaining[1:].strip()
    return accepted


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
                JarRequirement(
                    owner=owner,
                    dependency=field(entry, "modId", owner),
                    side=side,
                    version_range=field(entry, "versionRange", owner)
                    if "versionRange" in entry
                    else None,
                )
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
    directory: Path,
    artifacts: tuple[Artifact, ...],
    overrides: Mapping[str, bytes],
    *,
    manifest: Manifest,
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
        versions = (manifest.neoforge,)
        if (manifest.minecraft, manifest.neoforge) == ("1.21.1", "21.1.230"):
            # This exact platform's FML 4.0.42 also accepts the 1.21 compatibility version.
            versions += ("21.0.166",)
        for requirement in installed.requirements:
            if requirement.dependency != "neoforge" or requirement.side not in {
                "BOTH",
                side.upper(),
            }:
                continue
            try:
                compatible = neoforge_range_matches(requirement.version_range, versions)
            except PackError as error:
                raise PackError(f"{requirement.owner} on {side}: {error}") from error
            if not compatible:
                raise PackError(
                    f"{requirement.owner} requires NeoForge {requirement.version_range} "
                    f"on {side}; installed {manifest.neoforge}"
                )
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
