"""Bundle the editable owned-mod project beside its exact shipped binary."""

import hashlib
import io
import json
import tempfile
import zipfile
from collections.abc import Mapping
from pathlib import Path

from aeroscapes_pack.data import PackError, field, read_json, table
from aeroscapes_pack.files import safe_path
from aeroscapes_pack.models import Project

SOURCE_ARCHIVE = "licenses/aeroscapes-reimagine-source.zip"
MOD_PROJECT = "mods/aeroscapes-reimagine"
REQUIRED_INPUTS = (
    "README.md",
    "LICENSE",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/verification-metadata.xml",
    "config/pmd.xml",
    "src/main/resources/META-INF/NOTICE.txt",
)
PACK_INPUTS = (
    "pack/config/simplebackups-common.toml",
    "pack/licenses/THIRD-PARTY-NOTICES.txt",
)
PACK_INPUT_TREES = (
    "pack/resourcepacks/aeroscapes-reimagine-atmosphere",
    "pack/resourcepacks/aeroscapes-reimagine-refined-compat",
)


def _source_path(project: Project, name: str) -> Path:
    path = project.root
    for component in Path(name).parts:
        path /= component
        if path.is_symlink():
            raise PackError(f"Symlinked corresponding source input: {path}")
    return path


def source_inputs(project: Project) -> dict[str, bytes]:
    inputs: dict[str, bytes] = {}
    required = (
        *(f"{MOD_PROJECT}/{name}" for name in REQUIRED_INPUTS),
        "metadata/worldgen_adoption.json",
        "metadata/runtime_data_patches.json",
        "metadata/native_template_upgrades.json",
        *PACK_INPUTS,
    )
    for name in required:
        path = _source_path(project, name)
        if not path.is_file():
            raise PackError(f"Missing corresponding source input: {path}")
        inputs[name] = path.read_bytes()
    for name in (
        *(f"{MOD_PROJECT}/{name}" for name in ("src", "config", "gradle")),
        *PACK_INPUT_TREES,
    ):
        subtree = _source_path(project, name)
        if not subtree.is_dir():
            raise PackError(f"Missing corresponding source input: {subtree}")
        for path in (subtree, *sorted(subtree.rglob("*"))):
            if path.is_symlink():
                raise PackError(f"Symlinked corresponding source input: {path}")
            if path.is_file():
                inputs[path.relative_to(project.root).as_posix()] = path.read_bytes()
    return inputs


def source_archive_bytes(project: Project) -> bytes:
    inventory = table(read_json(project.root / "metadata/worldgen_adoption.json"), "adoption")
    jar_name = field(inventory, "jar", "adoption")
    binary = safe_path(project.pack, jar_name).read_bytes()
    inputs = source_inputs(project)
    manifest = {
        "format": 1,
        "binary": {"path": f"pack/{jar_name}", "sha256": hashlib.sha256(binary).hexdigest()},
        "build_instructions": f"{MOD_PROJECT}/README.md",
        "files": {
            name: hashlib.sha256(content).hexdigest() for name, content in sorted(inputs.items())
        },
    }
    inputs["SOURCE-MANIFEST.json"] = (
        json.dumps(manifest, indent=2, sort_keys=True) + "\n"
    ).encode()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(inputs.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (0o100755 if name.endswith("/gradlew") else 0o100644) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, content, compresslevel=9)
    return output.getvalue()


def write_source_archive(project: Project) -> Path:
    content = source_archive_bytes(project)
    destination = project.pack / SOURCE_ARCHIVE
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent, prefix=".source-") as scratch:
        candidate = Path(scratch) / "source.zip"
        candidate.write_bytes(content)
        candidate.replace(destination)
    return destination


def check_source_archive(project: Project, overrides: Mapping[str, bytes]) -> None:
    if not (project.root / "metadata/worldgen_adoption.json").is_file():
        return
    if overrides.get(SOURCE_ARCHIVE) != source_archive_bytes(project):
        raise PackError(
            "Missing or stale corresponding source archive; "
            "run make source-archive and make refresh"
        )
