"""Modrinth releases must preserve the checked GitHub artifact and status."""

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import pytest

from aeroscapes_pack import modrinth_release
from aeroscapes_pack.data import PackError, field, json_value, table
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release, checksum
from aeroscapes_pack.models import Manifest, Project
from aeroscapes_pack.modrinth_release import Modrinth, matching_version, publication_status
from aeroscapes_pack.pack import SourcePack


def test_existing_version_requires_exact_original_file() -> None:
    content = b"checked pack"
    digest = hashlib.sha512(content).hexdigest()
    records: list[object] = [
        {
            "version_number": "0.1.0-test.1",
            "status": "listed",
            "version_type": "beta",
            "game_versions": ["1.21.1"],
            "loaders": ["neoforge"],
            "environment": "client_and_server",
            "changelog": "notes",
            "files": [{"filename": "pack.mrpack", "hashes": {"sha512": digest}}],
        }
    ]
    assert (
        matching_version(records, "0.1.0-test.1", "pack.mrpack", content, "1.21.1", "notes")
        == "listed"
    )
    assert (
        matching_version(records, "0.1.0-test.2", "pack.mrpack", content, "1.21.1", "notes") is None
    )
    with pytest.raises(PackError, match="different"):
        matching_version(records, "0.1.0-test.1", "pack.mrpack", b"changed", "1.21.1", "notes")


def test_project_moderation_is_distinct_from_upload_status() -> None:
    assert publication_status("approved", "listed") == "available"
    assert publication_status("processing", "listed") == "awaiting moderation"
    assert publication_status("approved", "draft") == "uploaded, not listed"
    assert publication_status("rejected", "listed") == "project rejected; action required"


def test_existing_prerelease_must_not_be_marked_stable() -> None:
    content = b"pack"
    records: list[object] = [
        {
            "version_number": "0.1.0-test.1",
            "status": "listed",
            "version_type": "release",
            "game_versions": ["1.21.1"],
            "loaders": ["neoforge"],
            "environment": "client_and_server",
            "changelog": "notes",
            "files": [
                {
                    "filename": "pack.mrpack",
                    "hashes": {"sha512": hashlib.sha512(content).hexdigest()},
                }
            ],
        }
    ]
    with pytest.raises(PackError, match="metadata"):
        matching_version(records, "0.1.0-test.1", "pack.mrpack", content, "1.21.1", "notes")


@dataclass(slots=True)
class PublicationCase:
    project: Project
    github: GitHub
    modrinth: Modrinth
    records: list[object]
    uploads: list[dict[str, object]]
    variables: dict[str, str]


