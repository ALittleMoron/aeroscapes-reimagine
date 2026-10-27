"""Exercise the release transport and recovery without publishing to GitHub."""

import io
import json
import urllib.error
from http.client import HTTPMessage
from pathlib import Path
from typing import Self
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request
from zipfile import ZipFile

import pytest

from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import publish_release


class Response:
    def __init__(self, content: bytes) -> None:
        self.content = content

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *_arguments: object) -> None:
        pass

    def read(self) -> bytes:
        return self.content


class FakeGitHub:
    def __init__(self) -> None:
        self.tag: str | None = None
        self.release: dict[str, object] | None = None
        self.assets: dict[int, tuple[str, bytes]] = {}
        self.posts = 0
        self.starters: set[int] = set()
        self.fail_after_upload = False
        self.immutable_enabled = True

    def open(self, request: Request, timeout: int) -> Response:
        assert timeout == 60
        url = urlsplit(request.full_url)
        prefix = "/repos/owner/repo"
        assert url.path.startswith(prefix)
        path = url.path[len(prefix) :]
        method = request.get_method()
        data = request.data
        assert data is None or isinstance(data, bytes)
        result: object
        if method == "GET" and path == "/immutable-releases":
            result = {"enabled": self.immutable_enabled}
        elif method == "GET" and path == "/releases":
            result = [self.release] if self.release is not None else []
        elif method == "GET" and path.startswith("/git/ref/tags/"):
            if self.tag is None:
                raise urllib.error.HTTPError(request.full_url, 404, "missing", HTTPMessage(), None)
            result = {"object": {"type": "commit", "sha": self.tag}}
        elif method == "POST" and path == "/git/refs":
            payload = json.loads(data or b"{}")
            self.tag = payload["sha"]
            result = {"ref": payload["ref"]}
        elif method == "POST" and path == "/releases":
            payload = json.loads(data or b"{}")
            self.release = {
                "id": 1,
                "tag_name": payload["tag_name"],
                "draft": True,
                "prerelease": payload["prerelease"],
                "target_commitish": payload["target_commitish"],
                "immutable": False,
            }
            result = self.release
        elif method == "GET" and path == "/releases/1":
            result = {
                "assets": [
                    {
                        "id": key,
                        "name": value[0],
                        "state": "starter" if key in self.starters else "uploaded",
                        "size": len(value[1]),
                    }
                    for key, value in self.assets.items()
                ]
            }
        elif method == "POST" and path == "/releases/1/assets":
            name = parse_qs(url.query)["name"][0]
            self.posts += 1
            self.assets[self.posts] = (name, data or b"")
            if self.fail_after_upload:
                self.fail_after_upload = False
                raise urllib.error.URLError("connection lost after upload")
            result = {"name": name}
        elif method == "DELETE" and path.startswith("/releases/assets/"):
            key = int(path.rsplit("/", 1)[1])
            del self.assets[key]
            self.starters.discard(key)
            return Response(b"")
        elif method == "GET" and path.startswith("/releases/assets/"):
            return Response(self.assets[int(path.rsplit("/", 1)[1])][1])
        elif method == "PATCH" and path == "/releases/1":
            assert self.release is not None
            payload = json.loads(data or b"{}")
            assert payload["make_latest"] == ("false" if self.release["prerelease"] else "true")
            self.release["draft"] = False
            self.release["immutable"] = True
            result = self.release
        else:
            raise AssertionError(f"Unexpected request: {method} {path}")
        return Response(json.dumps(result).encode())


def test_api_publishes_once_with_exact_assets_and_prerelease(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    github = GitHub("owner/repo", "secret")
    remote = FakeGitHub()
    monkeypatch.setattr(github, "opener", remote)
    artifact = tmp_path / "pack.mrpack"
    with ZipFile(artifact, "w") as archive:
        archive.writestr("modrinth.index.json", '{"files":[]}')
        archive.writestr("overrides/item", b"pack data")
    assert publish_release(github, "0.1.0-test.1", "a" * 40, artifact, "changes") == "published"
    assert remote.tag == "a" * 40
    assert remote.assets[1] == ("pack.mrpack", artifact.read_bytes())
    assert remote.posts == 2
    assert publish_release(github, "0.1.0-test.1", "b" * 40, artifact, "changes") == "skipped"
    assert remote.posts == 2


def test_redirect_removes_token_for_other_host() -> None:
    from aeroscapes_pack.github_api import SafeRedirect

    request = Request("https://api.github.com/file", headers={"Authorization": "Bearer secret"})
    redirected = SafeRedirect().redirect_request(
        request,
        io.BytesIO(),
        302,
        "Found",
        HTTPMessage(),
        "https://objects.example.com/file",
    )
    assert redirected is not None
    assert redirected.get_header("Authorization") is None


def test_json_accepts_empty_success_for_checkpoint_updates(monkeypatch: pytest.MonkeyPatch) -> None:
    github = GitHub("owner/repo", "secret")
    monkeypatch.setattr(github, "request", lambda *_args, **_kwargs: b"")
    assert (
        github.json("PATCH", "/actions/variables/RELEASE_QUEUE_STATE", {"value": "state"}) is None
    )


def test_empty_failed_upload_is_removed_from_draft_and_retried(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    github = GitHub("owner/repo", "secret")
    remote = FakeGitHub()
    remote.release = {
        "id": 1,
        "tag_name": "v0.1.0-test.1",
        "draft": True,
        "prerelease": True,
        "target_commitish": "a" * 40,
        "immutable": False,
    }
    remote.assets[1] = ("pack.mrpack", b"")
    remote.starters.add(1)
    remote.posts = 1
    monkeypatch.setattr(github, "opener", remote)
    artifact = tmp_path / "pack.mrpack"
    with ZipFile(artifact, "w") as archive:
        archive.writestr("modrinth.index.json", '{"files":[]}')
    assert publish_release(github, "0.1.0-test.1", "a" * 40, artifact, "changes") == "published"
    assert 1 not in remote.assets
    assert remote.assets[2] == ("pack.mrpack", artifact.read_bytes())


def test_lost_upload_response_resumes_without_duplicate(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack.data import PackError

    github = GitHub("owner/repo", "secret")
    remote = FakeGitHub()
    remote.fail_after_upload = True
    monkeypatch.setattr(github, "opener", remote)
    artifact = tmp_path / "pack.mrpack"
    with ZipFile(artifact, "w") as archive:
        archive.writestr("modrinth.index.json", '{"files":[]}')
    with pytest.raises(PackError, match="connection lost"):
        publish_release(github, "0.1.0", "a" * 40, artifact, "changes")
    assert remote.tag is None
    assert remote.posts == 1
    assert publish_release(github, "0.1.0", "a" * 40, artifact, "changes") == "published"
    assert remote.posts == 2
    assert remote.assets[1] == ("pack.mrpack", artifact.read_bytes())


def test_publication_requires_immutable_release_setting(monkeypatch: pytest.MonkeyPatch) -> None:
    from aeroscapes_pack.data import PackError

    github = GitHub("owner/repo", "secret")
    remote = FakeGitHub()
    remote.immutable_enabled = False
    monkeypatch.setattr(github, "opener", remote)
    with pytest.raises(PackError, match="immutable"):
        github.ensure_immutable_enabled()
    assert not remote.assets
    assert remote.release is None
