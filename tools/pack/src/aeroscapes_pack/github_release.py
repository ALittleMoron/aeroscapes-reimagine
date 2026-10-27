"""Idempotent GitHub release transitions; published assets are never overwritten."""

import hashlib
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from aeroscapes_pack.data import PackError
from aeroscapes_pack.release import archive_fingerprint, release_decision, version_key


@dataclass(frozen=True, slots=True)
class Release:
    id: int
    tag: str
    draft: bool
    prerelease: bool
    target: str = ""
    immutable: bool = False


class ReleaseStore(Protocol):
    def releases(self) -> tuple[Release, ...]: ...
    def tag_commit(self, tag: str, /) -> str | None: ...
    def create_tag(self, tag: str, commit: str, /) -> None: ...
    def create_draft(self, tag: str, body: str, prerelease: bool, commit: str, /) -> Release: ...
    def asset(self, release: Release, name: str, /) -> bytes | None: ...
    def upload(self, release: Release, name: str, content: bytes, /) -> None: ...
    def publish(self, release: Release) -> None: ...


def content_fingerprint(content: bytes) -> str:
    with tempfile.TemporaryDirectory(prefix="aeroscapes-release-") as directory:
        path = Path(directory) / "published.mrpack"
        path.write_bytes(content)
        return archive_fingerprint(path)


def checksum(name: str, content: bytes) -> bytes:
    return f"{hashlib.sha256(content).hexdigest()}  {name}\n".encode()


def publish_release(
    store: ReleaseStore, version: str, commit: str, artifact: Path, body: str
) -> str:
    version_key(version)
    tag = f"v{version}"
    releases = store.releases()
    matching = [release for release in releases if release.tag == tag]
    if len(matching) > 1:
        raise PackError(f"Duplicate releases for {tag}")
    current = matching[0] if matching else None
    published = tuple(
        release.tag[1:] for release in releases if not release.draft and release.tag.startswith("v")
    )
    for other in releases:
        if other.draft and other.tag != tag:
            raise PackError(f"Unfinished draft {other.tag}; resume its original run first")
    expected_prerelease = "-" in version
    if current and current.prerelease != expected_prerelease:
        raise PackError(f"Release type does not match version {version}")
    if current and not current.draft:
        if not current.immutable:
            raise PackError(f"Published release {tag} is not immutable")
        if store.tag_commit(tag) is None:
            raise PackError(f"Published release {tag} has no matching commit tag")
        original = store.asset(current, artifact.name)
        original_checksum = store.asset(current, artifact.name + ".sha256")
        if original is None or original_checksum != checksum(artifact.name, original):
            raise PackError(f"Published release {tag} is missing a valid artifact or checksum")
        matches = content_fingerprint(original) == archive_fingerprint(artifact)
        release_decision(version, published, matches)
        return "skipped"
    release_decision(version, published, None)
    tagged = store.tag_commit(tag)
    if tagged is not None and tagged != commit:
        raise PackError(f"Tag {tag} points to another commit; restore the original run")
    if current is None:
        if tagged is not None:
            raise PackError(f"Tag {tag} exists without a release; inspect it before continuing")
        current = store.create_draft(tag, body, expected_prerelease, commit)
    if current.target != commit:
        raise PackError(f"Draft {tag} targets a different commit")
    original = store.asset(current, artifact.name)
    if original is None:
        original = artifact.read_bytes()
        store.upload(current, artifact.name, original)
    elif content_fingerprint(original) != archive_fingerprint(artifact):
        raise PackError(f"Draft {tag} contains different content; resume the original run")
    name = artifact.name + ".sha256"
    original_checksum = checksum(artifact.name, original)
    existing_checksum = store.asset(current, name)
    if existing_checksum is None:
        store.upload(current, name, original_checksum)
    elif existing_checksum != original_checksum:
        raise PackError(f"Draft {tag} contains an invalid checksum; do not overwrite it")
    tagged = store.tag_commit(tag)
    if tagged is None:
        store.create_tag(tag, commit)
        tagged = store.tag_commit(tag)
    if tagged != commit:
        raise PackError(f"Tag {tag} does not point to the checked commit")
    store.publish(current)
    updated = [item for item in store.releases() if item.id == current.id]
    if (
        len(updated) != 1
        or updated[0].draft
        or not updated[0].immutable
        or updated[0].prerelease != expected_prerelease
    ):
        raise PackError(f"GitHub did not confirm publication of {tag}")
    return "published"
