"""Validate incorporated JSON and Java behavior-reference bindings against shipped bytes.

Historical entry hashes are provenance assertions tied to the exact artifact pin;
the normal check validates outputs without downloading the excluded source archive.
"""

import hashlib
import io
import re
import zipfile
import zlib
from collections import Counter
from collections.abc import Generator, Mapping
from contextlib import contextmanager

from aeroscapes_pack.data import PackError, array, field, json_value, read_json, table, toml_table
from aeroscapes_pack.files import safe_path
from aeroscapes_pack.models import Project


def checksum(record: dict[str, object], key: str, length: int) -> str:
    value = field(record, key, "worldgen adoption")
    if not re.fullmatch(f"[0-9a-f]{{{length}}}", value):
        raise PackError(f"Invalid adoption checksum: {key}")
    return value


@contextmanager
def checked_archive(content: bytes, jar_name: str) -> Generator[zipfile.ZipFile]:
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            duplicates = sorted(
                entry_name for entry_name, count in Counter(archive.namelist()).items() if count > 1
            )
            if duplicates:
                raise PackError(f"Duplicate entries in owned JAR {jar_name}: {duplicates}")
            yield archive
    except zipfile.BadZipFile as error:
        raise PackError(f"Invalid owned JAR {jar_name}: {error}") from error


def is_adopted_json(name: str) -> bool:
    return name.endswith(".json") and name.startswith(("data/aeroscapes/", "data/minecraft/"))


def read_member(archive: zipfile.ZipFile, path: str, *, jar_name: str) -> bytes:
    try:
        return archive.read(path)
    except (zipfile.BadZipFile, NotImplementedError, RuntimeError, EOFError, zlib.error) as error:
        raise PackError(f"Cannot read owned JAR {jar_name} entry {path}: {error}") from error


def check_adoption(
    project: Project, overrides: Mapping[str, bytes]
) -> tuple[dict[str, str], dict[str, str]]:
    metadata = project.root / "metadata/worldgen_adoption.json"
    if not metadata.is_file():
        for name, content in overrides.items():
            if name.startswith("mods/") and name.endswith(".jar"):
                with checked_archive(content, name) as archive:
                    if any(is_adopted_json(name) for name in archive.namelist()):
                        raise PackError("Owned worldgen resources require an adoption inventory")
        return {}, {}
    record = table(read_json(metadata), "worldgen adoption")
    sources = check_source_identity(project, record.get("source"))
    name = field(record, "jar", "adoption")
    safe_path(project.pack, name)
    if name not in overrides:
        raise PackError("Missing owned adoption JAR")
    with checked_archive(overrides[name], name) as archive:
        owned = check_descriptor(archive, record, name)
        check_resources(project, archive, record.get("resources"), jar_name=name)
    check_java(project, record.get("java"))
    return owned, sources


def check_source_identity(project: Project, value: object) -> dict[str, str]:
    source = table(value, "adoption.source")
    identifier = field(source, "project_id", "adoption.source")
    version = field(source, "version_id", "adoption.source")
    source_hash = checksum(source, "sha512", 128)
    history = [
        table(item, "artifacts[]")
        for item in array(read_json(project.root / "metadata/artifacts.json"), "artifacts")
    ]
    matches = [item for item in history if item.get("project_id") == identifier]
    if len(matches) != 1 or (matches[0].get("version_id"), matches[0].get("sha512")) != (
        version,
        source_hash,
    ):
        raise PackError("Re-evaluate adoption source identity")
    return {identifier: version}


def check_descriptor(
    archive: zipfile.ZipFile, record: dict[str, object], name: str
) -> dict[str, str]:
    descriptor_path = "META-INF/neoforge.mods.toml"
    if descriptor_path not in archive.namelist():
        raise PackError(f"Missing owned descriptor in {name}: {descriptor_path}")
    try:
        content = read_member(archive, descriptor_path, jar_name=name).decode("utf-8")
    except UnicodeError as error:
        raise PackError(f"Invalid owned descriptor in {name}: {error}") from error
    descriptor = toml_table(content, f"{name}:{descriptor_path}")
    mod_id = field(record, "mod_id", "adoption")
    mod_version = field(record, "mod_version", "adoption")
    mods = [table(item, "mods[]") for item in array(descriptor.get("mods"), "mods")]
    if not any(item.get("modId") == mod_id and item.get("version") == mod_version for item in mods):
        raise PackError("Re-evaluate adoption owned version")
    return {"owned:" + mod_id: mod_version}


def check_resources(
    project: Project, archive: zipfile.ZipFile, value: object, *, jar_name: str
) -> None:
    covered: set[str] = set()
    for item in array(value, "adoption.resources"):
        binding = table(item, "adoption.resource")
        output = check_binding(project, binding)
        if output in covered or output not in archive.namelist():
            raise PackError(f"Missing or duplicate adopted resource: {output}")
        content = read_member(archive, output, jar_name=jar_name)
        json_value(content, output)
        if hashlib.sha256(content).hexdigest() != checksum(binding, "sha256", 64):
            raise PackError(f"Changed adopted resource: {output}")
        covered.add(output)
    actual = {name for name in archive.namelist() if is_adopted_json(name)}
    if covered != actual:
        raise PackError(f"Unbound adopted resources: {sorted(covered ^ actual)}")


def check_java(project: Project, value: object) -> None:
    for item in array(value, "adoption.java"):
        binding = table(item, "adoption.java[]")
        output = check_binding(project, binding, java_reference=True)
        path = safe_path(project.root, output)
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != checksum(
            binding, "sha256", 64
        ):
            raise PackError(f"Changed adopted Java: {output}")


def check_binding(
    project: Project, binding: dict[str, object], *, java_reference: bool = False
) -> str:
    source = field(binding, "source", "adoption.binding")
    output = field(binding, "output", "adoption.binding")
    safe_path(project.root, source)
    safe_path(project.root, output)
    before = checksum(binding, "source_sha256", 64)
    after = checksum(binding, "sha256", 64)
    mode = field(binding, "mode", "adoption.binding")
    field(binding, "reason", "adoption.binding")
    if mode == "exact":
        if before != after:
            raise PackError(f"Exact adoption changed source: {output}")
    elif mode != "adapted" and not (java_reference and mode == "reference"):
        raise PackError(f"Invalid adoption mode: {mode}")
    return output