@pytest.fixture
def publication_case(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> PublicationCase:
    content = b"verified archive"
    version = "0.1.0-test.1"
    name = f"aeroscapes-reimagine-{version}.mrpack"
    source = SourcePack(
        manifest=Manifest(
            name="Pack",
            version=version,
            index_file="index.toml",
            index_algorithm="sha256",
            index_hash="a" * 64,
            minecraft="1.21.1",
            neoforge="21.1.230",
        ),
        dependencies={},
        overrides={},
    )
    github = GitHub("owner/repo", "github-token")
    modrinth = Modrinth("project-id", "modrinth-token")
    records: list[object] = []
    metadata_records: list[dict[str, object]] = []
    variables: dict[str, str] = {}
    monkeypatch.setattr(modrinth_release, "check_project", lambda _project: source)
    monkeypatch.setattr(modrinth_release, "load_artifacts", lambda _project: ())
    monkeypatch.setattr(modrinth_release, "export_original", lambda *_args: Project(tmp_path))
    monkeypatch.setattr(modrinth_release, "verify_mrpack", lambda *_arguments: None)
    monkeypatch.setattr(
        github, "releases", lambda: (Release(1, "v" + version, False, True, "", True),)
    )
    monkeypatch.setattr(github, "tag_commit", lambda _tag: "a" * 40)
    monkeypatch.setattr(
        github, "asset", lambda _release, path: content if path == name else checksum(name, content)
    )

    def response(method: str, path: str, *, allow_missing: bool = False) -> object:
        assert method == "GET"
        if path == "/releases/1":
            return {"body": "shared notes"}
        assert allow_missing
        variable = path.rsplit("/", 1)[-1]
        return {"value": variables[variable]} if variable in variables else None

    def request(method: str, path: str, data: bytes | None = None) -> bytes:
        assert data is not None
        payload = table(json_value(data, "variable request"), "variable request")
        variable = field(payload, "name", "variable request")
        if method == "POST":
            assert path == "/actions/variables"
            assert variable not in variables
        else:
            assert method == "PATCH"
            assert path == f"/actions/variables/{variable}"
            assert variable in variables
        variables[variable] = field(payload, "value", "variable request")
        return b""

    monkeypatch.setattr(github, "json", response)
    monkeypatch.setattr(github, "request", request)
    monkeypatch.setattr(
        modrinth,
        "project",
        lambda: {"id": "Project1", "project_type": "modpack", "status": "processing"},
    )
    monkeypatch.setattr(modrinth, "versions", lambda: records)

    def upload(metadata: dict[str, object], filename: str, contents: bytes) -> str:
        assert filename == name
        assert contents == content
        assert len(variables) == 1
        intent = table(json_value(next(iter(variables.values())), "intent"), "intent")
        assert intent["version_id"] is None
        assert intent["sha512"] == hashlib.sha512(content).hexdigest()
        metadata_records.append(metadata)
        records.append(
            metadata
            | {
                "id": "Created1",
                "files": [
                    {"filename": name, "hashes": {"sha512": hashlib.sha512(content).hexdigest()}}
                ],
            }
        )
        return "Created1"

    def version_record(identifier: str) -> dict[str, object]:
        assert identifier == "Created1"
        return table(records[0], "recorded version")

    monkeypatch.setattr(modrinth, "upload", upload)
    monkeypatch.setattr(modrinth, "version", version_record)
    return PublicationCase(
        project=Project(tmp_path),
        github=github,
        modrinth=modrinth,
        records=records,
        uploads=metadata_records,
        variables=variables,
    )


def test_upload_retries_without_duplicate_and_reports_moderation(
    publication_case: PublicationCase,
) -> None:
    case = publication_case
    assert "uploaded; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert "skipped; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert len(case.uploads) == 1
    assert case.uploads[0]["version_type"] == "beta"
    assert case.uploads[0]["changelog"] == "shared notes"


def test_missing_previous_release_is_retried_before_new_release(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from aeroscapes_pack import modrinth_release
    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.models import Project
    from aeroscapes_pack.modrinth_release import Modrinth

    github = GitHub("owner/repo", "github-token")
    modrinth = Modrinth("project-id", "modrinth-token")
    releases = (
        Release(2, "v0.2.0", False, False, "", True),
        Release(1, "v0.1.0", False, False, "", True),
    )
    monkeypatch.setattr(github, "releases", lambda: releases)
    monkeypatch.setattr(github, "tag_commit", lambda tag: "a" * 40 if tag == "v0.1.0" else "b" * 40)
    monkeypatch.setattr(modrinth_release, "export_original", lambda *_args: Project(tmp_path))
    processed: list[str] = []

    def publish(_project: Project, _github: GitHub, _modrinth: Modrinth, release: Release) -> str:
        processed.append(release.tag)
        return release.tag

    monkeypatch.setattr(modrinth_release, "publish_version", publish)
    modrinth_release.run(Project(tmp_path), github, modrinth)
    assert processed == ["v0.1.0", "v0.2.0"]


def test_mutable_github_release_is_not_uploaded(tmp_path: Path) -> None:
    from aeroscapes_pack.github_api import GitHub
    from aeroscapes_pack.github_release import Release
    from aeroscapes_pack.models import Project
    from aeroscapes_pack.modrinth_release import Modrinth, publish_version

    with pytest.raises(PackError, match="not immutable"):
        publish_version(
            Project(tmp_path),
            GitHub("owner/repo", "test"),
            Modrinth("id", "test"),
            Release(1, "v0.1.0", False, False, "", False),
        )


def test_existing_version_environment_must_match(publication_case: PublicationCase) -> None:
    case = publication_case
    modrinth_release.run(case.project, case.github, case.modrinth)
    record = table(case.records[0], "version")
    case.records[0] = record | {"environment": "client_only"}
    with pytest.raises(PackError, match="different metadata"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    assert len(case.uploads) == 1


def test_lost_upload_response_waits_for_visibility_without_duplicate_post(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case
    original_upload = case.modrinth.upload

    def lost_response(metadata: dict[str, object], name: str, content: bytes) -> str:
        original_upload(metadata, name, content)
        raise TimeoutError("response lost after server created version")

    monkeypatch.setattr(case.modrinth, "upload", lost_response)
    monkeypatch.setattr(case.modrinth, "versions", list)
    with pytest.raises(TimeoutError, match="response lost"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    with pytest.raises(PackError, match="Unresolved Modrinth upload intent"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    assert len(case.uploads) == 1
    monkeypatch.setattr(case.modrinth, "versions", lambda: case.records)
    assert "skipped; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert len(case.uploads) == 1


def test_saved_created_id_bypasses_delayed_version_list(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case
    monkeypatch.setattr(case.modrinth, "versions", list)
    assert "uploaded; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert "skipped; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert len(case.uploads) == 1
    intent = table(json_value(next(iter(case.variables.values())), "receipt"), "receipt")
    assert intent["version_id"] == "Created1"


def test_saved_id_survives_failed_direct_read_after_upload(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case
    original_version = case.modrinth.version

    def not_visible(_version_id: str) -> dict[str, object]:
        raise PackError("version ID not visible yet")

    monkeypatch.setattr(case.modrinth, "versions", list)
    monkeypatch.setattr(case.modrinth, "version", not_visible)
    with pytest.raises(PackError, match="not visible yet"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    monkeypatch.setattr(case.modrinth, "version", original_version)
    assert "skipped; awaiting moderation" in modrinth_release.run(
        case.project, case.github, case.modrinth
    )
    assert len(case.uploads) == 1


def test_unconfirmed_intent_never_starts_upload(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case

    def ignored_write(_method: str, _path: str, _data: bytes | None = None) -> bytes:
        return b""

    monkeypatch.setattr(case.github, "request", ignored_write)
    with pytest.raises(PackError, match="did not confirm"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    assert not case.uploads


def test_changed_receipt_cannot_trigger_another_upload(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case
    modrinth_release.run(case.project, case.github, case.modrinth)
    variable = next(iter(case.variables))
    # A receipt is bound to the exact file hash, not only a version label.
    intent = table(json_value(case.variables[variable], "receipt"), "receipt")

    case.variables[variable] = json.dumps(intent | {"sha512": "a" * 128})
    monkeypatch.setattr(case.modrinth, "versions", list)
    with pytest.raises(PackError, match="differs from this exact artifact"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    assert len(case.uploads) == 1


def test_receipt_version_must_belong_to_the_canonical_project(
    publication_case: PublicationCase,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case = publication_case
    modrinth_release.run(case.project, case.github, case.modrinth)
    record = table(case.records[0], "created version")
    case.records[0] = record | {"project_id": "OtherProject"}
    monkeypatch.setattr(case.modrinth, "versions", list)
    with pytest.raises(PackError, match="different project"):
        modrinth_release.run(case.project, case.github, case.modrinth)
    assert len(case.uploads) == 1


def test_upload_reads_successful_response_id(monkeypatch: pytest.MonkeyPatch) -> None:
    modrinth = Modrinth("Project1", "fixture")

    def response(path: str, *, data: bytes | None = None, content_type: str = "") -> object:
        assert path == "/version"
        assert data is not None
        assert b'filename="pack.mrpack"' in data
        assert content_type.startswith("multipart/form-data; boundary=")
        return {"id": "Created1"}

    monkeypatch.setattr(modrinth, "request", response)
    assert modrinth.upload({"project_id": "Project1"}, "pack.mrpack", b"pack") == "Created1"
