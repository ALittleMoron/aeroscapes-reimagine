"""Recipients receive the matching, editable owned-mod project with the binary."""

import hashlib
import io
import json
import shutil
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack.cli import main
from aeroscapes_pack.data import PackError
from aeroscapes_pack.models import Project
from aeroscapes_pack.source_archive import (
    SOURCE_ARCHIVE,
    check_source_archive,
    write_source_archive,
)

MAP_PROFILE = (
    "pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/"
    "minecraft/punchy/compat/aeroscapes_map.json"
)
HOOK_HANDLE = (
    "pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/"
    "yo_hooks/models/item/grappling_hook_handle.json"
)
ROD_TEXTURE = (
    "pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets/"
    "tide/textures/item/equipment/stone_fishing_rod.png"
)


@pytest.fixture
def source_project(tmp_path: Path) -> Project:
    files = {
        "mods/aeroscapes-reimagine/README.md": b"Standalone build instructions",
        "mods/aeroscapes-reimagine/LICENSE": b"GPL-3.0-or-later",
        "mods/aeroscapes-reimagine/build.gradle.kts": b"plugins { java }",
        "mods/aeroscapes-reimagine/settings.gradle.kts": b"",
        "mods/aeroscapes-reimagine/gradle.properties": b"",
        "mods/aeroscapes-reimagine/gradlew": b"#!/bin/sh\n",
        "mods/aeroscapes-reimagine/gradle/wrapper/gradle-wrapper.jar": b"wrapper",
        "mods/aeroscapes-reimagine/gradle/wrapper/gradle-wrapper.properties": b"pinned",
        "mods/aeroscapes-reimagine/gradle/verification-metadata.xml": b"<verification/>",
        "mods/aeroscapes-reimagine/config/pmd.xml": b"<ruleset/>",
        "mods/aeroscapes-reimagine/src/main/java/Owned.java": b"class Owned {}",
        "mods/aeroscapes-reimagine/src/main/resources/META-INF/NOTICE.txt": b"Origins",
        "mods/aeroscapes-reimagine/src/main/resources/data/owned.json": b"{}",
        "mods/aeroscapes-reimagine/src/policy/java/Policy.java": b"class Policy {}",
        "mods/aeroscapes-reimagine/src/regression/resources/fixture.json": b"{}",
        "mods/aeroscapes-reimagine/build/secret.txt": b"generated",
        "mods/aeroscapes-reimagine/.gradle/secret.txt": b"cache",
        "pack/mods/owned.jar": b"binary",
        "pack/config/simplebackups-common.toml": b'ignoredFiles = ["cache"]\n',
        "pack/licenses/THIRD-PARTY-NOTICES.txt": b"Owned resource notices",
        MAP_PROFILE: b'{"itemSpecific":{}}',
        HOOK_HANDLE: b'{"elements":[]}',
        ROD_TEXTURE: b"owned pixels",
        "pack/resourcepacks/unrelated-provider.zip": b"not an owned build input",
        "metadata/worldgen_adoption.json": json.dumps({"jar": "mods/owned.jar"}).encode(),
        "metadata/runtime_data_patches.json": b'{"patches":[]}',
        "metadata/native_template_upgrades.json": b'{"templates":[]}',
    }
    for name, content in files.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    return Project(tmp_path)


def test_source_archive_is_deterministic_and_contains_editable_build_inputs(
    source_project: Project,
) -> None:
    path = write_source_archive(source_project)
    first = path.read_bytes()
    source = source_project.root / "mods/aeroscapes-reimagine/src/main/java/Owned.java"
    source.touch()
    source.chmod(0o600)
    assert write_source_archive(source_project).read_bytes() == first
    with zipfile.ZipFile(io.BytesIO(first)) as archive:
        assert archive.read("mods/aeroscapes-reimagine/src/main/java/Owned.java") == (
            b"class Owned {}"
        )
        assert (
            archive.read("mods/aeroscapes-reimagine/src/regression/resources/fixture.json") == b"{}"
        )
        assert (
            archive.read("mods/aeroscapes-reimagine/src/policy/java/Policy.java")
            == b"class Policy {}"
        )
        assert (
            archive.read("metadata/worldgen_adoption.json")
            == (source_project.root / "metadata/worldgen_adoption.json").read_bytes()
        )
        assert archive.read("metadata/runtime_data_patches.json") == b'{"patches":[]}'
        assert archive.read("metadata/native_template_upgrades.json") == b'{"templates":[]}'
        assert not any(
            "secret" in name or name.endswith(("owned.jar", "unrelated-provider.zip"))
            for name in archive.namelist()
        )
        assert archive.getinfo("mods/aeroscapes-reimagine/gradlew").external_attr >> 16 == 0o100755
        assert archive.getinfo("mods/aeroscapes-reimagine/gradlew").create_system == 3
        assert archive.getinfo("mods/aeroscapes-reimagine/gradlew").compress_type == (
            zipfile.ZIP_DEFLATED
        )
        manifest = json.loads(archive.read("SOURCE-MANIFEST.json"))
        assert manifest["binary"] == {
            "path": "pack/mods/owned.jar",
            "sha256": hashlib.sha256(b"binary").hexdigest(),
        }
    check_source_archive(source_project, {SOURCE_ARCHIVE: first, "mods/owned.jar": b"binary"})


