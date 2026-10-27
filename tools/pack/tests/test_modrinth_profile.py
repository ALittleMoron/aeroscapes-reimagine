"""Project-page publication must preserve provenance, authorization and retry safety."""

import hashlib
import io
import json
import struct
import urllib.request
import zlib
from dataclasses import dataclass
from http.client import HTTPMessage
from pathlib import Path

import pytest

from aeroscapes_pack import modrinth_profile, modrinth_release
from aeroscapes_pack.data import PackError, json_value, read_toml, table
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release
from aeroscapes_pack.models import Project
from aeroscapes_pack.modrinth_profile import load_profile, sync_profile
from aeroscapes_pack.modrinth_release import IconRedirect, Modrinth


def png(width: int = 2, height: int = 2) -> bytes:
    def chunk(kind: bytes, content: bytes) -> bytes:
        return (
            struct.pack(">I", len(content))
            + kind
            + content
            + struct.pack(">I", zlib.crc32(kind + content))
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress((b"\x00" + b"\x80\x90\xa0\xff" * width) * height))
        + chunk(b"IEND", b"")
    )


def write_profile(project: Project, record: dict[str, object]) -> None:
    path = project.root / "tools/pack/publishing/modrinth/profile.toml"
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"{key} = {json.dumps(value)}" for key, value in record.items() if key != "en"]
    lines.append("[en]")
    content = table(record["en"], "en")
    lines.extend(f"{key} = {json.dumps(value)}" for key, value in content.items())
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def profile_record(project: Project) -> dict[str, object]:
    return read_toml(project.root / "tools/pack/publishing/modrinth/profile.toml")


@pytest.fixture
def profile_project(tmp_path: Path) -> Project:
    project = Project(tmp_path)
    project.pack.mkdir()
    (project.pack / "pack.toml").write_text(
        'name = "Pack"\nversion = "0.1.0"\n'
        '[index]\nfile = "index.toml"\nhash-format = "sha256"\nhash = "unused"\n'
        '[versions]\nminecraft = "1.21.1"\nneoforge = "21.1.230"\n',
        encoding="utf-8",
    )
    tool = {"filename": "tool.jar", "url": "https://example.org/tool.jar", "sha256": "a" * 64}
    (project.root / "toolchain.json").write_text(
        json.dumps(
            {
                "java": {"major": 25},
                "packwiz": {"module": "example.org/packwiz", "commit": "a" * 40},
                "bootstrap": tool,
                "installer": tool,
                "neoforge": tool,
            }
        ),
        encoding="utf-8",
    )
    icon = png()
    write_profile(
        project,
        {
            "schema_version": 1,
            "title": "Aeroscapes Reimagine",
            "icon_file": "icon.png",
            "icon_sha256": hashlib.sha256(icon).hexdigest(),
            "icon_origin": "human-authored-unmodified",
            "icon_author": "Human Artist",
            "icon_title": "Floating platforms",
            "icon_source_url": "https://example.org/icon",
            "icon_license_url": "https://creativecommons.org/licenses/by/3.0/",
            "wiki_url": "https://docs.example.org/pack",
            "en": {
                "summary": "Explore floating islands.",
                "body": (
                    "Minecraft {minecraft}, NeoForge {neoforge}, Java {java}. "
                    "[Documentation]({docs}) is available in English and Russian."
                ),
            },
        },
    )
    (project.root / "tools/pack/publishing/modrinth/icon.png").write_bytes(icon)
    return project


def test_local_profile_renders_pinned_versions_links_and_attribution(
    profile_project: Project,
) -> None:
    profile = load_profile(profile_project)
    assert set(profile.payload()) == {"title", "description", "body", "wiki_url"}
    assert profile.summary == "Explore floating islands."
    assert "Minecraft 1.21.1, NeoForge 21.1.230, Java 25" in profile.body
    assert "[Documentation](https://docs.example.org/pack/)" in profile.body
    assert "available in English and Russian" in profile.body
    assert profile.body.count("Floating platforms") == 1
    assert "Human Artist" in profile.body
    assert "https://creativecommons.org/licenses/by/3.0/" in profile.body
    assert profile.icon == png()


