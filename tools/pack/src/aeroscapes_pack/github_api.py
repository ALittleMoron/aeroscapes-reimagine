"""Bounded GitHub REST operations for release publication."""

import json
import re
import urllib.error
import urllib.parse
import urllib.request
from http.client import HTTPMessage
from typing import IO, override

from aeroscapes_pack.data import PackError, array, field, integer, json_value, table
from aeroscapes_pack.github_release import Release


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    @override
    def redirect_request(
        self,
        req: urllib.request.Request,
        fp: IO[bytes],
        code: int,
        msg: str,
        headers: HTTPMessage,
        newurl: str,
    ) -> urllib.request.Request | None:
        url = urllib.parse.urlsplit(newurl)
        if (
            url.scheme != "https"
            or not bool(url.hostname)
            or url.username is not None
            or url.password is not None
        ):
            raise PackError("GitHub returned an unsafe redirect")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected and url.hostname != urllib.parse.urlsplit(req.full_url).hostname:
            redirected.remove_header("Authorization")
        return redirected


class GitHub:
    def __init__(self, repository: str, token: str) -> None:
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not token:
            raise PackError("Expected GITHUB_REPOSITORY and GITHUB_TOKEN")
        self.repository = repository
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(
        self,
        method: str,
        path: str,
        data: bytes | None = None,
        *,
        accept: str = "application/vnd.github+json",
        content_type: str = "application/json",
        upload: bool = False,
        allow_missing: bool = False,
    ) -> bytes | None:
        host = "uploads.github.com" if upload else "api.github.com"
        url = f"https://{host}/repos/{self.repository}{path}"
        request = urllib.request.Request(
            url,
            data=data,
            method=method,
            headers={
                "Accept": accept,
                "Authorization": f"Bearer {self.token}",
                "Content-Type": content_type,
                "X-GitHub-Api-Version": "2026-03-10",
                "User-Agent": "aeroscapes-release",
            },
        )
        try:
            with self.opener.open(request, timeout=60) as response:
                result: object = response.read()
                if not isinstance(result, bytes):
                    raise PackError("Invalid GitHub response")
                return result
        except urllib.error.HTTPError as error:
            if error.code == 404 and allow_missing:
                return None
            hints = {
                401: "invalid or expired token",
                403: "insufficient permissions or rate limit",
                404: "resource missing or token lacks access",
                422: "invalid request, conflicting tag or release, or rate limit",
            }
            hint = hints.get(error.code, "GitHub API error")
            raise PackError(
                f"GitHub {method} {path} failed with HTTP {error.code}: {hint}"
            ) from error
        except urllib.error.URLError as error:
            raise PackError(f"GitHub {method} {path} failed: {error.reason}") from error

    def json(
        self,
        method: str,
        path: str,
        data: dict[str, object] | None = None,
        *,
        allow_missing: bool = False,
    ) -> object:
        payload = json.dumps(data).encode() if data is not None else None
        result = self.request(method, path, payload, allow_missing=allow_missing)
        return None if result is None or result == b"" else json_value(result, f"GitHub {path}")

    def ensure_immutable_enabled(self) -> None:
        result = self.json("GET", "/immutable-releases", allow_missing=True)
        if result is None or table(result, "immutable releases").get("enabled") is not True:
            raise PackError(
                "Enable immutable releases and grant RELEASE_TOKEN Administration read access"
            )

    def releases(self) -> tuple[Release, ...]:
        result: list[Release] = []
        for page in range(1, 101):
            values = array(self.json("GET", f"/releases?per_page=100&page={page}"), "releases")
            for value in values:
                record = table(value, "release")
                draft, prerelease = record.get("draft"), record.get("prerelease")
                immutable = record.get("immutable")
                if (
                    not isinstance(draft, bool)
                    or not isinstance(prerelease, bool)
                    or not isinstance(immutable, bool)
                ):
                    raise PackError("Invalid release state")
                result.append(
                    Release(
                        id=integer(record.get("id"), "release.id"),
                        tag=field(record, "tag_name", "release"),
                        draft=draft,
                        prerelease=prerelease,
                        target=field(record, "target_commitish", "release"),
                        immutable=immutable,
                    )
                )
            if len(values) < 100:
                return tuple(result)
        raise PackError("Too many GitHub releases to inspect safely")

    def tag_commit(self, tag: str) -> str | None:
        quoted = urllib.parse.quote(tag, safe="")
        value = self.json("GET", f"/git/ref/tags/{quoted}", allow_missing=True)
        if value is None:
            return None
        record = table(table(value, "reference").get("object"), "reference.object")
        if field(record, "type", "reference.object") != "commit":
            raise PackError(f"Tag {tag} is not a lightweight commit tag")
        return field(record, "sha", "reference.object")

    def create_tag(self, tag: str, commit: str) -> None:
        self.json("POST", "/git/refs", {"ref": f"refs/tags/{tag}", "sha": commit})

    def create_draft(self, tag: str, body: str, prerelease: bool, commit: str) -> Release:
        value = table(
            self.json(
                "POST",
                "/releases",
                {
                    "tag_name": tag,
                    "target_commitish": commit,
                    "name": tag[1:],
                    "body": body,
                    "draft": True,
                    "prerelease": prerelease,
                    "make_latest": "false",
                },
            ),
            "release",
        )
        return Release(
            id=integer(value.get("id"), "release.id"),
            tag=tag,
            draft=True,
            prerelease=prerelease,
            target=commit,
        )

    def asset(self, release: Release, name: str) -> bytes | None:
        record = table(self.json("GET", f"/releases/{release.id}"), "release")
        matches = [table(item, "asset") for item in array(record.get("assets"), "assets")]
        matches = [item for item in matches if item.get("name") == name]
        if len(matches) > 1:
            raise PackError(f"Duplicate release asset {name}")
        if not matches:
            return None
        asset_id = integer(matches[0].get("id"), "asset.id")
        state = field(matches[0], "state", "asset")
        if (
            state == "starter"
            and release.draft
            and integer(matches[0].get("size"), "asset.size") == 0
        ):
            self.request("DELETE", f"/releases/assets/{asset_id}")
            return None
        if state != "uploaded":
            raise PackError(f"Release asset {name} has unexpected state {state}")
        return self.request(
            "GET", f"/releases/assets/{asset_id}", accept="application/octet-stream"
        )

    def upload(self, release: Release, name: str, content: bytes) -> None:
        query = urllib.parse.urlencode({"name": name})
        self.request(
            "POST",
            f"/releases/{release.id}/assets?{query}",
            content,
            content_type="application/octet-stream",
            upload=True,
        )
        if self.asset(release, name) != content:
            raise PackError(f"Uploaded asset {name} does not match verified bytes")

    def publish(self, release: Release) -> None:
        self.json(
            "PATCH",
            f"/releases/{release.id}",
            {
                "draft": False,
                "prerelease": release.prerelease,
                "make_latest": "false" if release.prerelease else "true",
            },
        )

    def generated_notes(self, tag: str, commit: str, previous: str | None) -> str:
        payload: dict[str, object] = {"tag_name": tag, "target_commitish": commit}
        if previous is not None:
            payload["previous_tag_name"] = previous
        record = table(self.json("POST", "/releases/generate-notes", payload), "release notes")
        return field(record, "body", "release notes")
