"""Release content identity ignores incidental ZIP metadata."""

import json
import zipfile
from pathlib import Path

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.release import archive_fingerprint


def archive(path: Path, entries: list[tuple[str, bytes]], year: int = 2020) -> None:
    with zipfile.ZipFile(path, "w") as output:
        for name, contents in entries:
            info = zipfile.ZipInfo(name, (year, 1, 1, 0, 0, 0))
            output.writestr(info, contents)


def test_archive_identity_ignores_zip_metadata_and_manifest_format(tmp_path: Path) -> None:
    one = tmp_path / "one.mrpack"
    two = tmp_path / "two.mrpack"
    manifest = {"name": "Pack", "files": [{"path": "a"}, {"path": "b"}]}
    archive(one, [("modrinth.index.json", json.dumps(manifest).encode()), ("overrides/x", b"x")])
    manifest["files"].reverse()
    archive(
        two,
        [("overrides/x", b"x"), ("modrinth.index.json", json.dumps(manifest, indent=2).encode())],
        2024,
    )
    assert archive_fingerprint(one) == archive_fingerprint(two)
    archive(two, [("overrides/x", b"y"), ("modrinth.index.json", json.dumps(manifest).encode())])
    assert archive_fingerprint(one) != archive_fingerprint(two)


def test_archive_rejects_duplicate_paths(tmp_path: Path) -> None:
    path = tmp_path / "duplicate.mrpack"
    with pytest.warns(UserWarning, match="Duplicate name"):
        archive(path, [("modrinth.index.json", b"{}"), ("x", b"1"), ("x", b"2")])
    with pytest.raises(PackError, match="duplicate"):
        archive_fingerprint(path)


def test_version_order_and_invalid_version() -> None:
    from aeroscapes_pack.release import version_key

    assert version_key("0.1.0-test.9") < version_key("0.1.0-test.10")
    assert version_key("0.1.0-test.10") < version_key("0.1.0") < version_key("0.1.1-alpha")
    for value in ("01.0.0", "1.0.0-test.01", "1.0", "1.0.0+build"):
        with pytest.raises(PackError, match="version"):
            version_key(value)


def test_changelog_extracts_version_for_both_stable_and_prerelease() -> None:
    from aeroscapes_pack.release import changelog_section

    source = "---\ntitle: Test\n---\n\n## 0.2.0\n\n- New thing\n\n## 0.1.0\n\n- Old thing\n"
    assert changelog_section(source, "0.2.0-test.2") == "- New thing"
    assert changelog_section(source, "0.2.0") == "- New thing"
    with pytest.raises(PackError, match="changelog"):
        changelog_section(source, "0.3.0")


def test_release_decision_rejects_reused_version_changes_and_downgrades() -> None:
    from aeroscapes_pack.release import release_decision

    assert release_decision("0.2.0", (), None) == "create"
    assert release_decision("0.2.0", ("0.1.0",), None) == "create"
    assert release_decision("0.2.0", ("0.2.0",), True) == "skip"
    with pytest.raises(PackError, match="increase"):
        release_decision("0.2.0", ("0.2.0",), False)
    with pytest.raises(PackError, match="higher"):
        release_decision("0.1.0", ("0.2.0",), None)
    with pytest.raises(PackError, match="higher"):
        release_decision("0.2.0-test.1", ("0.2.0",), None)