@pytest.mark.parametrize(
    ("changes", "error"),
    [
        ({"schema_version": True}, "schema_version"),
        ({"extra": "unknown"}, "schema_version"),
        ({"icon_origin": "ai-assisted"}, "human-authored"),
        ({"icon_file": "../concepts/icon.png"}, "Unsafe pack path"),
        ({"icon_sha256": "a" * 64}, "SHA-256 differs"),
        ({"title": "x" * 65}, "at most 64"),
        ({"wiki_url": "http://example.org/"}, "HTTPS"),
        ({"en": {"summary": "x" * 257, "body": "English"}}, "at most 256"),
        ({"en": {"summary": "English", "body": "{java.__class__}"}}, "only"),
        ({"en": {"summary": "English", "body": "{minecraft!r}"}}, "only"),
        (
            {
                "en": {"summary": "English", "body": "a" * 65530},
            },
            "Rendered",
        ),
    ],
)
def test_invalid_profile_fails_before_api(
    profile_project: Project,
    monkeypatch: pytest.MonkeyPatch,
    changes: dict[str, object],
    error: str,
) -> None:
    write_profile(profile_project, profile_record(profile_project) | changes)
    modrinth = Modrinth("Project1", "secret")

    def forbidden_read() -> dict[str, object]:
        pytest.fail("Invalid local profile must fail before any API call")

    monkeypatch.setattr(modrinth, "project", forbidden_read)
    with pytest.raises(PackError, match=error):
        sync_profile(profile_project, modrinth, disclosures_confirmed=True)


@pytest.mark.parametrize(
    "content", [b"not png", png(2, 3), png()[:-1], png() + b"extra", b"x" * (256 * 1024 + 1)]
)
def test_invalid_icon_is_rejected(profile_project: Project, content: bytes) -> None:
    (profile_project.root / "tools/pack/publishing/modrinth/icon.png").write_bytes(content)
    record = profile_record(profile_project)
    record["icon_sha256"] = hashlib.sha256(content).hexdigest()
    write_profile(profile_project, record)
    with pytest.raises(PackError, match="PNG|256KiB"):
        load_profile(profile_project)


def test_private_concept_is_not_a_publishable_icon(profile_project: Project) -> None:
    concepts = profile_project.root / "tools/pack/publishing/modrinth/concepts"
    concepts.mkdir()
    (concepts / "draft.svg").write_text("<svg>AI-assisted concept</svg>", encoding="utf-8")
    assert load_profile(profile_project).icon == png()
    record = profile_record(profile_project)
    record["icon_file"] = "concepts/draft.svg"
    write_profile(profile_project, record)
    with pytest.raises(PackError, match="icon_file must be icon.png"):
        load_profile(profile_project)


def test_disclosures_must_be_confirmed_before_local_or_remote_work(tmp_path: Path) -> None:
    with pytest.raises(PackError, match="manually.*stable API v2"):
        sync_profile(Project(tmp_path), Modrinth("Project1", "secret"), disclosures_confirmed=False)


@dataclass(slots=True)
class SyncCase:
    project: Project
    modrinth: Modrinth
    current: dict[str, object]
    writes: list[tuple[str, bytes]]
    remote_icon: bytes
    lose_response: str | None


@pytest.fixture
def sync_case(profile_project: Project, monkeypatch: pytest.MonkeyPatch) -> SyncCase:
    profile = load_profile(profile_project)
    case = SyncCase(
        project=profile_project,
        modrinth=Modrinth("Project1", "secret"),
        current=profile.payload()
        | {
            "id": "Project1",
            "project_type": "modpack",
            "raw_icon_url": "https://cdn.modrinth.com/data/Project1/icon-raw.png",
            "source_url": "owner-managed",
            "gallery": ["owner-managed"],
        },
        writes=[],
        remote_icon=profile.icon,
        lose_response=None,
    )
    monkeypatch.setattr(case.modrinth, "project", lambda: dict(case.current))
    monkeypatch.setattr(case.modrinth, "read_icon", lambda _url: case.remote_icon)

    def request(
        path: str,
        *,
        data: bytes | None = None,
        content_type: str = "",
        method: str | None = None,
    ) -> object:
        assert method == "PATCH"
        assert data is not None
        case.writes.append((path, data))
        operation = "icon" if path.endswith("/icon?ext=png") else "page"
        if operation == "page":
            assert content_type == "application/json"
            case.current.update(table(json_value(data, "profile update"), "profile update"))
        else:
            assert content_type == "image/png"
            case.remote_icon = data
        if case.lose_response == operation:
            case.lose_response = None
            raise PackError("successful write response lost")
        return None

    monkeypatch.setattr(case.modrinth, "request", request)
    return case


