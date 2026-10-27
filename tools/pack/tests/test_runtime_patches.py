"""Guard the redistribution boundary and exact provider identity of local repairs."""

import io
import json
import zipfile
from dataclasses import dataclass, replace
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.models import Artifact, Manifest, Project
from aeroscapes_pack.runtime_patches import check_runtime_patches


@dataclass
class Patches:
    project: Project
    artifact: Artifact
    platform: Manifest
    recipe: dict[str, object]
    overrides: dict[str, bytes]

    def write(self, recipes: list[dict[str, object]]) -> None:
        content = json.dumps(recipes).encode()
        (self.project.root / "recipes.json").write_bytes(content)
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as archive:
            archive.writestr("aeroscapes_reimagine/data-patches.json", content)
        self.overrides["mods/aeroscapes-reimagine-0.1.0.jar"] = output.getvalue()

    def check(self) -> None:
        check_runtime_patches(self.project, self.platform, (self.artifact,), self.overrides)


@pytest.fixture
def patches(tmp_path: Path) -> Patches:
    project = Project(tmp_path)
    (tmp_path / "metadata").mkdir()
    artifact = Artifact(
        project_id="provider",
        version_id="pinned",
        filename="provider.jar",
        sha512="a" * 128,
        sha1="b" * 40,
        size=1,
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
    recipe: dict[str, object] = {
        "id": "provider:worldgen/structure/camp.json",
        "mod": "provider",
        "source": "data/provider/worldgen/structure/camp.json",
        "sha256": "c" * 64,
        "edits": [],
    }
    inventory = {
        "minecraft": "1.21.1",
        "neoforge": "21.1.230",
        "patch_resource": "recipes.json",
        "providers": [
            {
                "project_id": "provider",
                "version_id": "pinned",
                "filename": "provider.jar",
                "sha512": "a" * 128,
            }
        ],
        "resources": [
            {
                "id": recipe["id"],
                "provider": "provider",
                "version": "pinned",
                "source": recipe["source"],
                "source_sha256": "c" * 64,
                "result_json_sha256": "d" * 64,
            }
        ],
    }
    (tmp_path / "metadata/runtime_data_patches.json").write_text(json.dumps(inventory))
    fixture = Patches(project, artifact, platform, recipe, {})
    fixture.write([recipe])
    return fixture


def test_recipe_with_original_provider_pin_and_matching_jar_is_accepted(patches: Patches) -> None:
    patches.check()


def test_duplicate_recipe_is_rejected_before_minecraft_load(patches: Patches) -> None:
    patches.write([patches.recipe, patches.recipe])
    with pytest.raises(PackError, match="Duplicate runtime patch recipe"):
        patches.check()


def test_distributed_result_is_rejected_even_when_recipe_and_jar_match(patches: Patches) -> None:
    patches.overrides["global_packs/compat/data/provider/worldgen/structure/camp.json"] = b"{}"
    with pytest.raises(PackError, match="must not be distributed"):
        patches.check()


@pytest.mark.parametrize("field", ["version_id", "sha512"])
def test_changed_provider_requires_review(patches: Patches, field: str) -> None:
    patches.artifact = replace(patches.artifact, **{field: "changed"})
    with pytest.raises(PackError, match="changed runtime patch provider"):
        patches.check()


def test_unbuilt_recipe_change_is_rejected(patches: Patches) -> None:
    (patches.project.root / "recipes.json").write_text(
        json.dumps([{**patches.recipe, "edits": [{"op": "remove", "path": ["obsolete"]}]}])
    )
    with pytest.raises(PackError, match="differ from the shipped mod"):
        patches.check()
