"""Publish the immutable GitHub artifact as an optional Modrinth version."""

import hashlib
import json
import os
import re
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
from http.client import HTTPException, HTTPMessage
from pathlib import Path
from typing import IO, override

from aeroscapes_pack.data import PackError, array, field, json_value, read_toml, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.github_api import GitHub, SafeRedirect
from aeroscapes_pack.github_release import Release, checksum
from aeroscapes_pack.models import Manifest, Project, load_artifacts
from aeroscapes_pack.modrinth_profile import sync_profile
from aeroscapes_pack.pack import check_project, verify_mrpack
from aeroscapes_pack.release import version_key
from aeroscapes_pack.release_cli import export_original


def matching_version(
    records: list[object], version: str, name: str, content: bytes, minecraft: str, notes: str
) -> str | None:
    matches = [table(item, "Modrinth version") for item in records]
    matches = [item for item in matches if item.get("version_number") == version]
    if len(matches) > 1:
        raise PackError(f"Multiple Modrinth versions named {version}; resolve duplicates")
    if not matches:
        return None
    record = matches[0]
    files = [table(item, "Modrinth file") for item in array(record.get("files"), "Modrinth files")]
    matching = [item for item in files if item.get("filename") == name]
    expected = hashlib.sha512(content).hexdigest()
    if (
        len(files) != 1
        or len(matching) != 1
        or table(matching[0].get("hashes"), "file hashes").get("sha512") != expected
    ):
        raise PackError(f"Modrinth version {version} has different files; increase the version")
    expected_type = "beta" if "-" in version else "release"
    if (
        record.get("version_type") != expected_type
        or record.get("game_versions") != [minecraft]
        or record.get("loaders") != ["neoforge"]
        or record.get("changelog") != notes
        or record.get("environment") != "client_and_server"
    ):
        raise PackError(f"Modrinth version {version} has different metadata")
    return field(record, "status", "Modrinth version")


def publication_status(project_status: str, version_status: str) -> str:
    if project_status == "approved" and version_status == "listed":
        return "available"
    if project_status == "processing":
        return "awaiting moderation"
    if project_status in {"rejected", "withheld"}:
        return f"project {project_status}; action required"
    return "uploaded, not listed"


def require_icon_cdn(url: str) -> None:
    try:
        parsed = urllib.parse.urlsplit(url)
        valid = (
            parsed.scheme == "https"
            and parsed.hostname == "cdn.modrinth.com"
            and parsed.port in {None, 443}
            and parsed.username is None
            and parsed.password is None
            and not parsed.fragment
            and not any(character.isspace() for character in url)
        )
    except ValueError as error:
        raise PackError("Invalid Modrinth icon CDN URL") from error
    if not valid:
        raise PackError("Modrinth raw icon must use credential-free HTTPS on cdn.modrinth.com")


