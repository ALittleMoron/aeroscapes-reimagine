"""Network-boundary regressions without contacting an artifact host."""

import io
from dataclasses import dataclass
from http.client import HTTPMessage
from pathlib import Path
from urllib.request import Request

import pytest

from aeroscapes_pack.data import PackError
from aeroscapes_pack.files import HttpsRedirectHandler, digest, download


@dataclass(frozen=True, slots=True)
class MemoryOpener:
    content: bytes

    def open(self, _request: Request, *, timeout: int) -> io.BytesIO:
        assert timeout > 0
        return io.BytesIO(self.content)


def install_response(monkeypatch: pytest.MonkeyPatch, content: bytes) -> None:
    def opener(_handler: object) -> MemoryOpener:
        return MemoryOpener(content)

    monkeypatch.setattr("urllib.request.build_opener", opener)


@pytest.mark.parametrize("url", ["http://example.invalid/mod.jar", "file:///tmp/mod.jar"])
def test_download_rejects_non_https_even_for_cached_files(tmp_path: Path, url: str) -> None:
    target = tmp_path / "mod.jar"
    target.write_bytes(b"existing")
    with pytest.raises(PackError, match="HTTPS"):
        download(url, target, digest(target))
    assert target.read_bytes() == b"existing"


def test_https_redirect_cannot_downgrade_to_http() -> None:
    handler = HttpsRedirectHandler()
    with pytest.raises(PackError, match="HTTPS"):
        handler.redirect_request(
            Request("https://example.invalid/mod.jar"),
            io.BytesIO(),
            302,
            "Found",
            HTTPMessage(),
            "http://example.invalid/mod.jar",
        )


def test_verified_download_is_published_and_temporary_files_are_removed(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    content = b"published artifact"
    reference = tmp_path / "reference"
    reference.write_bytes(content)
    cache = tmp_path / "cache"
    target = cache / "mod.jar"
    install_response(monkeypatch, content)
    assert download("https://example.invalid/mod.jar", target, digest(reference)) == target
    assert target.read_bytes() == content
    assert list(cache.iterdir()) == [target]


def test_bad_download_is_not_cached(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    install_response(monkeypatch, b"wrong bytes")
    target = tmp_path / "cache/mod.jar"
    with pytest.raises(PackError, match="Hash mismatch"):
        download("https://example.invalid/mod.jar", target, "0" * 64)
    assert list(target.parent.iterdir()) == []
