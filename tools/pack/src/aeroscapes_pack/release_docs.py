"""Render publication status into the ephemeral documentation checkout."""

import argparse
import os
import sys
from collections.abc import Sequence
from pathlib import Path

from aeroscapes_pack.data import PackError, field, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.github_api import GitHub
from aeroscapes_pack.github_release import Release, checksum
from aeroscapes_pack.models import Project
from aeroscapes_pack.release import version_key


def release_listing(releases: tuple[Release, ...], repository: str, locale: str) -> str:
    published = [
        release for release in releases if not release.draft and release.tag.startswith("v")
    ]
    published.sort(key=lambda release: version_key(release.tag[1:]), reverse=True)
    if not published:
        return (
            "No public versions are available yet."
            if locale == "en"
            else "Публичных версий пока нет."
        )
    lines = []
    for release in published:
        kind = (
            ("prerelease" if release.prerelease else "stable")
            if locale == "en"
            else ("предварительная" if release.prerelease else "стабильная")
        )
        lines.append(
            f"- [{release.tag[1:]}](https://github.com/{repository}/releases/tag/"
            f"{release.tag}) — {kind}"
        )
    return "\n".join(lines)


def checked_releases(github: GitHub) -> tuple[Release, ...]:
    result: list[Release] = []
    for release in github.releases():
        if release.draft or not release.tag.startswith("v"):
            continue
        version_key(release.tag[1:])
        if not release.immutable:
            raise PackError(f"GitHub release {release.tag} is not immutable")
        if release.prerelease != ("-" in release.tag[1:]):
            raise PackError(f"GitHub release {release.tag} has an incorrect release type")
        name = f"aeroscapes-reimagine-{release.tag[1:]}.mrpack"
        content = github.asset(release, name)
        if content is None:
            raise PackError(f"GitHub release {release.tag} is missing asset {name}")
        if github.asset(release, name + ".sha256") != checksum(name, content):
            raise PackError(f"GitHub release {release.tag} is missing a valid checksum for {name}")
        result.append(release)
    return tuple(result)


def is_current(github: GitHub, commit: str) -> bool:
    ref = table(github.json("GET", "/git/ref/heads/main"), "main ref")
    head = field(table(ref.get("object"), "main ref object"), "sha", "main ref object")
    return head == commit


def prepare(project: Project, github: GitHub, commit: str) -> bool:
    if not is_current(github, commit):
        print("Documentation skipped: a newer commit is already on main")
        return False
    releases = checked_releases(github)
    repository = table(github.json("GET", ""), "repository")
    private = repository.get("private")
    if not isinstance(private, bool):
        raise PackError("GitHub repository visibility is missing")
    for locale in ("en", "ru"):
        install = project.root / "docs" / locale / "install.md"
        content = install.read_text(encoding="utf-8")
        introduction = (
            "Import a downloaded or supplied `.mrpack` as a new instance in Modrinth App."
            if locale == "en"
            else (
                "Импортируйте скачанный или полученный `.mrpack` "
                "как новый экземпляр в Modrinth App."
            )
        )
        if introduction not in content:
            raise PackError(f"Installation instruction marker changed in {install}")
        listing = release_listing(releases, github.repository, locale)
        if private and releases:
            notice = (
                "These downloads require repository access because the repository is private."
                if locale == "en"
                else "Для скачивания нужен доступ к репозиторию: он закрытый."
            )
            listing = notice + "\n\n" + listing
        content = content.replace(introduction, introduction + "\n\n" + listing, 1)
        if releases and not private:
            content = content.replace("status: partial", "status: available", 1)
        install.write_text(content, encoding="utf-8")
    return True


class Options(argparse.Namespace):
    check_current: bool = False


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Prepare documentation for the current main commit"
    )
    parser.add_argument(
        "--check-current", action="store_true", help="Only check deployment freshness"
    )
    options = parser.parse_args(argv, namespace=Options())
    try:
        if os.environ.get("CI") != "true":
            raise PackError("Documentation status rendering requires an ephemeral CI checkout")
        github = GitHub(os.environ.get("GITHUB_REPOSITORY", ""), os.environ.get("GITHUB_TOKEN", ""))
        commit = os.environ.get("DOCS_COMMIT") or os.environ.get("GITHUB_SHA", "")
        result = (
            is_current(github, commit)
            if options.check_current
            else prepare(Project.discover(Path.cwd()), github, commit)
        )
        output = os.environ.get("GITHUB_OUTPUT")
        if output is None:
            raise PackError("GITHUB_OUTPUT is required")
        with Path(output).open("a", encoding="utf-8") as stream:
            stream.write(f"deploy={'true' if result else 'false'}\n")
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
