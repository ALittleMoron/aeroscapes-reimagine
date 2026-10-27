"""Source-only integrity contracts for the pinned native template upgrades."""

import gzip
import hashlib
import json
from dataclasses import dataclass, replace
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError, Table
from aeroscapes_pack.models import Artifact, Manifest, Project
from aeroscapes_pack.native_templates import check_native_template_upgrades


@dataclass
class UpgradeFixture:
    project: Project
    platform: Manifest
    artifact: Artifact
    manifest: Table
    template: Table
    output: Path

    def write_manifest(self) -> None:
        (self.project.root / "metadata/native_template_upgrades.json").write_text(
            json.dumps(self.manifest), encoding="utf-8"
        )


@pytest.fixture
def upgrade(tmp_path: Path) -> UpgradeFixture:
    project = Project(tmp_path)
    (project.root / "metadata").mkdir()
    identifier = "nova_structures:lone_citadel/frame/example"
    relative = "data/nova_structures/structure/lone_citadel/frame/example.nbt"
    output = project.pack / "global_packs/required_data/aeroscapes_reimagine_compat" / relative
    output.parent.mkdir(parents=True)
    encoded = gzip.compress(b"\x0a\x00\x00\x03\x00\x0bDataVersion\x00\x00\x0fs\x00", mtime=0)
    artifact = Artifact(
        project_id="tpehi7ww",
        version_id="BYUUUeZA",
        filename="dungeons-and-taverns-v4.4.4.jar",
        sha512=hashlib.sha512(b"upstream artifact").hexdigest(),
        sha1="0" * 40,
        size=17,
        side="both",
        kind="mod",
        release_type="release",
        loaders=("neoforge",),
        game_versions=("1.21.1",),
        requirements=(),
    )
    platform = Manifest(
        name="Fixture",
        version="0.1",
        index_file="index.toml",
        index_algorithm="sha256",
        index_hash="0" * 64,
        minecraft="1.21.1",
        neoforge="21.1.230",
    )
    template: Table = {
        "id": identifier,
        "path": relative,
        "original_sha256": hashlib.sha256(b"original template").hexdigest(),
        "output_sha256": hashlib.sha256(encoded).hexdigest(),
        "output_sha512": hashlib.sha512(encoded).hexdigest(),
        "output_data_version": 3955,
        "native_dfu_typed_nbt_sha256": hashlib.sha256(b"native DFU reference").hexdigest(),
        "loaded_template_nbt_sha256": hashlib.sha256(b"loaded reference").hexdigest(),
    }
    manifest: Table = {
        "strategy": "minecraft-runtime-dfu",
        "minecraft": "1.21.1",
        "neoforge": "21.1.230",
        "target_data_version": 3955,
        "upstream_project_id": artifact.project_id,
        "upstream_version_id": artifact.version_id,
        "upstream_sha512": artifact.sha512,
        "count": 1,
        "template_ids_sha256": hashlib.sha256((identifier + "\n").encode()).hexdigest(),
        "templates": [template],
    }
    fixture = UpgradeFixture(
        project=project,
        platform=platform,
        artifact=artifact,
        manifest=manifest,
        template=template,
        output=output,
    )
    fixture.write_manifest()
    return fixture


def test_verified_template_is_accepted_without_the_upstream_jar(upgrade: UpgradeFixture) -> None:
    check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


@pytest.mark.parametrize("field", ["minecraft", "neoforge"])
def test_platform_changes_require_new_native_conversion(
    upgrade: UpgradeFixture, field: str
) -> None:
    platform = replace(upgrade.platform, **{field: "new-version"})

    with pytest.raises(PackError, match="platform"):
        check_native_template_upgrades(upgrade.project, platform, (upgrade.artifact,))