def test_extracted_sources_preserve_pack_inputs_at_the_build_relative_paths(
    source_project: Project, tmp_path: Path
) -> None:
    destination = tmp_path / "recipient"
    with zipfile.ZipFile(write_source_archive(source_project)) as archive:
        archive.extractall(destination)
    mod_project = destination / "mods/aeroscapes-reimagine"
    assert (mod_project / "../../pack/config/simplebackups-common.toml").read_bytes() == (
        b'ignoredFiles = ["cache"]\n'
    )
    atmosphere = mod_project / "../../pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets"
    assert (atmosphere / "minecraft/punchy/compat/aeroscapes_map.json").read_bytes() == (
        b'{"itemSpecific":{}}'
    )
    assert (atmosphere / "yo_hooks/models/item/grappling_hook_handle.json").read_bytes() == (
        b'{"elements":[]}'
    )
    refined = mod_project / "../../pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets"
    assert (refined / "tide/textures/item/equipment/stone_fishing_rod.png").read_bytes() == (
        b"owned pixels"
    )
    manifest = json.loads((destination / "SOURCE-MANIFEST.json").read_bytes())
    for name, checksum in manifest["files"].items():
        assert hashlib.sha256((destination / name).read_bytes()).hexdigest() == checksum


@pytest.mark.parametrize(
    "changed",
    [
        "mods/aeroscapes-reimagine/src/main/java/Owned.java",
        "pack/config/simplebackups-common.toml",
        "pack/licenses/THIRD-PARTY-NOTICES.txt",
        MAP_PROFILE,
        ROD_TEXTURE,
        "binary",
        "archive",
        "missing",
    ],
)
def test_source_archive_rejects_stale_or_missing_recipient_sources(
    source_project: Project, changed: str
) -> None:
    content = write_source_archive(source_project).read_bytes()
    overrides = {SOURCE_ARCHIVE: content, "mods/owned.jar": b"binary"}
    if changed == "binary":
        (source_project.pack / "mods/owned.jar").write_bytes(b"new binary")
        overrides["mods/owned.jar"] = b"new binary"
    elif changed == "archive":
        overrides[SOURCE_ARCHIVE] = b"truncated"
    elif changed == "missing":
        del overrides[SOURCE_ARCHIVE]
    else:
        (source_project.root / changed).write_bytes(b"modified build input")
    with pytest.raises(PackError, match="corresponding source"):
        check_source_archive(source_project, overrides)


def test_incomplete_or_symlinked_build_inputs_are_not_published(source_project: Project) -> None:
    wrapper = source_project.root / "mods/aeroscapes-reimagine/gradlew"
    wrapper.unlink()
    with pytest.raises(PackError, match="Missing corresponding source input"):
        write_source_archive(source_project)
    wrapper.symlink_to(source_project.pack / "mods/owned.jar")
    with pytest.raises(PackError, match="Symlinked corresponding source input"):
        write_source_archive(source_project)


@pytest.mark.parametrize(
    "name",
    [
        "pack/config/simplebackups-common.toml",
        "pack/licenses/THIRD-PARTY-NOTICES.txt",
        "pack/resourcepacks/aeroscapes-reimagine-atmosphere",
        "pack/resourcepacks/aeroscapes-reimagine-refined-compat",
        "pack/resourcepacks",
    ],
)
def test_missing_or_symlinked_pack_build_inputs_are_not_published(
    source_project: Project, name: str
) -> None:
    path = source_project.root / name
    if path.is_dir():
        shutil.rmtree(path)
    else:
        path.unlink()
    with pytest.raises(PackError, match="Missing corresponding source input"):
        write_source_archive(source_project)
    path.symlink_to(source_project.pack / "mods/owned.jar")
    with pytest.raises(PackError, match="Symlinked corresponding source input"):
        write_source_archive(source_project)


def test_source_command_can_regenerate_before_refreshing_the_pack_index(
    source_project: Project,
) -> None:
    assert main(["--root", str(source_project.root), "source-archive"]) == 0
    with zipfile.ZipFile(source_project.pack / SOURCE_ARCHIVE) as archive:
        assert archive.testzip() is None


@pytest.mark.parametrize(
    "inventory", ["runtime_data_patches.json", "native_template_upgrades.json"]
)
def test_cited_provenance_inventories_are_required(source_project: Project, inventory: str) -> None:
    (source_project.root / "metadata" / inventory).unlink()
    with pytest.raises(PackError, match="Missing corresponding source input"):
        write_source_archive(source_project)