def test_release_recovers_draft_without_replacing_verified_asset(tmp_path: Path) -> None:
    from aeroscapes_pack.github_release import Release, publish_release

    class Store:
        def __init__(self) -> None:
            self.release: Release | None = None
            self.tag: str | None = None
            self.assets: dict[str, bytes] = {}
            self.uploads: list[str] = []

        def releases(self) -> tuple[Release, ...]:
            return (self.release,) if self.release else ()

        def tag_commit(self, _tag: str) -> str | None:
            return self.tag

        def create_tag(self, _tag: str, commit: str) -> None:
            self.tag = commit

        def create_draft(self, tag: str, _body: str, prerelease: bool, commit: str) -> Release:
            self.release = Release(1, tag, True, prerelease, commit)
            return self.release

        def asset(self, _release: Release, name: str) -> bytes | None:
            return self.assets.get(name)

        def upload(self, _release: Release, name: str, content: bytes) -> None:
            assert name not in self.assets
            self.uploads.append(name)
            self.assets[name] = content

        def publish(self, release: Release) -> None:
            self.release = Release(
                release.id, release.tag, False, release.prerelease, release.target, True
            )

    store = Store()
    artifact = tmp_path / "pack.mrpack"
    archive(artifact, [("modrinth.index.json", b'{"files":[]}'), ("overrides/a", b"content")])
    store.tag = "a" * 40
    store.release = Release(1, "v0.1.0-test.1", True, True, "a" * 40)
    with pytest.raises(PackError, match="original"):
        publish_release(store, "0.1.0-test.1", "b" * 40, artifact, "notes")
    store.assets[artifact.name] = artifact.read_bytes()
    assert publish_release(store, "0.1.0-test.1", "a" * 40, artifact, "notes") == "published"
    assert store.uploads == ["pack.mrpack.sha256"]
    assert store.release is not None
    assert not store.release.draft
    assert store.release.prerelease
    archive(artifact, [("modrinth.index.json", b'{"files":[]}'), ("overrides/a", b"content")], 2024)
    assert publish_release(store, "0.1.0-test.1", "b" * 40, artifact, "notes") == "skipped"
    archive(artifact, [("modrinth.index.json", b'{"files":[]}'), ("overrides/a", b"changed")])
    with pytest.raises(PackError, match="increase"):
        publish_release(store, "0.1.0-test.1", "b" * 40, artifact, "notes")
    assert store.uploads == ["pack.mrpack.sha256"]

    not_immutable = Store()
    not_immutable.tag = "c" * 40
    not_immutable.release = Release(1, "v0.1.0", False, False, "c" * 40, False)
    with pytest.raises(PackError, match="not immutable"):
        publish_release(not_immutable, "0.1.0", "c" * 40, artifact, "notes")

    fresh = Store()
    archive(artifact, [("modrinth.index.json", b'{"files":[]}')])
    assert publish_release(fresh, "0.1.0", "c" * 40, artifact, "notes") == "published"
    assert fresh.tag == "c" * 40
    assert fresh.uploads == ["pack.mrpack", "pack.mrpack.sha256"]
    assert fresh.release is not None
    assert not fresh.release.prerelease


def test_docs_status_lists_only_published_releases() -> None:
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.release_docs import release_listing

    releases = (
        Release(1, "v0.1.0-test.1", False, True, "", True),
        Release(2, "v0.1.0", False, False, "", True),
        Release(3, "v0.2.0", True, False),
    )
    english = release_listing(releases, "owner/repo", "en")
    russian = release_listing(releases, "owner/repo", "ru")
    assert "0.1.0-test.1" in english
    assert "prerelease" in english
    assert "0.1.0" in russian
    assert "стабильная" in russian
    assert "0.2.0" not in english


@pytest.mark.parametrize("has_release", [False, True])
def test_documentation_uses_published_assets_and_ignores_stale_commit(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, has_release: bool
) -> None:
    import shutil

    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release, checksum
    from aeroscapes_pack.models import Project
    from aeroscapes_pack.release_docs import prepare

    root = Path(__file__).resolve().parents[3]
    shutil.copytree(root / "docs", tmp_path / "docs")
    github = GitHub("owner/repo", "test-token")
    release = Release(1, "v0.1.0", False, False, "", True)
    content = b"archive"
    filename = "aeroscapes-reimagine-0.1.0.mrpack"
    monkeypatch.setattr(
        github,
        "json",
        lambda _method, path, **_kwargs: (
            {"private": False} if path == "" else {"object": {"sha": "abc"}}
        ),
    )
    monkeypatch.setattr(github, "releases", lambda: (release,) if has_release else ())
    monkeypatch.setattr(
        github,
        "asset",
        lambda _item, name: content if name == filename else checksum(filename, content),
    )
    project = Project(tmp_path)
    assert not prepare(project, github, "older")
    assert prepare(project, github, "abc")
    for language in ("en", "ru"):
        installation = (tmp_path / "docs" / language / "install.md").read_text()
        changes = (tmp_path / "docs" / language / "changelog.md").read_text()
        original = root / "docs" / language
        assert changes == (original / "changelog.md").read_text()
        assert (tmp_path / "docs" / language / "known-issues.md").read_text() == (
            original / "known-issues.md"
        ).read_text()
        if has_release:
            assert "https://github.com/owner/repo/releases/tag/v0.1.0" in installation
            assert "status: available" in installation
        else:
            assert "https://github.com/owner/repo/releases/tag/" not in installation
            assert "status: partial" in installation


