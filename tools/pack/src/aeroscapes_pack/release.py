"""Fingerprint exported pack content independently of ZIP metadata."""

import hashlib
import json
import zipfile
from pathlib import Path

from aeroscapes_pack.data import PackError, array, field, json_value, table


def archive_fingerprint(path: Path) -> str:
    with zipfile.ZipFile(path) as archive:
        entries = [entry for entry in archive.infolist() if not entry.is_dir()]
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise PackError("Archive contains duplicate paths")
        if "modrinth.index.json" not in names:
            raise PackError("Archive is missing modrinth.index.json")
        manifest = table(
            json_value(archive.read("modrinth.index.json"), "archive manifest"), "archive manifest"
        )
        files = [
            table(item, "archive manifest file")
            for item in array(manifest.get("files"), "archive manifest files")
        ]
        paths = [field(item, "path", "archive manifest file") for item in files]
        if len(paths) != len(set(paths)):
            raise PackError("Archive manifest has duplicate paths")
        manifest["files"] = sorted(
            files, key=lambda item: field(item, "path", "archive manifest file")
        )
        hasher = hashlib.sha256()
        for name in sorted(names):
            contents = (
                json.dumps(
                    manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=False
                ).encode("utf-8")
                if name == "modrinth.index.json"
                else archive.read(name)
            )
            encoded_name = name.encode("utf-8")
            hasher.update(len(encoded_name).to_bytes(8, "big"))
            hasher.update(encoded_name)
            hasher.update(len(contents).to_bytes(8, "big"))
            hasher.update(contents)
        return hasher.hexdigest()


def version_key(value: str) -> tuple[int, int, int, tuple[tuple[int, int, str], ...]]:
    """Return SemVer precedence; build metadata is excluded to avoid ambiguous releases."""
    import re

    match = re.fullmatch(
        r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?",
        value,
    )
    if match is None:
        raise PackError(f"Invalid release version: {value}")
    identifiers: list[tuple[int, int, str]] = []
    if match[4]:
        for part in match[4].split("."):
            if part.isdigit():
                if len(part) > 1 and part.startswith("0"):
                    raise PackError(f"Invalid release version: {value}")
                identifiers.append((0, int(part), ""))
            else:
                identifiers.append((1, 0, part))
    else:
        identifiers.append((2, 0, ""))
    return int(match[1]), int(match[2]), int(match[3]), tuple(identifiers)


def changelog_section(source: str, version: str) -> str:
    """Extract the version's player-facing content, retaining subsection headings."""
    import re

    version_key(version)
    base = version.split("-", 1)[0]
    headings = list(re.finditer(r"(?m)^## ([^\n]+)\s*$", source))
    matches = [index for index, heading in enumerate(headings) if heading[1].split()[0] == base]
    if len(matches) != 1:
        raise PackError(f"Expected exactly one changelog section for {base}")
    index = matches[0]
    end = headings[index + 1].start() if index + 1 < len(headings) else len(source)
    contents = source[headings[index].end() : end].strip()
    if not contents:
        raise PackError(f"Empty changelog section for {base}")
    return contents


def release_decision(version: str, published: tuple[str, ...], matches: bool | None) -> str:
    """Require a strictly newer version or unchanged content for an existing release."""
    key = version_key(version)
    if version in published:
        if matches is not True:
            raise PackError(f"Content of published {version} changed; increase the pack version")
        return "skip"
    if matches is not None:
        raise PackError(f"Unexpected existing content for unpublished version {version}")
    if published and key <= max(version_key(item) for item in published):
        raise PackError(f"New version {version} must be higher than every published version")
    return "create"


def changelog_between(source: str, version: str, previous: str | None) -> str:
    """Include each version section since the preceding release, including the target."""
    import re

    target = version_key(version.split("-", 1)[0])
    lower = version_key(previous.split("-", 1)[0]) if previous is not None else None
    same_base = lower == target
    headings = re.findall(r"(?m)^## ([^\n]+)", source)
    versions: list[str] = []
    for heading in headings:
        candidate = heading.split()[0]
        try:
            key = version_key(candidate)
        except PackError:
            continue
        if key <= target and (lower is None or key > lower or (same_base and key == lower)):
            versions.append(candidate)
    base = version.split("-", 1)[0]
    if base not in versions:
        raise PackError(f"Missing changelog section for {base}")
    return "\n\n".join(
        f"### {candidate}\n\n{changelog_section(source, candidate)}" for candidate in versions
    )
