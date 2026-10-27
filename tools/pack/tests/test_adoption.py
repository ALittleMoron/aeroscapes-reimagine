"""Source-reference and shipped-byte contracts for the owned world-generation resources."""

import hashlib
import io
import json
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack.adoption import check_adoption
from aeroscapes_pack.data import PackError
from aeroscapes_pack.models import Project
from aeroscapes_pack.pack import check_compat

RESOURCE = "data/aeroscapes/worldgen/density_function/final_density.json"
CONTENT = b'{"type":"minecraft:constant","argument":1}'


def jar(resources: dict[str, bytes]) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(
            "META-INF/neoforge.mods.toml",
            '[[mods]]\nmodId="aeroscapes_reimagine"\nversion="0.1.0"\n',
        )
        for name, content in resources.items():
            archive.writestr(name, content)
    return output.getvalue()


@pytest.fixture
def adoption(tmp_path: Path) -> tuple[Project, dict[str, bytes], dict[str, object]]:
    (tmp_path / "metadata").mkdir()
    (tmp_path / "metadata/artifacts.json").write_text(
        json.dumps(
            [
                {
                    "project_id": "source",
                    "version_id": "exact",
                    "sha512": "a" * 128,
                    "status": "excluded",
                }
            ]
        )
    )
    checksum = hashlib.sha256(CONTENT).hexdigest()
    record: dict[str, object] = {
        "jar": "mods/owned.jar",
        "mod_id": "aeroscapes_reimagine",
        "mod_version": "0.1.0",
        "source": {"project_id": "source", "version_id": "exact", "sha512": "a" * 128},
        "resources": [
            {
                "source": RESOURCE,
                "source_sha256": checksum,
                "output": RESOURCE,
                "sha256": checksum,
                "mode": "exact",
                "reason": "Retain seeded density",
            }
        ],
        "java": [],
    }
    return Project(tmp_path), {"mods/owned.jar": jar({RESOURCE: CONTENT})}, record


def run_adoption(
    fixture: tuple[Project, dict[str, bytes], dict[str, object]],
) -> tuple[dict[str, str], dict[str, str]]:
    project, overrides, record = fixture
    (project.root / "metadata/worldgen_adoption.json").write_text(json.dumps(record))
    return check_adoption(project, overrides)