def test_matching_profile_never_writes(sync_case: SyncCase) -> None:
    assert sync_profile(sync_case.project, sync_case.modrinth, disclosures_confirmed=True).endswith(
        "unchanged"
    )
    assert sync_case.writes == []


def test_page_and_raw_icon_update_are_idempotent(sync_case: SyncCase) -> None:
    sync_case.current["body"] = "Old description"
    sync_case.current["title"] = "Old title"
    sync_case.remote_icon = b"old icon"
    result = sync_profile(sync_case.project, sync_case.modrinth, disclosures_confirmed=True)
    assert result == "Modrinth profile: page updated, icon updated"
    assert len(sync_case.writes) == 2
    assert set(table(json_value(sync_case.writes[0][1], "patch"), "patch")) == {"title", "body"}
    assert sync_case.current["source_url"] == "owner-managed"
    assert sync_case.current["gallery"] == ["owner-managed"]
    assert sync_profile(sync_case.project, sync_case.modrinth, disclosures_confirmed=True).endswith(
        "unchanged"
    )
    assert len(sync_case.writes) == 2


@pytest.mark.parametrize("operation", ["page", "icon"])
def test_lost_response_retry_does_not_repeat_a_successful_write(
    sync_case: SyncCase, operation: str
) -> None:
    sync_case.current["body"] = "Old description"
    sync_case.remote_icon = b"old icon"
    sync_case.lose_response = operation
    with pytest.raises(PackError, match="response lost"):
        sync_profile(sync_case.project, sync_case.modrinth, disclosures_confirmed=True)
    sync_profile(sync_case.project, sync_case.modrinth, disclosures_confirmed=True)
    assert [path for path, _content in sync_case.writes] == [
        "/project/Project1",
        "/project/Project1/icon?ext=png",
    ]


class Response(io.BytesIO):
    def __init__(self, content: bytes, url: str) -> None:
        super().__init__(content)
        self.url = url

    def geturl(self) -> str:
        return self.url


def test_api_patch_accepts_empty_204_response(monkeypatch: pytest.MonkeyPatch) -> None:
    modrinth = Modrinth("Project1", "secret")

    def open_response(request: urllib.request.Request, timeout: int) -> Response:
        assert request.get_method() == "PATCH"
        assert request.get_header("Authorization") == "secret"
        assert timeout == 120
        return Response(b"", request.full_url)

    monkeypatch.setattr(modrinth.opener, "open", open_response)
    assert modrinth.request("/project/Project1", method="PATCH", data=b"{}") is None


@pytest.mark.parametrize(
    "url",
    [
        "http://cdn.modrinth.com/icon.png",
        "https://evil.example/icon.png",
        "https://cdn.modrinth.com.evil.example/icon.png",
        "https://secret@cdn.modrinth.com/icon.png",
        "https://cdn.modrinth.com:444/icon.png",
        "https://cdn.modrinth.com/icon.png#fragment",
    ],
)
def test_unsafe_icon_urls_and_redirects_fail_before_network(url: str) -> None:
    modrinth = Modrinth("Project1", "secret")
    with pytest.raises(PackError, match="credential-free HTTPS"):
        modrinth.read_icon(url)
    with pytest.raises(PackError, match="credential-free HTTPS"):
        IconRedirect().redirect_request(
            urllib.request.Request("https://cdn.modrinth.com/icon.png"),
            io.BytesIO(),
            302,
            "Found",
            HTTPMessage(),
            url,
        )


