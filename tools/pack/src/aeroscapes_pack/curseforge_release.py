"""Preflight an optional CurseForge channel without an unsafe upload retry."""

import os
import re
import sys
import tempfile
from pathlib import Path

from aeroscapes_pack.curseforge_pack import build_client_archive
from aeroscapes_pack.data import PackError
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release, checksum
from aeroscapes_pack.models import Project, load_artifacts
from aeroscapes_pack.pack import check_project, verify_mrpack
from aeroscapes_pack.release import version_key
from aeroscapes_pack.release_cli import export_original

UPLOAD_BLOCKER = (
    "CurseForge publication is not implemented safely: its documented upload API has no "
    "idempotency key or authenticated listing of pending private uploads. A lost response "
    "can leave an uploaded file without a known ID; an empty public file list does not prove "
    "absence. Add a verified pending-file reconciliation capability or an explicitly "
    "initialized durable upload journal before enabling publication. No upload was attempted."
)


def preflight_release(
    project: Project, github: GitHub, release: Release, directory: Path, project_id: int
) -> Path:
    if release.draft or not release.immutable or not release.tag.startswith("v"):
        raise PackError("CurseForge requires a published immutable GitHub version release")
    version = release.tag[1:]
    version_key(version)
    source = check_project(project)
    if source.manifest.version != version or release.prerelease != ("-" in version):
        raise PackError("GitHub release disagrees with its exact source or release type")
    name = f"aeroscapes-reimagine-{version}.mrpack"
    content = github.asset(release, name)
    if content is None or github.asset(release, name + ".sha256") != checksum(name, content):
        raise PackError("GitHub release artifact or checksum is missing or invalid")
    artifact = directory / name
    artifact.write_bytes(content)
    verify_mrpack(artifact, source, load_artifacts(project))
    return build_client_archive(
        project, directory / f"aeroscapes-reimagine-{version}-curseforge.zip", project_id
    )


def run(project: Project, github: GitHub, project_id: int) -> None:
    if isinstance(project_id, bool) or project_id <= 0:
        raise PackError("CURSEFORGE_PROJECT_ID must be a positive numeric project ID")
    releases = [item for item in github.releases() if not item.draft and item.tag.startswith("v")]
    releases.sort(key=lambda item: version_key(item.tag[1:]))
    if not releases:
        raise PackError("A published GitHub release is required for CurseForge preflight")
    for release in releases:
        commit = github.tag_commit(release.tag)
        if commit is None:
            raise PackError(f"GitHub tag {release.tag} is missing")
        with tempfile.TemporaryDirectory(prefix="aeroscapes-curseforge-source-") as directory:
            location = Path(directory)
            original = export_original(project, commit, location)
            preflight_release(original, github, release, location, project_id)
    raise PackError(UPLOAD_BLOCKER)


def main() -> int:
    try:
        project_id = os.environ.get("CURSEFORGE_PROJECT_ID", "")
        if not re.fullmatch(r"[1-9][0-9]*", project_id):
            raise PackError("CURSEFORGE_PROJECT_ID must be a positive numeric project ID")
        github = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        run(Project.discover(Path.cwd()), github, int(project_id))
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