class IconRedirect(urllib.request.HTTPRedirectHandler):
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
        require_icon_cdn(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Modrinth:
    def __init__(self, project_id: str, token: str) -> None:
        if not re.fullmatch(r"[A-Za-z0-9_-]+", project_id) or not token:
            raise PackError("MODRINTH_PROJECT_ID and MODRINTH_TOKEN are required")
        self.project_id = project_id
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(
        self,
        path: str,
        *,
        data: bytes | None = None,
        content_type: str = "",
        method: str | None = None,
    ) -> object:
        headers = {"Authorization": self.token, "User-Agent": "AeroscapesReimagine/release"}
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(
            f"https://api.modrinth.com/v2{path}", data=data, headers=headers, method=method
        )
        try:
            with self.opener.open(request, timeout=120) as response:
                content: object = response.read()
                if not isinstance(content, bytes):
                    raise PackError("Invalid Modrinth response")
                return None if content == b"" else json_value(content, f"Modrinth {path}")
        except urllib.error.HTTPError as error:
            raise PackError(f"Modrinth {path} failed with HTTP {error.code}") from error
        except urllib.error.URLError as error:
            raise PackError(f"Modrinth {path} failed: {error.reason}") from error

    def read_icon(self, url: str) -> bytes:
        require_icon_cdn(url)
        opener = urllib.request.build_opener(IconRedirect())
        request = urllib.request.Request(url, headers={"User-Agent": "AeroscapesReimagine/release"})
        try:
            with opener.open(request, timeout=30) as response:
                require_icon_cdn(response.geturl())
                # Existing icons can use the server's current 512KiB limit;
                # new profile icons keep the stricter documented 256KiB limit.
                content: object = response.read(512 * 1024 + 1)
                if not isinstance(content, bytes) or len(content) > 512 * 1024:
                    raise PackError("Modrinth raw icon response exceeds 512KiB or is invalid")
                return content
        except (HTTPException, OSError) as error:
            raise PackError(f"Modrinth raw icon download failed: {error}") from error

    def project(self) -> dict[str, object]:
        project = table(self.request(f"/project/{self.project_id}"), "Modrinth project")
        if project.get("project_type") != "modpack":
            raise PackError("Modrinth project must be a modpack")
        return project

    def versions(self) -> list[object]:
        return array(self.request(f"/project/{self.project_id}/version"), "Modrinth versions")

    def version(self, version_id: str) -> dict[str, object]:
        if not re.fullmatch(r"[A-Za-z0-9]+", version_id):
            raise PackError("Invalid Modrinth version ID")
        record = table(self.request(f"/version/{version_id}"), "Modrinth version")
        if record.get("id") != version_id:
            raise PackError("Modrinth returned a different version ID")
        return record

    def upload(self, metadata: dict[str, object], name: str, content: bytes) -> str:
        boundary = uuid.uuid4().hex
        data = json.dumps(metadata, ensure_ascii=False).encode()
        body = (
            f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            "Content-Type: application/json\r\n\r\n"
        ).encode() + data
        body += (
            f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; '
            f'filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'
        ).encode()
        body += content + f"\r\n--{boundary}--\r\n".encode()
        result = table(
            self.request(
                "/version", data=body, content_type=f"multipart/form-data; boundary={boundary}"
            ),
            "Modrinth created version",
        )
        identifier = field(result, "id", "Modrinth created version")
        if not re.fullmatch(r"[A-Za-z0-9]+", identifier):
            raise PackError("Invalid Modrinth created version ID")
        return identifier


def read_upload_intent(
    github: GitHub, variable: str, identity: dict[str, object]
) -> dict[str, object] | None:
    response = github.json("GET", f"/actions/variables/{variable}", allow_missing=True)
    if response is None:
        return None
    value = field(table(response, "upload intent variable"), "value", "upload intent variable")
    record = table(json_value(value, "Modrinth upload intent"), "Modrinth upload intent")
    if set(record) != {*identity, "version_id"} or any(
        type(record.get(key)) is not type(expected) or record.get(key) != expected
        for key, expected in identity.items()
    ):
        raise PackError(f"Modrinth upload intent {variable} differs from this exact artifact")
    version_id = record.get("version_id")
    if version_id is not None and (
        not isinstance(version_id, str) or not re.fullmatch(r"[A-Za-z0-9]+", version_id)
    ):
        raise PackError(f"Modrinth upload intent {variable} has an invalid version ID")
    return record


def write_upload_intent(
    github: GitHub, variable: str, identity: dict[str, object], version_id: str | None
) -> None:
    record = identity | {"version_id": version_id}
    payload = json.dumps({"name": variable, "value": json.dumps(record, sort_keys=True)}).encode()
    method = "POST" if version_id is None else "PATCH"
    path = "/actions/variables" if version_id is None else f"/actions/variables/{variable}"
    # The documented creation/update responses have no JSON body (201/204).
    github.request(method, path, payload)
    if read_upload_intent(github, variable, identity) != record:
        raise PackError(f"GitHub did not confirm Modrinth upload intent {variable}")


def recorded_version(
    modrinth: Modrinth,
    version_id: str,
    project_id: str,
    version: str,
    name: str,
    content: bytes,
    minecraft: str,
    notes: str,
) -> str:
    record = modrinth.version(version_id)
    if record.get("project_id") != project_id:
        raise PackError("Modrinth upload receipt references a different project")
    status = matching_version([record], version, name, content, minecraft, notes)
    if status is None:
        raise PackError("Modrinth upload receipt references a different version number")
    return status


def publish_version(project: Project, github: GitHub, modrinth: Modrinth, release: Release) -> str:
    if not release.immutable:
        raise PackError(f"GitHub release {release.tag} is not immutable")
    source = check_project(project)
    version = release.tag[1:]
    version_key(version)
    if source.manifest.version != version or release.prerelease != ("-" in version):
        raise PackError(f"GitHub release {release.tag} disagrees with its source or release type")
    name = f"aeroscapes-reimagine-{version}.mrpack"
    content = github.asset(release, name)
    if content is None or github.asset(release, name + ".sha256") != checksum(name, content):
        raise PackError("GitHub release artifact or checksum is missing or invalid")
    with tempfile.TemporaryDirectory(prefix="aeroscapes-modrinth-") as directory:
        artifact = Path(directory) / name
        artifact.write_bytes(content)
        verify_mrpack(artifact, source, load_artifacts(project))
    release_info = table(github.json("GET", f"/releases/{release.id}"), "GitHub release")
    notes = field(release_info, "body", "GitHub release")
    project_id = field(modrinth.project(), "id", "Modrinth project")
    if not re.fullmatch(r"[A-Za-z0-9]+", project_id):
        raise PackError("Invalid canonical Modrinth project ID")
    existing = matching_version(
        modrinth.versions(), version, name, content, source.manifest.minecraft, notes
    )
    operation = "skipped"
    if existing is None:
        identity: dict[str, object] = {
            "project_id": project_id,
            "version": version,
            "filename": name,
            "sha512": hashlib.sha512(content).hexdigest(),
        }
        variable = (
            "MODRINTH_UPLOAD_"
            + hashlib.sha256(f"{project_id}\0{version}".encode()).hexdigest().upper()
        )
        intent = read_upload_intent(github, variable, identity)
        if intent is not None:
            version_id = intent.get("version_id")
            if not isinstance(version_id, str):
                raise PackError(
                    f"Unresolved Modrinth upload intent {variable}; "
                    "no matching version is visible. "
                    "A previous upload may have succeeded. Retry after visibility or reconcile "
                    "the Modrinth project before changing the receipt. No upload was attempted."
                )
            existing = recorded_version(
                modrinth,
                version_id,
                project_id,
                version,
                name,
                content,
                source.manifest.minecraft,
                notes,
            )
            state = publication_status(field(modrinth.project(), "status", "project"), existing)
            return f"Modrinth {version}: skipped; {state}"
        metadata: dict[str, object] = {
            "project_id": project_id,
            "name": version,
            "version_number": version,
            "changelog": notes,
            "dependencies": [],
            "game_versions": [source.manifest.minecraft],
            "version_type": "beta" if release.prerelease else "release",
            "loaders": ["neoforge"],
            "featured": not release.prerelease,
            "status": "listed",
            "file_parts": ["file"],
            "primary_file": "file",
            "environment": "client_and_server",
        }
        write_upload_intent(github, variable, identity, None)
        version_id = modrinth.upload(metadata, name, content)
        write_upload_intent(github, variable, identity, version_id)
        existing = recorded_version(
            modrinth,
            version_id,
            project_id,
            version,
            name,
            content,
            source.manifest.minecraft,
            notes,
        )
        operation = "uploaded"
    state = publication_status(field(modrinth.project(), "status", "project"), existing)
    return f"Modrinth {version}: {operation}; {state}"


def run(project: Project, github: GitHub, modrinth: Modrinth) -> str:
    releases = [item for item in github.releases() if not item.draft and item.tag.startswith("v")]
    releases.sort(key=lambda item: version_key(item.tag[1:]))
    if not releases:
        raise PackError("A published GitHub release is required before publishing to Modrinth")
    messages: list[str] = []
    for release in releases:
        commit = github.tag_commit(release.tag)
        if commit is None:
            raise PackError(f"GitHub tag {release.tag} is missing")
        with tempfile.TemporaryDirectory(prefix="aeroscapes-modrinth-source-") as directory:
            source_project = export_original(project, commit, Path(directory))
            message = publish_version(source_project, github, modrinth, release)
            print(message)
            messages.append(message)
    return "\n".join(messages)


def main() -> int:
    try:
        if os.environ.get("MODRINTH_DISCLOSURES_CONFIRMED") != "true":
            raise PackError(
                "Set MODRINTH_DISCLOSURES_CONFIRMED=true only after manually selecting the "
                "required AI text/publishing and other applicable disclosures in Modrinth's "
                "project settings; stable API v2 cannot read or update disclosures"
            )
        github = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        modrinth = Modrinth(
            os.environ.get("MODRINTH_PROJECT_ID", ""), os.environ.get("MODRINTH_TOKEN", "")
        )
        project = Project.discover(Path.cwd())
        message = run(project, github, modrinth)
        if os.environ.get("MODRINTH_PROFILE_ENABLED") == "true":
            version = Manifest.parse(read_toml(project.pack / "pack.toml")).version
            if any(
                release.tag == f"v{version}" and not release.draft and release.immutable
                for release in github.releases()
            ):
                profile_message = sync_profile(project, modrinth, disclosures_confirmed=True)
            else:
                profile_message = (
                    f"Modrinth profile: skipped; current pack {version} has no published "
                    "immutable GitHub release"
                )
            print(profile_message)
            message += "\n" + profile_message
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary is not None and summary != "":
            with Path(summary).open("a", encoding="utf-8") as output:
                for line in message.splitlines():
                    output.write(f"- {line}\n")
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