@pytest.mark.parametrize(
    "final_url", ["https://cdn.modrinth.com/icon.png", "https://evil.example/icon.png"]
)
def test_icon_get_has_no_credentials_and_validates_final_host(
    monkeypatch: pytest.MonkeyPatch, final_url: str
) -> None:
    modrinth = Modrinth("Project1", "secret")

    class Opener:
        def open(self, request: urllib.request.Request, timeout: int) -> Response:
            assert request.get_header("Authorization") is None
            assert timeout == 30
            return Response(png(), final_url)

    monkeypatch.setattr(urllib.request, "build_opener", lambda _redirect: Opener())
    if final_url.startswith("https://cdn.modrinth.com/"):
        assert modrinth.read_icon(final_url) == png()
    else:
        with pytest.raises(PackError, match="credential-free HTTPS"):
            modrinth.read_icon("https://cdn.modrinth.com/icon.png")


def test_raw_icon_download_is_bounded(monkeypatch: pytest.MonkeyPatch) -> None:
    modrinth = Modrinth("Project1", "secret")

    class Opener:
        def open(self, request: urllib.request.Request, timeout: int) -> Response:
            assert timeout == 30
            return Response(b"x" * (512 * 1024 + 1), request.full_url)

    monkeypatch.setattr(urllib.request, "build_opener", lambda _redirect: Opener())
    with pytest.raises(PackError, match="exceeds 512KiB"):
        modrinth.read_icon("https://cdn.modrinth.com/icon.png")


def test_preview_compiles_without_publication(
    profile_project: Project, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.chdir(profile_project.root)
    output = profile_project.runtime / "profile-preview"
    assert modrinth_profile.main(["--output", str(output)]) == 0
    assert (output / "icon.png").read_bytes() == png()
    assert (
        table(json_value((output / "payload.json").read_bytes(), "preview"), "preview")
        == load_profile(profile_project).payload()
    )
    assert (output / "description.en.md").read_text(encoding="utf-8") == (
        load_profile(profile_project).body + "\n"
    )
    assert modrinth_profile.main([]) == 0


@pytest.mark.parametrize("confirmation", [None, "false", "TRUE"])
def test_release_main_defaults_to_no_writes(
    monkeypatch: pytest.MonkeyPatch, confirmation: str | None
) -> None:
    monkeypatch.delenv("MODRINTH_DISCLOSURES_CONFIRMED", raising=False)
    if confirmation is not None:
        monkeypatch.setenv("MODRINTH_DISCLOSURES_CONFIRMED", confirmation)

    def forbidden_client(_repository: str, _token: str) -> GitHub:
        pytest.fail("Disclosure gate must run before constructing API clients")

    monkeypatch.setattr(modrinth_release, "GitHub", forbidden_client)
    assert modrinth_release.main() == 1


@pytest.mark.parametrize(
    ("release", "expected_syncs"),
    [
        (Release(1, "v0.1.0", False, False, "", True), 1),
        (Release(1, "v0.1.0", False, False, "", False), 0),
        (Release(1, "v0.1.0", True, False, "", True), 0),
        (Release(1, "v0.0.9", False, False, "", True), 0),
    ],
)
def test_release_syncs_profile_once_only_for_a_published_immutable_current_version(
    profile_project: Project,
    monkeypatch: pytest.MonkeyPatch,
    release: Release,
    expected_syncs: int,
) -> None:
    monkeypatch.chdir(profile_project.root)
    for key, value in {
        "MODRINTH_DISCLOSURES_CONFIRMED": "true",
        "MODRINTH_PROFILE_ENABLED": "true",
        "GITHUB_REPOSITORY": "owner/repo",
        "GITHUB_TOKEN": "github-secret",
        "MODRINTH_PROJECT_ID": "Project1",
        "MODRINTH_TOKEN": "modrinth-secret",
    }.items():
        monkeypatch.setenv(key, value)
    monkeypatch.delenv("GITHUB_STEP_SUMMARY", raising=False)
    monkeypatch.setattr(GitHub, "releases", lambda _self: (release,))
    events: list[str] = []

    def publish_versions(_project: Project, _github: GitHub, _modrinth: Modrinth) -> str:
        events.append("versions")
        return "published versions"

    def profile_sync(_project: Project, _modrinth: Modrinth, *, disclosures_confirmed: bool) -> str:
        assert disclosures_confirmed
        events.append("profile")
        return "profile synced"

    monkeypatch.setattr(modrinth_release, "run", publish_versions)
    monkeypatch.setattr(modrinth_release, "sync_profile", profile_sync)
    assert modrinth_release.main() == 0
    assert events == ["versions"] + ["profile"] * expected_syncs
