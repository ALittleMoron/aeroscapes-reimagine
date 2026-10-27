"""Keep local JSON repair recipes tied to their reviewed providers and shipped code."""

import io
import re
import zipfile
from collections.abc import Mapping

from aeroscapes_pack.data import PackError, array, field, read_json, table
from aeroscapes_pack.files import safe_path
from aeroscapes_pack.models import Artifact, Manifest, Project


def check_runtime_patches(
    project: Project,
    platform: Manifest,
    artifacts: tuple[Artifact, ...],
    overrides: Mapping[str, bytes],
) -> None:
    inventory = project.root / "metadata/runtime_data_patches.json"
    if not inventory.is_file():
        return
    record = table(read_json(inventory), "runtime data patches")
    if (record.get("minecraft"), record.get("neoforge")) != (
        platform.minecraft,
        platform.neoforge,
    ):
        raise PackError("Re-audit runtime data patches for the changed platform")
    pins = {artifact.project_id: artifact for artifact in artifacts}
    providers: dict[str, str] = {}
    for value in array(record.get("providers"), "runtime patch providers"):
        provider = table(value, "runtime patch provider")
        identifier = field(provider, "project_id", "runtime patch provider")
        artifact = pins.get(identifier)
        if artifact is None or (
            artifact.version_id,
            artifact.filename,
            artifact.sha512,
        ) != (provider.get("version_id"), provider.get("filename"), provider.get("sha512")):
            raise PackError(f"Re-audit changed runtime patch provider: {identifier}")
        providers[identifier] = artifact.version_id
    source_name = field(record, "patch_resource", "runtime data patches")
    source = safe_path(project.root, source_name)
    recipes: dict[str, dict[str, object]] = {}
    for value in array(read_json(source), "runtime patch recipes"):
        row = table(value, "runtime patch")
        identifier = field(row, "id", "runtime patch")
        if identifier in recipes:
            raise PackError(f"Duplicate runtime patch recipe: {identifier}")
        recipes[identifier] = row
    seen: set[str] = set()
    for value in array(record.get("resources"), "runtime patch resources"):
        resource = table(value, "runtime patch resource")
        identifier = field(resource, "id", "runtime patch resource")
        recipe = recipes.get(identifier)
        if identifier in seen or recipe is None:
            raise PackError(f"Missing or duplicate runtime patch: {identifier}")
        seen.add(identifier)
        if providers.get(field(resource, "provider", "runtime patch resource")) != resource.get(
            "version"
        ):
            raise PackError(f"Unbound runtime patch provider: {identifier}")
        for key in ("source_sha256", "result_json_sha256"):
            if not re.fullmatch("[0-9a-f]{64}", field(resource, key, "runtime patch resource")):
                raise PackError(f"Invalid runtime patch digest: {identifier}")
        if (recipe.get("source"), recipe.get("sha256")) != (
            resource.get("source"),
            resource.get("source_sha256"),
        ):
            raise PackError(f"Changed runtime patch source: {identifier}")
        namespace, separator, path = identifier.partition(":")
        if not separator:
            raise PackError(f"Invalid runtime patch resource ID: {identifier}")
        suffix = f"/data/{namespace}/{path}"
        if any(("/" + name).endswith(suffix) for name in overrides):
            raise PackError(f"Runtime repaired resource must not be distributed: {identifier}")
    if seen != recipes.keys():
        raise PackError("Uninventoried runtime data patch recipes")
    binary = overrides.get("mods/aeroscapes-reimagine-0.1.0.jar")
    if binary is None:
        raise PackError("Runtime data patches require their owned mod")
    with zipfile.ZipFile(io.BytesIO(binary)) as archive:
        if archive.read("aeroscapes_reimagine/data-patches.json") != source.read_bytes():
            raise PackError("Runtime patch recipes differ from the shipped mod")