@pytest.mark.parametrize("field", ["minecraft", "neoforge"])
def test_metadata_cannot_rebind_outputs_to_an_unverified_platform(
    upgrade: UpgradeFixture, field: str
) -> None:
    upgrade.manifest[field] = "new-version"
    upgrade.write_manifest()
    platform = replace(upgrade.platform, **{field: "new-version"})

    with pytest.raises(PackError, match="platform"):
        check_native_template_upgrades(upgrade.project, platform, (upgrade.artifact,))


@pytest.mark.parametrize("field", ["version_id", "sha512"])
def test_changed_upstream_pin_rejects_old_conversions(upgrade: UpgradeFixture, field: str) -> None:
    artifact = replace(upgrade.artifact, **{field: "f" * 128})

    with pytest.raises(PackError, match="upstream"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (artifact,))


def test_missing_upstream_artifact_rejects_conversions(upgrade: UpgradeFixture) -> None:
    with pytest.raises(PackError, match="upstream"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, ())


@pytest.mark.parametrize(
    "pack_name", ["aeroscapes_reimagine_compat", "aeroscapes_reimagine_worldgen"]
)
def test_runtime_dfu_rejects_redistributed_native_templates(
    upgrade: UpgradeFixture, pack_name: str
) -> None:
    output = (
        upgrade.project.pack
        / "global_packs/required_data"
        / pack_name
        / "data/nova_structures/structure/unrecorded.nbt"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(b"redistributed source")

    with pytest.raises(PackError, match="forbids redistributed"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


@pytest.mark.parametrize("strategy", ["preconverted-copies", "unknown"])
def test_unverified_template_strategy_is_rejected(upgrade: UpgradeFixture, strategy: str) -> None:
    upgrade.manifest["strategy"] = strategy
    upgrade.write_manifest()

    with pytest.raises(PackError, match="strategy"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


def test_native_outputs_require_a_provenance_manifest(upgrade: UpgradeFixture) -> None:
    (upgrade.project.root / "metadata/native_template_upgrades.json").unlink()
    upgrade.output.write_bytes(b"unrecorded output")

    with pytest.raises(PackError, match="native_template_upgrades.json"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


def test_duplicate_template_records_are_rejected(upgrade: UpgradeFixture) -> None:
    upgrade.manifest["templates"] = [upgrade.template, upgrade.template]
    upgrade.manifest["count"] = 2
    upgrade.write_manifest()

    with pytest.raises(PackError, match="Duplicate native template"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


@pytest.mark.parametrize(
    "path",
    ["../escape.nbt", "/tmp/escape.nbt", "data\\escape.nbt", "data/other/structure/example.nbt"],
)
def test_unsafe_or_misdirected_template_paths_are_rejected(
    upgrade: UpgradeFixture, path: str
) -> None:
    upgrade.template["path"] = path
    upgrade.write_manifest()

    with pytest.raises(PackError, match="[Pp]ath"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


@pytest.mark.parametrize("field", ["target_data_version", "output_data_version"])
@pytest.mark.parametrize("value", [3954, True])
def test_wrong_or_boolean_data_versions_are_rejected(
    upgrade: UpgradeFixture, field: str, value: object
) -> None:
    target = upgrade.template if field == "output_data_version" else upgrade.manifest
    target[field] = value
    upgrade.write_manifest()

    with pytest.raises(PackError, match="data.version|integer"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


@pytest.mark.parametrize(
    "field",
    [
        "original_sha256",
        "output_sha256",
        "output_sha512",
        "native_dfu_typed_nbt_sha256",
        "loaded_template_nbt_sha256",
    ],
)
def test_template_hashes_require_exact_hex_digests(upgrade: UpgradeFixture, field: str) -> None:
    upgrade.template[field] = "not-a-digest"
    upgrade.write_manifest()

    with pytest.raises(PackError, match="SHA"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))


def test_manifest_id_digest_detects_a_changed_record_set(upgrade: UpgradeFixture) -> None:
    upgrade.manifest["template_ids_sha256"] = "f" * 64
    upgrade.write_manifest()

    with pytest.raises(PackError, match="template ID"):
        check_native_template_upgrades(upgrade.project, upgrade.platform, (upgrade.artifact,))
