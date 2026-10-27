"""Publish a checked export from the exact GitHub Actions commit."""

import io
import re
import tarfile
import tempfile
import zipfile
from pathlib import Path

from aeroscapes_pack.data import PackError, array, integer, table
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import checksum, publish_release
from aeroscapes_pack.models import Project, load_artifacts
from aeroscapes_pack.pack import check_project, verify_mrpack
from aeroscapes_pack.release import changelog_between, version_key
from aeroscapes_pack.runtime import executable, run_command


def git(root: Path, *arguments: str) -> str:
    return run_command([executable("git"), *arguments], directory=root, timeout_seconds=60).stdout


def release_body(
    project: Project, github: GitHub, version: str, commit: str, git_root: Path
) -> str:
    published = [
        release.tag
        for release in github.releases()
        if not release.draft and release.tag.startswith("v")
    ]
    previous = max(published, key=lambda tag: version_key(tag[1:])) if published else None
    previous_commit = github.tag_commit(previous) if previous is not None else None
    if previous is not None and previous_commit is None:
        raise PackError(f"Previous release tag {previous} is missing")
    if previous_commit is not None:
        git(git_root, "merge-base", "--is-ancestor", previous_commit, commit)
    generated = github.generated_notes(f"v{version}", commit, previous)
    english = changelog_between(
        (project.root / "docs/en/changelog.md").read_text(),
        version,
        previous[1:] if previous is not None else None,
    )
    russian = changelog_between(
        (project.root / "docs/ru/changelog.md").read_text(),
        version,
        previous[1:] if previous is not None else None,
    )
    revision = f"{previous_commit}..{commit}" if previous_commit is not None else commit
    log = git(git_root, "log", "--format=%H%x09%s", revision)
    commits: list[str] = []
    for line in log.splitlines():
        sha, separator, subject = line.partition("\t")
        if not separator or not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise PackError("Unexpected git log output")
        subject = subject.replace("[", "\\[").replace("]", "\\]")
        commits.append(
            f"- [{sha[:7]}](https://github.com/{github.repository}/commit/{sha}) {subject}"
        )
    owner, name = github.repository.split("/", 1)
    site = f"https://{owner}.github.io/{name}/"
    body = (
        f"## English\n\n{english}\n\n## Русский\n\n{russian}\n\n"
        f"## Pull requests\n\n{generated}\n\n## Commits\n\n"
        + "\n".join(commits)
        + f"\n\n[Changelog]({site}en/changelog/) · [Documentation]({site})\n"
        + f"\n<!-- aeroscapes-ci-commit:{commit} -->\n"
    )
    if len(body.encode()) > 120_000:
        raise PackError("Release notes exceed the safe GitHub size; shorten the changelog")
    return body


def export_original(project: Project, commit: str, directory: Path) -> Project:
    """Extract an exact commit into a temporary directory without a Git worktree."""
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise PackError("Release has no exact source commit")
    snapshot = directory / "source"
    snapshot.mkdir()
    archive_path = directory / "source.tar"
    git(project.root, "archive", "--format=tar", f"--output={archive_path}", commit)
    with tarfile.open(archive_path, "r") as archive:
        archive.extractall(snapshot, filter="data")
    return Project(snapshot)


def checked_artifact(github: GitHub, run_id: int, attempt: int, name: str) -> bytes:
    """Read only the immutable artifact belonging to the successful CI attempt."""
    expected = f"release-{run_id}-{attempt}"
    matches: list[dict[str, object]] = []
    page = 1
    while True:
        response = table(
            github.json("GET", f"/actions/runs/{run_id}/artifacts?per_page=100&page={page}"),
            "artifacts",
        )
        artifacts = [
            table(value, "artifact") for value in array(response.get("artifacts"), "artifacts")
        ]
        matches.extend(value for value in artifacts if value.get("name") == expected)
        if len(artifacts) < 100:
            break
        page += 1
    if len(matches) != 1 or matches[0].get("expired") is not False:
        raise PackError(f"Missing or expired verified CI artifact {expected}; rerun its CI")
    artifact_id = integer(matches[0].get("id"), "artifact.id")
    content = github.request("GET", f"/actions/artifacts/{artifact_id}/zip")
    if content is None:
        raise PackError(f"CI artifact {expected} could not be downloaded")
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        names = archive.namelist()
        if len(names) != 2 or set(names) != {name, name + ".sha256"}:
            raise PackError("Unexpected files in the verified CI artifact")
        result = archive.read(name)
        if archive.read(name + ".sha256") != checksum(name, result):
            raise PackError("CI artifact checksum differs")
        return result


def publish_checked_run(
    project: Project, github: GitHub, commit: str, run_id: int, attempt: int
) -> str:
    """Publish checked bytes without executing historical source with release credentials."""
    with tempfile.TemporaryDirectory(prefix="aeroscapes-publish-") as directory:
        source_project = export_original(project, commit, Path(directory))
        source = check_project(source_project)
        version = source.manifest.version
        version_key(version)
        artifact = Path(directory) / f"aeroscapes-reimagine-{version}.mrpack"
        artifact.write_bytes(checked_artifact(github, run_id, attempt, artifact.name))
        verify_mrpack(artifact, source, load_artifacts(source_project))
        github.ensure_immutable_enabled()
        existing = next((item for item in github.releases() if item.tag == f"v{version}"), None)
        body = (
            "" if existing else release_body(source_project, github, version, commit, project.root)
        )
        result = publish_release(github, version, commit, artifact, body)
        return f"GitHub release {version}: {result}"
