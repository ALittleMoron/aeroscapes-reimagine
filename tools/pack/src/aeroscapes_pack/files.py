"""Path confinement, integrity checks and verified artifact downloads."""

import hashlib
import shutil
import tempfile
import urllib.request
from http.client import HTTPException, HTTPMessage
from pathlib import Path, PurePosixPath
from typing import IO, override
from urllib.parse import urlsplit

from aeroscapes_pack.data import PackError

DOWNLOAD_TIMEOUT_SECONDS = 30


def digest(path: Path, algorithm: str = "sha256") -> str:
    if algorithm not in {"sha256", "sha512"}:
        raise PackError(f"Unsupported integrity algorithm: {algorithm}")
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def safe_path(root: Path, name: str) -> Path:
    """Constrain portable pack paths, including symlinks, to their installation root."""
    path = PurePosixPath(name)
    if (
        not name
        or "\\" in name
        or ":" in name
        or path.is_absolute()
        or ".." in path.parts
        or path.as_posix() != name
        or name == "."
    ):
        raise PackError(f"Unsafe pack path: {name!r}")
    result = root / path
    if not result.resolve().is_relative_to(root.resolve()):
        raise PackError(f"Pack path escapes its root: {name!r}")
    return result


def new_target(target: Path) -> Path:
    target = target.expanduser().resolve()
    if target.exists():
        raise PackError(f"Target already exists; choose a new directory: {target}")
    target.mkdir(parents=True)
    return target


def require_https(url: str) -> None:
    parsed = urlsplit(url)
    if (
        parsed.scheme != "https"
        or not bool(parsed.hostname)
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise PackError(f"Expected an HTTPS artifact URL without credentials: {url}")


class HttpsRedirectHandler(urllib.request.HTTPRedirectHandler):
    @override
    def redirect_request(  # urllib defines this callback's names and positional arguments.
        self,
        req: urllib.request.Request,
        fp: IO[bytes],
        code: int,
        msg: str,
        headers: HTTPMessage,
        newurl: str,
    ) -> urllib.request.Request | None:
        require_https(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def download(url: str, target: Path, expected: str, algorithm: str = "sha256") -> Path:
    """Publish only verified bytes; partial downloads never become cached artifacts."""
    require_https(url)
    if target.exists():
        if digest(target, algorithm) != expected:
            raise PackError(f"Hash mismatch: {target}")
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=target.parent, prefix=".download-") as scratch:
        temporary = Path(scratch) / "artifact"
        try:
            with temporary.open("wb") as output:
                request = urllib.request.Request(
                    url, headers={"User-Agent": "AeroscapesReimagine/0.1"}
                )
                opener = urllib.request.build_opener(HttpsRedirectHandler())
                with opener.open(request, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response:
                    shutil.copyfileobj(response, output)
        except (HTTPException, OSError) as error:
            raise PackError(f"Download failed for {url}: {error}") from error
        if digest(temporary, algorithm) != expected:
            raise PackError(f"Hash mismatch downloading {url}")
        temporary.replace(target)
    return target
