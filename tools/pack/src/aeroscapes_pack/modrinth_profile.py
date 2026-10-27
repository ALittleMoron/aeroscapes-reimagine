"""Compile and reconcile the optional Modrinth project page, independently of versions."""

import argparse
import hashlib
import json
import re
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from aeroscapes_pack.data import PackError, field, read_toml, table
from aeroscapes_pack.errors import EXPECTED_ERRORS, error_message
from aeroscapes_pack.files import new_target, require_https, safe_path
from aeroscapes_pack.models import Manifest, Project, Toolchain

if TYPE_CHECKING:
    from aeroscapes_pack.modrinth_release import Modrinth


@dataclass(frozen=True, slots=True)
class Profile:
    title: str
    summary: str
    body: str
    wiki_url: str
    icon: bytes

    def payload(self) -> dict[str, object]:
        return {
            "title": self.title,
            "description": self.summary,
            "body": self.body,
            "wiki_url": self.wiki_url,
        }


def profile_text(record: dict[str, object], key: str, context: str, maximum: int) -> str:
    value = field(record, key, context)
    if not value.strip() or len(value) > maximum or "\x00" in value:
        raise PackError(f"{context}.{key}: expected nonblank text of at most {maximum} characters")
    return value


def profile_url(record: dict[str, object], key: str) -> str:
    value = profile_text(record, key, "Modrinth profile", 2048)
    require_https(value)
    if any(character.isspace() for character in value) or any(
        character in value for character in '<>"()'
    ):
        raise PackError(f"Modrinth profile.{key}: expected a plain HTTPS URL")
    return value


def render_body(template: str, replacements: dict[str, str]) -> str:
    for key, value in replacements.items():
        template = template.replace("{" + key + "}", value)
    if "{" in template or "}" in template:
        raise PackError(
            "Modrinth profile body: only {minecraft}, {neoforge}, {java}, {docs} are allowed"
        )
    return template.strip()


def validate_icon(content: bytes) -> None:
    """Check PNG structure, checksums and square dimensions without transforming its bytes."""
    if len(content) > 256 * 1024:
        raise PackError("Modrinth profile icon exceeds the documented 256KiB maximum")
    if not content.startswith(b"\x89PNG\r\n\x1a\n"):
        raise PackError("Modrinth profile icon must be a PNG")
    offset = 8
    chunks: list[bytes] = []
    while offset < len(content):
        if len(content) - offset < 12:
            raise PackError("Modrinth profile icon has a truncated PNG chunk")
        length = int.from_bytes(content[offset : offset + 4])
        chunk_type = content[offset + 4 : offset + 8]
        end = offset + 12 + length
        if end > len(content):
            raise PackError("Modrinth profile icon has a truncated PNG chunk")
        data = content[offset + 8 : end - 4]
        if zlib.crc32(chunk_type + data) != int.from_bytes(content[end - 4 : end]):
            raise PackError("Modrinth profile icon has an invalid PNG checksum")
        if not chunks:
            if chunk_type != b"IHDR" or length != 13:
                raise PackError("Modrinth profile icon has an invalid PNG header")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", data
            )
            valid_depths = {
                0: {1, 2, 4, 8, 16},
                2: {8, 16},
                3: {1, 2, 4, 8},
                4: {8, 16},
                6: {8, 16},
            }
            if (
                not 0 < width == height < 2**31
                or depth not in valid_depths.get(color, set())
                or compression != 0
                or filtering != 0
                or interlace not in {0, 1}
            ):
                raise PackError("Modrinth profile icon must be a square PNG with a valid header")
        elif chunk_type == b"IHDR":
            raise PackError("Modrinth profile icon has duplicate PNG headers")
        chunks.append(chunk_type)
        offset = end
        if chunk_type == b"IEND":
            if length != 0 or offset != len(content) or b"IDAT" not in chunks:
                raise PackError("Modrinth profile icon has an invalid PNG ending")
            return
    raise PackError("Modrinth profile icon is missing the PNG ending")


