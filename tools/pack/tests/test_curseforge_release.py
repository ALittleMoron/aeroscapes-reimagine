"""CurseForge readiness reuses the immutable verified GitHub source artifact."""

from pathlib import Path

import pytest

from aeroscapes_pack import curseforge_release
from aeroscapes_pack.curseforge_release import UPLOAD_BLOCKER, preflight_release, run
from aeroscapes_pack.data import PackError
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release, checksum
from aeroscapes_pack.models import Manifest, Project
from aeroscapes_pack.pack import SourcePack


def test_mutable_or_draft_github_release_is_rejected(tmp_path: Path) -> None:
    github = GitHub("owner/repo", "fixture")
    for release in (
        Release(1, "v0.1.0-test.1", False, True, "", False),
        Release(1, "v0.1.0-test.1", True, True, "", True),
    ):
        with pytest.raises(PackError, match="published immutable"):
            preflight_release(Project(tmp_path), github, release, tmp_path, 123)


def test_github_artifact_checksum_is_required_before_export(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = SourcePack(
        manifest=Manifest(
            name="Pack",
            version="0.1.0-test.1",
            index_file="index.toml",
            index_algorithm="sha256",
            index_hash="a" * 64,
            minecraft="1.21.1",
            neoforge="21.1.230",
        ),
        dependencies={},
        overrides={},
    )
    monkeypatch.setattr(curseforge_release, "check_project", lambda _project: source)
    github = GitHub("owner/repo", "fixture")
    monkeypatch.setattr(github, "asset", lambda *_arguments: b"wrong checksum")
    release = Release(1, "v0.1.0-test.1", False, True, "", True)
    with pytest.raises(PackError, match="checksum"):
        preflight_release(Project(tmp_path), github, release, tmp_path, 123)


def test_preflight_verifies_exact_mrpack_before_building_channel_archive(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = SourcePack(
        manifest=Manifest(
            name="Pack",
            version="0.1.0-test.1",
            index_file="index.toml",
            index_algorithm="sha256",
            index_hash="a" * 64,
            minecraft="1.21.1",
            neoforge="21.1.230",
        ),
        dependencies={},
        overrides={},
    )
    monkeypatch.setattr(curseforge_release, "check_project", lambda _project: source)
    monkeypatch.setattr(curseforge_release, "load_artifacts", lambda _project: ())
    github = GitHub("owner/repo", "fixture")
    content = b"immutable original"
    name = "aeroscapes-reimagine-0.1.0-test.1.mrpack"
    monkeypatch.setattr(
        github, "asset", lambda _release, path: content if path == name else checksum(name, content)
    )
    operations: list[str] = []

    def verify(path: Path, actual_source: SourcePack, _artifacts: object) -> None:
        assert actual_source is source
        assert path.read_bytes() == content
        operations.append("verify")

    def build(_project: Project, output: Path, project_id: int) -> Path:
        assert operations == ["verify"]
        assert project_id == 123
        operations.append("build")
        return output

    monkeypatch.setattr(curseforge_release, "verify_mrpack", verify)
    monkeypatch.setattr(curseforge_release, "build_client_archive", build)
    preflight_release(
        Project(tmp_path), github, Release(1, "v0.1.0-test.1", False, True, "", True), tmp_path, 123
    )
    assert operations == ["verify", "build"]


def test_history_is_checked_in_version_order_and_never_claims_upload(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    github = GitHub("owner/repo", "fixture")
    releases = (
        Release(2, "v0.1.0-test.10", False, True, "", True),
        Release(1, "v0.1.0-test.9", False, True, "", True),
    )
    monkeypatch.setattr(github, "releases", lambda: releases)
    monkeypatch.setattr(github, "tag_commit", lambda _tag: "a" * 40)
    monkeypatch.setattr(
        curseforge_release, "export_original", lambda *_arguments: Project(tmp_path)
    )
    checked: list[str] = []

    def preflight(
        _project: Project, _github: GitHub, release: Release, directory: Path, _project_id: int
    ) -> Path:
        checked.append(release.tag)
        return directory / "pack.zip"

    monkeypatch.setattr(curseforge_release, "preflight_release", preflight)
    with pytest.raises(PackError, match="No upload was attempted") as failure:
        run(Project(tmp_path), github, 123)
    assert str(failure.value) == UPLOAD_BLOCKER
    assert checked == ["v0.1.0-test.9", "v0.1.0-test.10"]
