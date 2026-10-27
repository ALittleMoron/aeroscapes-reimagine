"""A client export cannot omit dependencies or bundle unaudited platform files."""

import json
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack import curseforge_pack
from aeroscapes_pack.curseforge_pack import (
    BlockedFile,
    build_client_archive,
    client_manifest,
    load_channel,
    parse_file,
    verify_curseforge,
)
from aeroscapes_pack.data import PackError
from aeroscapes_pack.models import Manifest, PinnedFile, Project
from aeroscapes_pack.pack import SourcePack


def dependency(project_id: str, filename: str, side: str = "both") -> PinnedFile:
    return PinnedFile.parse(
        {
            "name": project_id,
            "filename": filename,
            "side": side,
            "download": {
                "url": "https://cdn.modrinth.com/file",
                "hash-format": "sha512",
                "hash": "a" * 128,
            },
            "update": {"modrinth": {"mod-id": project_id, "version": "version"}},
        },
        "fixture",
    )


def pin(item: PinnedFile, number: int) -> dict[str, object]:
    return {
        "slug": item.name,
        "primary_project_id": item.project_id,
        "primary_sha512": item.sha512,
        "status": "hosted",
        "project_id": number,
        "file_id": number + 100,
        "filename": item.filename,
        "sha512": "a" * 128,
        "sha1": "b" * 40,
        "file_size": 100,
    }


@pytest.fixture
def fixture_project(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> tuple[Project, SourcePack]:
    project = Project(tmp_path)
    project.pack.mkdir()
    (project.pack / "pack.toml").write_text('author = "Pack author"\n')
    shared = dependency("shared", "shared.jar")
    client = dependency("client", "client.jar", "client")
    server = dependency("server", "server.jar", "server")
    source = SourcePack(
        manifest=Manifest(
            name="Fixture pack",
            version="0.1.0-test.25",
            index_file="index.toml",
            index_algorithm="sha256",
            index_hash="a" * 64,
            minecraft="1.21.1",
            neoforge="21.1.230",
        ),
        dependencies={
            "mods/shared.jar": shared,
            "mods/client.jar": client,
            "mods/server.jar": server,
        },
        overrides={"config/game.toml": b"enabled = true\n", "licenses/source.zip": b"source"},
    )
    metadata = {
        "schema_version": 1,
        "minecraft": "1.21.1",
        "neoforge": "21.1.230",
        "loader_id": "neoforge-21.1.230",
        "artifacts": [
            pin(item, number) for number, item in enumerate(source.dependencies.values(), 1)
        ],
    }
    (project.root / "metadata").mkdir()
    (project.root / "metadata/curseforge.json").write_text(json.dumps(metadata))
    monkeypatch.setattr(curseforge_pack, "check_project", lambda _project: source)
    return project, source


def test_client_export_is_complete_deterministic_and_preserves_overrides(
    fixture_project: tuple[Project, SourcePack],
) -> None:
    project, source = fixture_project
    one = build_client_archive(project, project.root / "one.zip", 123)
    two = build_client_archive(project, project.root / "two.zip", 123)
    assert one.read_bytes() == two.read_bytes()
    channel = load_channel(project, source)
    verify_curseforge(one, source, channel, 123)
    manifest = client_manifest(source, channel, 123)
    assert manifest["minecraft"] == {
        "version": "1.21.1",
        "modLoaders": [{"id": "neoforge-21.1.230", "primary": True}],
    }
    assert manifest["files"] == [
        {"projectID": 2, "fileID": 102, "required": True},
        {"projectID": 1, "fileID": 101, "required": True},
    ]
    assert manifest["projectID"] == 123
    assert manifest["author"] == "Pack author"
    with zipfile.ZipFile(one) as archive:
        assert archive.read("overrides/config/game.toml") == source.overrides["config/game.toml"]
        assert set(archive.namelist()) == {
            "manifest.json",
            "overrides/config/game.toml",
            "overrides/licenses/source.zip",
        }
    with pytest.raises(PackError, match="already exists"):
        build_client_archive(project, one)


def test_blocked_dependency_prevents_any_output(
    fixture_project: tuple[Project, SourcePack],
) -> None:
    project, source = fixture_project
    path = project.root / "metadata/curseforge.json"
    metadata = json.loads(path.read_text())
    metadata["artifacts"][0] = {
        "slug": "shared",
        "primary_project_id": "shared",
        "primary_sha512": "a" * 128,
        "status": "blocked",
        "reason": "Approval is missing",
    }
    path.write_text(json.dumps(metadata))
    channel = load_channel(project, source)
    assert isinstance(channel.files[0], BlockedFile)
    output = project.root / "exports/pack.zip"
    with pytest.raises(PackError, match="shared: Approval is missing"):
        build_client_archive(project, output)
    assert not output.parent.exists()


@pytest.mark.parametrize("mutation", ["missing", "stale", "duplicate"])
def test_channel_cannot_silently_omit_or_substitute_selected_files(
    fixture_project: tuple[Project, SourcePack],
    mutation: str,
) -> None:
    project, source = fixture_project
    path = project.root / "metadata/curseforge.json"
    metadata = json.loads(path.read_text())
    if mutation == "missing":
        metadata["artifacts"].pop()
    elif mutation == "stale":
        metadata["artifacts"][0]["primary_sha512"] = "c" * 128
    else:
        metadata["artifacts"].append(metadata["artifacts"][0])
    path.write_text(json.dumps(metadata))
    with pytest.raises(PackError, match="cover every|stale"):
        load_channel(project, source)


def test_verifier_rejects_extra_or_replaced_export_content(
    fixture_project: tuple[Project, SourcePack],
) -> None:
    project, source = fixture_project
    path = build_client_archive(project, project.root / "one.zip")
    with zipfile.ZipFile(path, "a") as archive:
        archive.writestr("overrides/mods/unaudited.jar", b"unaudited")
    with pytest.raises(PackError, match="extra, missing or duplicate"):
        verify_curseforge(path, source, load_channel(project, source))


@pytest.mark.parametrize("filename", ["../escape.jar", "x\\y.jar", "bad\r\nname.jar"])
def test_channel_filename_is_portable(filename: str) -> None:
    record = pin(dependency("project", filename), 1)
    with pytest.raises(PackError, match="portable filename"):
        parse_file(record)
