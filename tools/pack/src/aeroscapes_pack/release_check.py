"""Read-only CI gate before a checked integration artifact can be retained."""

import os
import sys
from pathlib import Path

from aeroscapes_pack.data import PackError, field, read_json, read_toml, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import checksum, content_fingerprint
from aeroscapes_pack.models import Project
from aeroscapes_pack.release import (
    archive_fingerprint,
    changelog_section,
    release_decision,
    require_release_title,
    version_key,
)


def integration_title(event_name: str, payload: object, repository: str) -> str | None:
    event = table(payload, "GitHub event")
    source = table(event.get("repository"), "event.repository")
    if field(source, "full_name", "event.repository") != repository:
        raise PackError("Integration event belongs to another repository")
    if event_name == "push":
        ref = field(event, "ref", "event")
        if not ref.startswith("refs/heads/"):
            raise PackError("Expected a branch push ref")
        if ref != "refs/heads/main":
            return None
        commit = table(event.get("head_commit"), "event.head_commit")
        return field(commit, "message", "event.head_commit").splitlines()[0]
    if event_name == "pull_request":
        request = table(event.get("pull_request"), "event.pull_request")
        base = table(request.get("base"), "pull_request.base")
        if field(base, "ref", "pull_request.base") != "main":
            return None
        return field(request, "title", "pull_request").splitlines()[0]
    raise PackError("Release integration check requires a push or pull_request event")


def check_candidate(github: GitHub, version: str, artifact: Path, title: str) -> bool:
    version_key(version)
    releases = github.releases()
    matching = [release for release in releases if release.tag == f"v{version}"]
    if len(matching) > 1:
        raise PackError(f"Multiple releases for {version}")
    current = matching[0] if matching else None
    # Read-only callers may not see drafts. The queue and publisher own reservations;
    # keep any visible draft away from asset(), which can clean draft placeholders.
    if current is not None and current.draft:
        raise PackError(f"Release {version} has an unfinished draft; wait and rerun CI")
    published = tuple(
        release.tag[1:] for release in releases if not release.draft and release.tag.startswith("v")
    )
    if current is not None:
        if not current.immutable or current.prerelease != ("-" in version):
            raise PackError(f"Published release {version} has an invalid type or is not immutable")
        original = github.asset(current, artifact.name)
        original_checksum = github.asset(current, artifact.name + ".sha256")
        if original is None or original_checksum != checksum(artifact.name, original):
            raise PackError(f"Published release {version} has no valid archive and checksum")
        release_decision(
            version, published, content_fingerprint(original) == archive_fingerprint(artifact)
        )
        return False
    release_decision(version, published, None)
    require_release_title(version=version, title=title)
    return True


def main() -> int:
    try:
        repository = os.environ.get("GITHUB_REPOSITORY", "")
        event_path = os.environ.get("GITHUB_EVENT_PATH", "")
        if not repository or not event_path:
            raise PackError("Expected GITHUB_REPOSITORY and GITHUB_EVENT_PATH")
        title = integration_title(
            os.environ.get("GITHUB_EVENT_NAME", ""), read_json(Path(event_path)), repository
        )
        if title is None:
            print("Preparatory branch: release integration check skipped")
            return 0
        project = Project.discover(Path.cwd())
        version = field(read_toml(project.pack / "pack.toml"), "version", "pack")
        artifact = project.root / "dist" / f"aeroscapes-reimagine-{version}.mrpack"
        github = GitHub(repository, os.environ.get("GITHUB_TOKEN", ""))
        if check_candidate(github, version, artifact, title):
            for locale in ("en", "ru"):
                changelog_section(
                    (project.root / "docs" / locale / "changelog.md").read_text(), version
                )
            print(f"Release integration checked: {version}")
        else:
            print(f"Export matches published {version}: no new pack release")
        return 0
    except EXPECTED_ERRORS as error:
        print(f"Error: {error_message(error)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