def test_excluded_source_cannot_satisfy_installed_target_but_owned_version_can(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    owned, sources = run_adoption(adoption)
    assert owned == {"owned:aeroscapes_reimagine": "0.1.0"}
    assert sources == {"source": "exact"}
    directory = adoption[0].root / "compat"
    directory.mkdir()
    (directory / "pack.mcmeta").write_text("{}")
    fix = {"targets": owned, "source_targets": sources, "files": ["pack.mcmeta"], "reason": "port"}
    check_compat(directory, [fix], (), owned=owned, sources=sources)
    with pytest.raises(PackError, match="Re-evaluate compatibility"):
        check_compat(
            directory, [fix], (), owned={"owned:aeroscapes_reimagine": "0.2.0"}, sources=sources
        )
    with pytest.raises(PackError, match="Re-evaluate compatibility"):
        check_compat(directory, [{**fix, "targets": sources}], (), owned=owned, sources=sources)


@pytest.mark.parametrize("mutation", ["changed", "extra", "missing", "malformed"])
def test_adopted_shipped_bytes_require_exact_reviewed_coverage(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
    mutation: str,
) -> None:
    project, overrides, record = adoption
    resources = {RESOURCE: CONTENT}
    if mutation == "changed":
        resources[RESOURCE] = b'{"type":"minecraft:constant","argument":2}'
    elif mutation == "extra":
        resources["data/aeroscapes/worldgen/noise/extra.json"] = b"{}"
    elif mutation == "missing":
        resources.clear()
    else:
        resources[RESOURCE] = b"{"
    overrides["mods/owned.jar"] = jar(resources)
    with pytest.raises(PackError):
        run_adoption((project, overrides, record))


def test_source_archive_identity_requires_reaudit(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    project, _, _ = adoption
    (project.root / "metadata/artifacts.json").write_text(
        json.dumps(
            [
                {
                    "project_id": "source",
                    "version_id": "exact",
                    "sha512": "b" * 128,
                    "status": "excluded",
                }
            ]
        )
    )
    with pytest.raises(PackError, match="source identity"):
        run_adoption(adoption)


def test_exact_mode_cannot_silently_adapt_source(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    record = adoption[2]
    record["resources"] = [
        {
            "source": RESOURCE,
            "source_sha256": "b" * 64,
            "output": RESOURCE,
            "sha256": hashlib.sha256(CONTENT).hexdigest(),
            "mode": "exact",
            "reason": "density",
        }
    ]
    with pytest.raises(PackError, match="Exact adoption"):
        run_adoption(adoption)


@pytest.mark.parametrize("output", ["../outside.json", RESOURCE])
def test_unsafe_and_duplicate_adoption_outputs_fail(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
    output: str,
) -> None:
    checksum = hashlib.sha256(CONTENT).hexdigest()
    adoption[2]["resources"] = [
        {
            "source": RESOURCE,
            "source_sha256": checksum,
            "output": name,
            "sha256": checksum,
            "mode": "exact",
            "reason": "density",
        }
        for name in [RESOURCE, output]
    ]
    with pytest.raises(PackError):
        run_adoption(adoption)


@pytest.mark.parametrize("resource", [RESOURCE, "data/minecraft/worldgen/noise/example.json"])
def test_adopted_data_requires_inventory(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
    resource: str,
) -> None:
    project, overrides, _ = adoption
    overrides["mods/owned.jar"] = jar({resource: CONTENT})
    with pytest.raises(PackError, match="adoption inventory"):
        check_adoption(project, overrides)


@pytest.mark.parametrize("binding", ["targets", "source_targets"])
@pytest.mark.parametrize("version", [None, "", 42, False])
def test_compatibility_versions_must_be_nonempty_strings(
    tmp_path: Path, binding: str, version: object
) -> None:
    (tmp_path / "pack.mcmeta").write_text("{}")
    fix: dict[str, object] = {
        "targets": {"owned:provider": "1"},
        "files": ["pack.mcmeta"],
        "reason": "exact binding",
    }
    fix[binding] = {"unknown": version}
    with pytest.raises(PackError):
        check_compat(tmp_path, [fix], (), owned={"owned:provider": "1"})


def test_conflicting_duplicate_jar_resources_are_rejected(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(RESOURCE, b'{"unreviewed":true}')
        archive.writestr(
            "META-INF/neoforge.mods.toml",
            '[[mods]]\nmodId="aeroscapes_reimagine"\nversion="0.1.0"\n',
        )
        with pytest.warns(UserWarning, match="Duplicate name"):
            archive.writestr(RESOURCE, CONTENT)
    adoption[1]["mods/owned.jar"] = output.getvalue()
    with pytest.raises(PackError, match="Duplicate.*JAR"):
        run_adoption(adoption)


def test_missing_owned_descriptor_is_a_contextual_pack_error(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr(RESOURCE, CONTENT)
    adoption[1]["mods/owned.jar"] = output.getvalue()
    with pytest.raises(PackError, match="descriptor.*mods/owned.jar"):
        run_adoption(adoption)


@pytest.mark.parametrize("encoding", ["unsupported_compression", "encrypted"])
def test_unreadable_owned_members_have_jar_and_entry_context(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
    encoding: str,
) -> None:
    # Patch only the descriptor's standard local/central header fields. Its data,
    # CRC and the resource entry remain intact, isolating native member decoding.
    archive_bytes = bytearray(adoption[1]["mods/owned.jar"])
    central_offset = archive_bytes.index(b"PK\x01\x02")
    if encoding == "unsupported_compression":
        archive_bytes[8:10] = (99).to_bytes(2, "little")
        archive_bytes[central_offset + 10 : central_offset + 12] = (99).to_bytes(2, "little")
    else:
        archive_bytes[6:8] = (1).to_bytes(2, "little")
        archive_bytes[central_offset + 8 : central_offset + 10] = (1).to_bytes(2, "little")
    adoption[1]["mods/owned.jar"] = bytes(archive_bytes)
    with pytest.raises(PackError, match="mods/owned.jar.*META-INF/neoforge.mods.toml"):
        run_adoption(adoption)


def test_java_reference_mode_preserves_the_compared_output_hash_contract(
    adoption: tuple[Project, dict[str, bytes], dict[str, object]],
) -> None:
    project, _, record = adoption
    output = project.root / "Example.java"
    output.write_bytes(b"reimplemented Java")
    record["java"] = [
        {
            "source": "original/Example.class",
            "source_sha256": "a" * 64,
            "output": "Example.java",
            "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "mode": "reference",
            "reason": "Behavior correspondence without asserting copying",
        }
    ]
    run_adoption(adoption)
    output.write_bytes(b"changed Java")
    with pytest.raises(PackError, match="Changed adopted Java"):
        run_adoption(adoption)