def test_release_notes_use_only_target_section_from_checked_snapshot(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack import release_cli
    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.models import Project

    for locale in ("en", "ru"):
        directory = tmp_path / "docs" / locale
        directory.mkdir(parents=True)
        (directory / "changelog.md").write_text(
            f"## 0.2.0\n\n- Current {locale}\n\n## 0.1.1\n\n- Previous {locale}\n"
        )
    github = GitHub("owner/repo", "test-token")
    monkeypatch.setattr(
        github,
        "releases",
        lambda: (
            Release(
                id=1,
                tag="v0.1.0",
                draft=False,
                prerelease=False,
                target="a" * 40,
                immutable=True,
            ),
        ),
    )
    monkeypatch.setattr(github, "tag_commit", lambda _tag: "a" * 40)
    monkeypatch.setattr(github, "generated_notes", lambda *_args: "PR references")
    monkeypatch.setattr(
        release_cli,
        "git",
        lambda _root, *args: "b" * 40 + "\tfeat: [0.2.0] atmosphere" if args[0] == "log" else "",
    )
    body = release_cli.release_body(Project(tmp_path), github, "0.2.0", "b" * 40, tmp_path)
    assert "- Current en" in body
    assert "- Current ru" in body
    assert "Previous" not in body
    assert "aeroscapes-ci-commit:" + "b" * 40 in body
    assert "https://github.com/owner/repo/commit/" + "b" * 40 in body


def test_docs_rejects_prerelease_marked_stable() -> None:
    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.release_docs import checked_releases

    github = GitHub("owner/repo", "test")
    with pytest.MonkeyPatch.context() as patch:
        patch.setattr(
            github, "releases", lambda: (Release(1, "v0.1.0-test.1", False, False, "", True),)
        )
        with pytest.raises(PackError, match="type"):
            checked_releases(github)


@pytest.mark.parametrize(
    ("artifact_content", "checksum_content"),
    [(None, None), (b"archive", None), (b"archive", b"incorrect checksum")],
    ids=["missing-archive", "missing-checksum", "invalid-checksum"],
)
def test_docs_rejects_invalid_published_assets(
    artifact_content: bytes | None,
    checksum_content: bytes | None,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.release_docs import checked_releases

    github = GitHub("owner/repo", "test")
    release = Release(id=1, tag="v0.1.0-test.1", draft=False, prerelease=True, immutable=True)
    name = "aeroscapes-reimagine-0.1.0-test.1.mrpack"
    monkeypatch.setattr(github, "releases", lambda: (release,))
    monkeypatch.setattr(
        github,
        "asset",
        lambda _release, filename: artifact_content if filename == name else checksum_content,
    )
    with pytest.raises(PackError, match="v0\\.1\\.0-test\\.1"):
        checked_releases(github)


@pytest.mark.parametrize(
    ("docs_commit", "github_commit", "expected"),
    [("abc", "older", "true"), ("older", "abc", "false"), (None, "abc", "true")],
    ids=["current", "stale", "fallback-github-sha"],
)
def test_docs_deploy_guard_checks_commit_without_editing_checkout(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    docs_commit: str | None,
    github_commit: str,
    expected: str,
) -> None:
    from aeroscapes_pack import release_docs
    from aeroscapes_pack.github_api import GitHub

    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("CI", "true")
    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("GITHUB_TOKEN", "test")
    monkeypatch.setenv("GITHUB_SHA", github_commit)
    if docs_commit is None:
        monkeypatch.delenv("DOCS_COMMIT", raising=False)
    else:
        monkeypatch.setenv("DOCS_COMMIT", docs_commit)
    output = tmp_path / "output"
    monkeypatch.setenv("GITHUB_OUTPUT", str(output))
    monkeypatch.setattr(GitHub, "json", lambda *_args, **_kwargs: {"object": {"sha": "abc"}})

    assert release_docs.main(["--check-current"]) == 0
    assert output.read_text() == f"deploy={expected}\n"


def test_private_repository_downloads_are_not_announced_as_public(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import shutil

    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release, checksum
    from aeroscapes_pack.models import Project
    from aeroscapes_pack.release_docs import prepare

    root = Path(__file__).resolve().parents[3]
    shutil.copytree(root / "docs", tmp_path / "docs")
    github = GitHub("owner/repo", "test")
    release = Release(1, "v0.1.0", False, False, "", True)
    name = "aeroscapes-reimagine-0.1.0.mrpack"
    content = b"archive"
    monkeypatch.setattr(
        github,
        "json",
        lambda _method, path, **_kwargs: (
            {"private": True} if path == "" else {"object": {"sha": "abc"}}
        ),
    )
    monkeypatch.setattr(github, "releases", lambda: (release,))
    monkeypatch.setattr(
        github,
        "asset",
        lambda _item, filename: content if filename == name else checksum(name, content),
    )
    assert prepare(Project(tmp_path), github, "abc")
    english = (tmp_path / "docs/en/install.md").read_text()
    russian = (tmp_path / "docs/ru/install.md").read_text()
    assert "repository access" in english
    assert "доступ к репозиторию" in russian
    assert "status: partial" in english
    assert "status: partial" in russian
