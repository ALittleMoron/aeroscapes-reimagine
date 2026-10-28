"""Only correctly identified integrations can supply a publication artifact."""

import json
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release, checksum


def write_archive(path: Path, content: bytes = b"pack content") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("modrinth.index.json", '{"files":[]}')
        archive.writestr("overrides/options.txt", content)


@pytest.mark.parametrize(
    "title",
    ["feat: [0.1.1] atmosphere", "Release v0.1.1", "0.1.1: animations and MC 1.21.1"],
)
def test_release_title_accepts_exact_pack_version(title: str) -> None:
    from aeroscapes_pack.release import require_release_title

    require_release_title(version="0.1.1", title=title)


@pytest.mark.parametrize(
    "title",
    ["More animations", "Release 0.1.10", "Release 10.1.1", "0.1.1-beta", "x0.1.1"],
)
def test_release_title_rejects_missing_or_partial_version(title: str) -> None:
    from aeroscapes_pack.release import require_release_title

    with pytest.raises(PackError, match="title"):
        require_release_title(version="0.1.1", title=title)


@pytest.mark.parametrize("version", ["0.1.1-beta", "0.1.1-test.1"])
def test_new_pack_releases_use_plain_versions(version: str) -> None:
    from aeroscapes_pack.release import require_release_title

    with pytest.raises(PackError, match="plain"):
        require_release_title(version=version, title=f"Release {version}")


def test_integration_uses_only_final_push_subject_or_pr_title() -> None:
    from aeroscapes_pack.release_check import integration_title

    event: dict[str, object] = {
        "repository": {"full_name": "owner/repo"},
        "ref": "refs/heads/main",
        "head_commit": {"message": "Final title\n\n0.1.1 in body is insufficient"},
    }
    assert integration_title("push", event, "owner/repo") == "Final title"
    event["ref"] = "refs/heads/topic"
    assert integration_title("push", event, "owner/repo") is None
    event["pull_request"] = {"base": {"ref": "main"}, "title": "[0.1.1] PR title"}
    assert integration_title("pull_request", event, "owner/repo") == "[0.1.1] PR title"
    event["pull_request"] = {"base": {"ref": "topic"}, "title": "Preparatory PR"}
    assert integration_title("pull_request", event, "owner/repo") is None


@pytest.mark.parametrize(
    ("event_name", "payload"),
    [
        ("workflow_dispatch", {}),
        ("push", {"ref": "refs/heads/main", "head_commit": None}),
        ("push", {"ref": "refs/heads/main", "head_commit": {"message": 42}}),
        ("push", {"head_commit": {"message": "[0.1.1] title"}}),
        ("push", {"ref": "refs/tags/v0.1.1"}),
        ("pull_request", {"pull_request": {"title": "[0.1.1] title"}}),
    ],
)
def test_invalid_event_cannot_bypass_integration_check(
    event_name: str, payload: dict[str, object]
) -> None:
    from aeroscapes_pack.release_check import integration_title

    event = {"repository": {"full_name": "owner/repo"}, **payload}
    with pytest.raises(PackError):
        integration_title(event_name, event, "owner/repo")
    with pytest.raises(PackError, match="repository"):
        integration_title("push", event, "other/repo")


def test_published_identical_pack_exempts_docs_but_changed_pack_requires_version(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack.release_check import check_candidate

    artifact = tmp_path / "aeroscapes-reimagine-0.1.1.mrpack"
    write_archive(artifact)
    original = artifact.read_bytes()
    github = GitHub("owner/repo", "read-only-test-token")
    release = Release(id=1, tag="v0.1.1", draft=False, prerelease=False, immutable=True)
    monkeypatch.setattr(github, "releases", lambda: (release,))
    monkeypatch.setattr(
        github,
        "asset",
        lambda _release, name: (
            original if name == artifact.name else checksum(artifact.name, original)
        ),
    )
    assert not check_candidate(github, "0.1.1", artifact, "docs: clarify installation")
    write_archive(artifact, b"changed modpack")
    with pytest.raises(PackError, match="increase"):
        check_candidate(github, "0.1.1", artifact, "fix: [0.1.1] changed pack")


def test_new_version_still_requires_title_after_predecessor_failed(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack.release_check import check_candidate

    github = GitHub("owner/repo", "read-only-test-token")
    monkeypatch.setattr(
        github,
        "releases",
        lambda: (Release(id=1, tag="v0.1.0", draft=False, prerelease=False, immutable=True),),
    )
    artifact = tmp_path / "aeroscapes-reimagine-0.1.1.mrpack"
    write_archive(artifact)
    with pytest.raises(PackError, match="title"):
        check_candidate(github, "0.1.1", artifact, "docs: correction after failed CI")
    assert check_candidate(github, "0.1.1", artifact, "fix: [0.1.1] correct release title")


def test_visible_draft_is_not_a_docs_exemption_or_an_asset_cleanup(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack.release_check import check_candidate

    github = GitHub("owner/repo", "read-only-test-token")
    monkeypatch.setattr(
        github,
        "releases",
        lambda: (Release(id=1, tag="v0.1.1", draft=True, prerelease=False),),
    )

    def asset(_release: Release, _name: str) -> bytes | None:
        pytest.fail("A read-only integration check must never inspect/clean draft assets")

    monkeypatch.setattr(github, "asset", asset)
    with pytest.raises(PackError, match="unfinished"):
        check_candidate(github, "0.1.1", tmp_path / "pack.mrpack", "[0.1.1] title")


@pytest.mark.parametrize("notes", ["- Current changes", "", "duplicate"])
def test_ci_entry_point_checks_both_changelogs_before_success(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, notes: str
) -> None:
    from aeroscapes_pack.release_check import main

    (tmp_path / "pack").mkdir()
    (tmp_path / "pack/pack.toml").write_text('version = "0.1.1"\n')
    (tmp_path / "toolchain.json").write_text("{}")
    for locale in ("en", "ru"):
        directory = tmp_path / "docs" / locale
        directory.mkdir(parents=True)
        content = "## 0.1.1\n\n- Current changes\n"
        if locale == "ru":
            content = "## 0.1.1\n\n" + notes + "\n"
            if notes == "duplicate":
                content += "## 0.1.1\n\n- Another section\n"
        (directory / "changelog.md").write_text(content)
    write_archive(tmp_path / "dist/aeroscapes-reimagine-0.1.1.mrpack")
    event = tmp_path / "event.json"
    event.write_text(
        json.dumps(
            {
                "repository": {"full_name": "owner/repo"},
                "ref": "refs/heads/main",
                "head_commit": {"message": "feat: [0.1.1] atmosphere"},
            }
        )
    )
    monkeypatch.chdir(tmp_path)
    for name, value in {
        "GITHUB_REPOSITORY": "owner/repo",
        "GITHUB_TOKEN": "read-only-test-token",
        "GITHUB_EVENT_NAME": "push",
        "GITHUB_EVENT_PATH": str(event),
    }.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setattr(GitHub, "releases", lambda _self: ())
    assert main() == (0 if notes == "- Current changes" else 1)