def load_profile(project: Project) -> Profile:
    source = safe_path(project.root, "tools/pack/publishing/modrinth/profile.toml")
    record = read_toml(source)
    expected = {
        "schema_version",
        "title",
        "icon_file",
        "icon_sha256",
        "icon_origin",
        "icon_author",
        "icon_title",
        "icon_source_url",
        "icon_license_url",
        "wiki_url",
        "en",
    }
    if (
        set(record) != expected
        or type(record.get("schema_version")) is not int
        or record.get("schema_version") != 1
    ):
        raise PackError("Modrinth profile must use the exact schema_version=1 fields")
    if record.get("icon_origin") != "human-authored-unmodified":
        raise PackError(
            "Modrinth profile requires an unmodified human-authored icon; AI images are prohibited"
        )
    icon_path = safe_path(source.parent, field(record, "icon_file", "Modrinth profile"))
    if icon_path.name != "icon.png" or icon_path.parent != source.parent:
        raise PackError("Modrinth profile icon_file must be icon.png beside profile.toml")
    expected_hash = field(record, "icon_sha256", "Modrinth profile")
    if not re.fullmatch(r"[a-f0-9]{64}", expected_hash):
        raise PackError("Modrinth profile.icon_sha256 must be a lowercase SHA-256")
    with icon_path.open("rb") as stream:
        icon = stream.read(256 * 1024 + 1)
    validate_icon(icon)
    if hashlib.sha256(icon).hexdigest() != expected_hash:
        raise PackError(
            "Modrinth profile icon SHA-256 differs from its reviewed human-authored source"
        )
    title = profile_text(record, "title", "Modrinth profile", 64)
    if len(title) < 3 or "\n" in title or "\r" in title:
        raise PackError("Modrinth profile.title must be 3–64 characters on one line")
    wiki_url = profile_url(record, "wiki_url").rstrip("/") + "/"
    manifest = Manifest.parse(read_toml(project.pack / "pack.toml"))
    replacements = {
        "minecraft": manifest.minecraft,
        "neoforge": manifest.neoforge,
        "java": str(Toolchain.load(project).java_major),
        "docs": wiki_url,
    }
    content = table(record.get("en"), "Modrinth profile.en")
    if set(content) != {"summary", "body"}:
        raise PackError("Modrinth profile.en must contain only summary and body")
    summary = profile_text(content, "summary", "Modrinth profile.en", 256)
    if len(summary) < 3 or "\n" in summary or "\r" in summary:
        raise PackError("Modrinth profile.en.summary must be 3–256 characters on one line")
    description = render_body(
        profile_text(content, "body", "Modrinth profile.en", 65536), replacements
    )
    author = profile_text(record, "icon_author", "Modrinth profile", 255)
    icon_title = profile_text(record, "icon_title", "Modrinth profile", 255)
    author = re.sub(r"([\\\[\]*_`<>])", r"\\\1", author)
    icon_title = re.sub(r"([\\\[\]*_`<>])", r"\\\1", icon_title)
    attribution = (
        f"[{icon_title}]({profile_url(record, 'icon_source_url')}) — {author}; "
        f"[license]({profile_url(record, 'icon_license_url')})."
    )
    body = f"{description}\n\nIcon: {attribution}"
    if len(body) > 65536:
        raise PackError("Rendered Modrinth profile body exceeds 65536 characters")
    return Profile(
        title=title,
        summary=summary,
        body=body,
        wiki_url=wiki_url,
        icon=icon,
    )


def sync_profile(project: Project, modrinth: Modrinth, *, disclosures_confirmed: bool) -> str:
    if not disclosures_confirmed:
        raise PackError(
            "Confirm the required AI text/publishing and other applicable disclosures manually "
            "in Modrinth's project settings; stable API v2 cannot read or update disclosures"
        )
    profile = load_profile(project)
    current = modrinth.project()
    project_id = field(current, "id", "Modrinth project")
    if not re.fullmatch(r"[A-Za-z0-9]+", project_id):
        raise PackError("Invalid canonical Modrinth project ID")
    payload = profile.payload()
    changed = {key: value for key, value in payload.items() if current.get(key) != value}
    operations: list[str] = []
    if changed:
        modrinth.request(
            f"/project/{project_id}",
            method="PATCH",
            data=json.dumps(changed, ensure_ascii=False).encode(),
            content_type="application/json",
        )
        current = modrinth.project()
        if any(current.get(key) != value for key, value in payload.items()):
            raise PackError("Modrinth did not confirm the updated project profile")
        operations.append("page updated")
    icon_url = current.get("raw_icon_url")
    if icon_url is not None and not isinstance(icon_url, str):
        raise PackError("Invalid Modrinth raw icon URL")
    if icon_url is None or modrinth.read_icon(icon_url) != profile.icon:
        modrinth.request(
            f"/project/{project_id}/icon?ext=png",
            method="PATCH",
            data=profile.icon,
            content_type="image/png",
        )
        current = modrinth.project()
        icon_url = field(current, "raw_icon_url", "Modrinth project")
        if modrinth.read_icon(icon_url) != profile.icon:
            raise PackError("Modrinth did not confirm the uploaded project icon")
        operations.append("icon updated")
    return "Modrinth profile: " + (", ".join(operations) if operations else "unchanged")


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Write a local preview in a new directory")
    options = parser.parse_args(arguments)
    try:
        profile = load_profile(Project.discover(Path.cwd()))
        if options.output is not None:
            output = new_target(options.output)
            (output / "payload.json").write_text(
                json.dumps(profile.payload(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            (output / "description.en.md").write_text(profile.body + "\n", encoding="utf-8")
            (output / "icon.png").write_bytes(profile.icon)
            print(f"Modrinth profile preview: {output}")
        else:
            print(f"Modrinth profile valid: {profile.title}; {len(profile.icon)} icon bytes")
    except EXPECTED_ERRORS as error:
        print(error_message(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
