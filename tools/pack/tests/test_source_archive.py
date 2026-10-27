"""Recipients receive the matching, editable owned-mod project with the binary."""

import hashlib
import io
import json
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
            "secret" in name or name.endswith("owned.jar") for name in archive.namelist()
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


@pytest.mark.parametrize("changed", ["source", "binary", "archive", "missing"])
def test_source_archive_rejects_stale_or_missing_recipient_sources(
    source_project: Project, changed: str
) -> None:
    content = write_source_archive(source_project).read_bytes()
    overrides = {SOURCE_ARCHIVE: content, "mods/owned.jar": b"binary"}
    if changed == "source":
        (source_project.root / "mods/aeroscapes-reimagine/src/main/java/Owned.java").write_bytes(
            b"class Modified {}"
        )
    elif changed == "binary":
        (source_project.pack / "mods/owned.jar").write_bytes(b"new binary")
        overrides["mods/owned.jar"] = b"new binary"
    elif changed == "archive":
        overrides[SOURCE_ARCHIVE] = b"truncated"
    else:
        del overrides[SOURCE_ARCHIVE]
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
