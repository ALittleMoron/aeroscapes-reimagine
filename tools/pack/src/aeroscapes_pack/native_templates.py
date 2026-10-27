"""Bind native runtime STRUCTURE DFU to its verified platform, source pin and audit."""

import hashlib
import re
from pathlib import Path

from aeroscapes_pack.data import PackError, Table, array, field, integer, read_json, table
from aeroscapes_pack.files import safe_path
from aeroscapes_pack.models import Artifact, Manifest, Project


def _checksum(record: Table, key: str, length: int) -> str:
    value = field(record, key, "native template upgrade")
    if not re.fullmatch(f"[0-9a-f]{{{length}}}", value):
        raise PackError(f"Native template {key} needs an exact SHA-{length * 4}")
    return value


def _check_template(directory: Path, value: object, data_version: int) -> tuple[str, str]:
    record = table(value, "native template upgrade.templates[]")
    identifier = field(record, "id", "native template")
    name = field(record, "path", "native template")
    safe_path(directory, name)
    namespace, separator, resource = identifier.partition(":")
    if (
        namespace != "nova_structures"
        or not separator
        or not re.fullmatch("[a-z0-9_/.-]+", resource)
        or name != f"data/{namespace}/structure/{resource}.nbt"
    ):
        raise PackError(f"Native template ID/path mismatch: {identifier}: {name}")
    output_version = integer(
        record.get("output_data_version"), "native template.output_data_version"
    )
    if output_version != data_version:
        raise PackError(f"Wrong native template data version: {identifier}")
    # Source entries were verified against the pinned JAR during native staging.
    # Source-only checks retain their exact digests without fetching that JAR.
    _checksum(record, "original_sha256", 64)
    _checksum(record, "output_sha256", 64)
    _checksum(record, "output_sha512", 128)
    _checksum(record, "native_dfu_typed_nbt_sha256", 64)
    _checksum(record, "loaded_template_nbt_sha256", 64)
    return identifier, name


def check_native_template_upgrades(
    project: Project, platform: Manifest, artifacts: tuple[Artifact, ...]
) -> None:
    directory = project.pack / "global_packs/required_data/aeroscapes_reimagine_compat"
    actual = {
        path.relative_to(project.pack).as_posix()
        for path in project.pack.rglob("*.nbt")
        if path.is_file()
        and "/data/nova_structures/structure/" in "/" + path.relative_to(project.pack).as_posix()
    }
    metadata = project.root / "metadata/native_template_upgrades.json"
    if not metadata.is_file():
        if actual:
            raise PackError(
                "Native template outputs require metadata/native_template_upgrades.json"
            )
        return
    record = table(read_json(metadata), "native template upgrades")
    if field(record, "strategy", "native template upgrades") != "minecraft-runtime-dfu":
        raise PackError("Native template strategy must use verified Minecraft runtime DFU")
    if actual:
        raise PackError(
            f"Runtime DFU forbids redistributed native template copies: {sorted(actual)}"
        )
    expected_platform = (
        field(record, "minecraft", "native template upgrades"),
        field(record, "neoforge", "native template upgrades"),
    )
    if expected_platform != ("1.21.1", "21.1.230") or expected_platform != (
        platform.minecraft,
        platform.neoforge,
    ):
        raise PackError("Re-evaluate native template upgrades for the changed platform")
    data_version = integer(record.get("target_data_version"), "native target_data_version")
    if data_version != 3955:
        raise PackError("Native template upgrades require verified target data version 3955")
    project_id = field(record, "upstream_project_id", "native template upgrades")
    version_id = field(record, "upstream_version_id", "native template upgrades")
    upstream_hash = _checksum(record, "upstream_sha512", 128)
    upstream = next((artifact for artifact in artifacts if artifact.project_id == project_id), None)
    if (
        project_id != "tpehi7ww"
        or upstream is None
        or (upstream.version_id, upstream.sha512) != (version_id, upstream_hash)
    ):
        raise PackError("Re-evaluate native template upgrades for the changed upstream artifact")
    identifiers: set[str] = set()
    covered: set[str] = set()
    for value in array(record.get("templates"), "native template upgrades.templates"):
        identifier, name = _check_template(directory, value, data_version)
        if identifier in identifiers or name in covered:
            raise PackError(f"Duplicate native template binding: {identifier}: {name}")
        identifiers.add(identifier)
        covered.add(name)
    if integer(record.get("count"), "native template upgrades.count") != len(covered):
        raise PackError("Native template upgrade count differs from its records")
    canonical = "\n".join(sorted(identifiers)) + "\n"
    if hashlib.sha256(canonical.encode()).hexdigest() != _checksum(
        record, "template_ids_sha256", 64
    ):
        raise PackError("Native template ID digest differs from the verified record set")
