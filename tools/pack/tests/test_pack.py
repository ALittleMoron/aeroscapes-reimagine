"""Behavioral contracts for locked sources, artifact metadata and launcher exports."""

import hashlib
import io
import json
import zipfile
from dataclasses import dataclass, replace
from pathlib import Path
from textwrap import dedent

import pytest

from aeroscapes_pack import cli
from aeroscapes_pack.artifacts import (
    JarRequirement,
    audit_jars,
    declared_mods,
    jar_mods,
    neoforge_range_matches,
)
from aeroscapes_pack.cli import main
from aeroscapes_pack.data import PackError, Table
from aeroscapes_pack.files import digest, download, new_target, safe_path
from aeroscapes_pack.models import Artifact, ArtifactRequirement, Project
from aeroscapes_pack.pack import (
    SourcePack,
    audit_metadata,
    check_compat,
    read_source,
    validate_release,
    validate_resourcepacks,
    verify_mrpack,
)
from aeroscapes_pack.smoke import (
    CaptureResult,
    CaptureSettings,
    eula_accepted,
    run,
    write_result,
)


@pytest.fixture
def artifact() -> Artifact:
    content = b"pinned mod fixture"
    return Artifact(
        project_id="fixture-project",
        version_id="fixture-version",
        filename="client.jar",
        sha512=hashlib.sha512(content).hexdigest(),
        sha1=hashlib.sha1(content, usedforsecurity=False).hexdigest(),
        size=len(content),
        side="client",
        kind="mod",
        release_type="release",
        loaders=("neoforge",),
        game_versions=("1.21.1",),
        requirements=(),
    )


def artifact_record(artifact: Artifact) -> Table:
    """Build the external metadata form rather than bypassing its parser in CLI tests."""
    return {
        "status": "included",
        "project_id": artifact.project_id,
        "version_id": artifact.version_id,
        "filename": artifact.filename,
        "sha512": artifact.sha512,
        "sha1": artifact.sha1,
        "file_size": artifact.size,
        "side": artifact.side,
        "kind": artifact.kind,
        "release_type": artifact.release_type,
        "loaders": list(artifact.loaders),
        "game_versions": list(artifact.game_versions),
        "dependencies": [],
    }


def write_lock(directory: Path) -> None:
    """Create a packwiz lock for the fixture's current files, without invoking packwiz."""
    entries = ['hash-format = "sha256"\n']
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.name in {"index.toml", "pack.toml"}:
            continue
        entries.append(
            dedent(
                f'''\
                [[files]]
                file = "{path.relative_to(directory).as_posix()}"
                hash = "{digest(path)}"
                metafile = {str(path.name.endswith(".pw.toml")).lower()}
                '''
            )
        )
    index = directory / "index.toml"
    index.write_text("\n".join(entries), encoding="utf-8")
    (directory / "pack.toml").write_text(
        dedent(
            f'''\
            name = "Fixture"
            version = "0.1"
            [index]
            file = "index.toml"
            hash-format = "sha256"
            hash = "{digest(index)}"
            [versions]
            minecraft = "1.21.1"
            neoforge = "21.1.230"
            '''
        ),
        encoding="utf-8",
    )


@pytest.fixture
def project(tmp_path: Path, artifact: Artifact) -> Project:
    project = Project(tmp_path)
    (project.pack / "mods").mkdir(parents=True)
    (project.root / "metadata").mkdir()
    (project.pack / "config.json").write_text('{"volume": 0.5}\n', encoding="utf-8")
    (project.pack / "mods/client.pw.toml").write_text(
        dedent(
            f'''\
            name = "Client fixture"
            filename = "{artifact.filename}"
            side = "{artifact.side}"
            [download]
            url = "https://cdn.modrinth.com/data/fixture/client.jar"
            hash-format = "sha512"
            hash = "{artifact.sha512}"
            [update.modrinth]
            mod-id = "{artifact.project_id}"
            version = "{artifact.version_id}"
            '''
        ),
        encoding="utf-8",
    )
    write_lock(project.pack)
    (project.root / "metadata/artifacts.json").write_text(
        json.dumps([artifact_record(artifact)]), encoding="utf-8"
    )
    (project.root / "metadata/compat_fixes.json").write_text("[]", encoding="utf-8")
    (project.root / "toolchain.json").write_text(
        json.dumps(
            {
                "java": {"major": 21},
                "packwiz": {"module": "github.com/packwiz/packwiz", "commit": "a" * 40},
                "bootstrap": {
                    "filename": "bootstrap.jar",
                    "url": "https://example.invalid/bootstrap.jar",
                    "sha256": "0" * 64,
                },
                "installer": {
                    "filename": "installer.jar",
                    "url": "https://example.invalid/installer.jar",
                    "sha256": "0" * 64,
                },
                "neoforge": {
                    "sha256": "0" * 64,
                    "filename": "neoforge-21.1.230-installer.jar",
                    "url": (
                        "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.230/"
                        "neoforge-21.1.230-installer.jar"
                    ),
                },
            }
        ),
        encoding="utf-8",
    )
    return project


@pytest.fixture
def source(project: Project) -> SourcePack:
    return read_source(project.pack)


@pytest.mark.parametrize("side", ["client", "both"])
def test_selected_resourcepack_resolves_to_a_client_download(source: SourcePack, side: str) -> None:
    dependency = replace(next(iter(source.dependencies.values())), filename="Leaves.zip", side=side)
    selected = replace(
        source,
        dependencies={"resourcepacks/Leaves.zip": dependency},
        overrides={"options.txt": b'resourcePacks:["vanilla","mod_resources","file/Leaves.zip"]\n'},
    )
    validate_resourcepacks(selected)


def test_selected_directory_resourcepack_resolves_to_its_pack_metadata(source: SourcePack) -> None:
    selected = replace(
        source,
        overrides={
            "options.txt": b'resourcePacks:["vanilla","file/Local"]\n',
            "resourcepacks/Local/pack.mcmeta": b'{"pack":{"pack_format":34}}',
        },
    )
    validate_resourcepacks(selected)


def test_missing_selected_resourcepack_fails_source_validation(project: Project) -> None:
    (project.pack / "options.txt").write_text(
        'resourcePacks:["vanilla","file/Old filename.zip"]\n', encoding="utf-8"
    )
    write_lock(project.pack)
    with pytest.raises(PackError, match="Selected resource pack is not installed on the client"):
        read_source(project.pack)


def test_selected_resourcepack_cannot_be_a_server_only_download(source: SourcePack) -> None:
    dependency = replace(
        next(iter(source.dependencies.values())), filename="Leaves.zip", side="server"
    )
    selected = replace(
        source,
        dependencies={"resourcepacks/Leaves.zip": dependency},
        overrides={"options.txt": b'resourcePacks:["file/Leaves.zip"]\n'},
    )
    with pytest.raises(PackError, match="Selected resource pack is not installed on the client"):
        validate_resourcepacks(selected)


@pytest.mark.parametrize("value", ['"file/Leaves.zip"', '["vanilla", 34]'])
def test_selected_resourcepack_list_validates_json_types(source: SourcePack, value: str) -> None:
    selected = replace(source, overrides={"options.txt": f"resourcePacks:{value}\n".encode()})
    with pytest.raises(PackError, match="expected"):
        validate_resourcepacks(selected)


@dataclass(frozen=True)
class ExportFixture:
    path: Path
    source: SourcePack
    artifact: Artifact

    def write(
        self,
        *,
        config: bytes | None = None,
        server_side: str = "unsupported",
        loader_version: str = "21.1.230",
    ) -> Path:
        manifest = {
            "formatVersion": 1,
            "game": "minecraft",
            "name": self.source.manifest.name,
            "versionId": self.source.manifest.version,
            "dependencies": {"minecraft": "1.21.1", "neoforge": loader_version},
            "files": [
                {
                    "path": "mods/client.jar",
                    "hashes": {"sha512": self.artifact.sha512, "sha1": self.artifact.sha1},
                    "fileSize": self.artifact.size,
                    "env": {"client": "required", "server": server_side},
                    "downloads": ["https://cdn.modrinth.com/data/fixture/client.jar"],
                }
            ],
        }
        with zipfile.ZipFile(self.path, "w") as archive:
            archive.writestr("modrinth.index.json", json.dumps(manifest))
            archive.writestr(
                "overrides/config.json",
                self.source.overrides["config.json"] if config is None else config,
            )
        return self.path


@pytest.fixture
def export(tmp_path: Path, source: SourcePack, artifact: Artifact) -> ExportFixture:
    return ExportFixture(tmp_path / "fixture.mrpack", source, artifact)


def make_jar(manifest: str, *, bundled: bytes | None = None) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("META-INF/neoforge.mods.toml", dedent(manifest))
        if bundled is not None:
            archive.writestr("META-INF/jarjar/library.jar", bundled)
            archive.writestr(
                "META-INF/jarjar/metadata.json",
                json.dumps({"jars": [{"path": "META-INF/jarjar/library.jar"}]}),
            )
    return buffer.getvalue()


def test_sha512_artifact_cache_is_reused_after_verification(tmp_path: Path) -> None:
    target = tmp_path / "mod.jar"
    target.write_bytes(b"verified mod")

    result = download("https://example.invalid/mod", target, digest(target, "sha512"), "sha512")

    assert result.read_bytes() == b"verified mod"


def test_existing_target_and_save_are_untouched(tmp_path: Path) -> None:
    world = tmp_path / "level.dat"
    world.write_bytes(b"existing save")

    with pytest.raises(PackError, match="already exists"):
        new_target(tmp_path)

    assert world.read_bytes() == b"existing save"


@pytest.mark.parametrize(
    "name",
    ["../save", "/save", "C:/save", "a/../../save", "a\\save", "", ".", "./save", "a//save"],
)
def test_paths_cannot_escape_or_rewrite_pack_destinations(tmp_path: Path, name: str) -> None:
    with pytest.raises(PackError, match="[Pp]ath"):
        safe_path(tmp_path, name)


def test_symlink_cannot_redirect_installation(tmp_path: Path) -> None:
    root = tmp_path / "pack"
    outside = tmp_path / "outside"
    root.mkdir()
    outside.mkdir()
    (root / "mods").symlink_to(outside, target_is_directory=True)

    with pytest.raises(PackError, match="escapes"):
        safe_path(root, "mods/file.jar")


def test_modified_cached_tool_is_never_used(tmp_path: Path) -> None:
    target = tmp_path / "installer.jar"
    target.write_bytes(b"corrupted")
    expected = hashlib.sha256(b"correct").hexdigest()

    with pytest.raises(PackError, match="Hash mismatch"):
        download("https://example.invalid/tool", target, expected)


def test_modified_config_is_detected_without_refreshing_it(project: Project) -> None:
    read_source(project.pack)
    (project.pack / "config.json").write_text("changed", encoding="utf-8")

    with pytest.raises(PackError, match="Stale file hash"):
        read_source(project.pack)


def test_unindexed_file_cannot_silently_disappear_from_release(project: Project) -> None:
    (project.pack / "new.txt").write_text("not indexed", encoding="utf-8")

    with pytest.raises(PackError, match="Unindexed"):
        read_source(project.pack)


@pytest.mark.parametrize("filename", ["client.jar", "./client.jar"])
def test_override_cannot_replace_a_pinned_dependency(project: Project, filename: str) -> None:
    (project.pack / "mods/client.jar").write_bytes(b"different override bytes")
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").replace(
            'filename = "client.jar"', f'filename = "{filename}"'
        ),
        encoding="utf-8",
    )
    write_lock(project.pack)

    with pytest.raises(PackError, match="Dependency and override destinations overlap"):
        read_source(project.pack)


@pytest.mark.parametrize(
    ("filename", "override_path"),
    [
        ("client.jar", "mods/client.jar/config.json"),
        ("client.jar/child.jar", "mods/client.jar"),
    ],
)
def test_dependency_and_override_cannot_require_file_and_directory(
    project: Project, filename: str, override_path: str
) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").replace(
            'filename = "client.jar"', f'filename = "{filename}"'
        ),
        encoding="utf-8",
    )
    override = project.pack / override_path
    override.parent.mkdir(parents=True, exist_ok=True)
    override.write_text("{}", encoding="utf-8")
    write_lock(project.pack)

    with pytest.raises(PackError, match="both a file and a directory"):
        read_source(project.pack)


def test_dependencies_cannot_require_file_and_directory(project: Project) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    (project.pack / "mods/nested.pw.toml").write_text(
        metadata.read_text(encoding="utf-8")
        .replace('filename = "client.jar"', 'filename = "client.jar/child.jar"')
        .replace('mod-id = "fixture-project"', 'mod-id = "nested-project"'),
        encoding="utf-8",
    )
    write_lock(project.pack)

    with pytest.raises(PackError, match="both a file and a directory"):
        read_source(project.pack)


def test_sibling_prefix_is_a_valid_installation_destination(project: Project) -> None:
    (project.pack / "mods/client.jar.notes").write_text("notes", encoding="utf-8")
    write_lock(project.pack)

    source = read_source(project.pack)

    assert "mods/client.jar" in source.dependencies
    assert "mods/client.jar.notes" in source.overrides


def test_modified_index_is_detected(project: Project) -> None:
    with (project.pack / "index.toml").open("a", encoding="utf-8") as stream:
        stream.write("\n# modified\n")

    with pytest.raises(PackError, match="Stale pack.toml"):
        read_source(project.pack)


def test_dependency_without_file_provenance_cannot_bypass_audit(project: Project) -> None:
    (project.pack / "unknown.pw.toml").write_text(
        dedent(
            f'''\
            name = "Unknown"
            filename = "unknown.jar"
            side = "both"
            [download]
            url = "https://example.invalid/unknown.jar"
            hash-format = "sha512"
            hash = "{"a" * 128}"
            [update.modrinth]
            version = "unknown-version"
            '''
        ),
        encoding="utf-8",
    )
    write_lock(project.pack)

    with pytest.raises(PackError, match="mod-id"):
        read_source(project.pack)


def test_maven_dependency_keeps_exact_identity_and_export(
    project: Project, artifact: Artifact, tmp_path: Path
) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").split("[update.modrinth]")[0]
        + '[source.maven]\ngroup = "dev.engine-room.flywheel"\n'
        + 'artifact = "flywheel-neoforge-1.21.1"\nversion = "1.0.6"\n',
        encoding="utf-8",
    )
    write_lock(project.pack)
    source = read_source(project.pack)
    maven_artifact = replace(
        artifact,
        project_id="maven:dev.engine-room.flywheel:flywheel-neoforge-1.21.1",
        version_id="1.0.6",
    )
    audit_metadata(source, (maven_artifact,))
    dependency = source.dependencies["mods/client.jar"]
    assert dependency.project_id == maven_artifact.project_id
    assert dependency.version_id == "1.0.6"
    exported = ExportFixture(tmp_path / "maven.mrpack", source, maven_artifact)
    verify_mrpack(exported.write(), source, (maven_artifact,))
    with pytest.raises(PackError, match="identity/hash/side mismatch"):
        audit_metadata(source, (replace(maven_artifact, version_id="1.0.4"),))


def test_curseforge_dependency_keeps_exact_file_identity(
    project: Project, artifact: Artifact
) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").split("[update.modrinth]")[0]
        + "[update.curseforge]\nproject-id = 1220929\nfile-id = 6711308\n",
        encoding="utf-8",
    )
    write_lock(project.pack)
    source = read_source(project.pack)
    selected = replace(artifact, project_id="curseforge:1220929", version_id="6711308")
    audit_metadata(source, (selected,))
    dependency = source.dependencies["mods/client.jar"]
    assert dependency.project_id == "curseforge:1220929"
    assert dependency.version_id == "6711308"
    with pytest.raises(PackError, match="identity/hash/side mismatch"):
        audit_metadata(source, (replace(selected, version_id="6711307"),))


@pytest.mark.parametrize("identifier", ["0", "-1", "true", '"6711308"', "1.5"])
@pytest.mark.parametrize("key", ["project-id", "file-id"])
def test_curseforge_ids_must_be_positive_integers(
    project: Project, key: str, identifier: str
) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    identifiers = {"project-id": "1220929", "file-id": "6711308", key: identifier}
    metadata.write_text(
        metadata.read_text(encoding="utf-8").split("[update.modrinth]")[0]
        + "[update.curseforge]\n"
        + "".join(f"{name} = {value}\n" for name, value in identifiers.items()),
        encoding="utf-8",
    )
    write_lock(project.pack)
    with pytest.raises(PackError, match="CurseForge"):
        read_source(project.pack)


@pytest.mark.parametrize(
    "provenance",
    [
        "",
        '[update.modrinth]\nmod-id = "fixture"\nversion = "exact"\n'
        '[source.maven]\ngroup = "dev.engine-room"\nartifact = "flywheel"\n'
        'version = "1.0.6"\n',
        '[update.modrinth]\nmod-id = "fixture"\nversion = "exact"\n'
        "[update.curseforge]\nproject-id = 1220929\nfile-id = 6711308\n",
    ],
)
def test_dependency_requires_one_source_identity(project: Project, provenance: str) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").split("[update.modrinth]")[0] + provenance,
        encoding="utf-8",
    )
    write_lock(project.pack)
    with pytest.raises(PackError, match="exactly one .* source identity"):
        read_source(project.pack)


@pytest.mark.parametrize(
    ("group", "artifact_name", "version"),
    [
        ("dev.engine-room:other", "flywheel", "1.0.6"),
        ("dev.engine-room", "../flywheel", "1.0.6"),
        ("dev.engine-room", "flywheel", "[1.0.6,)"),
    ],
)
def test_maven_coordinates_must_identify_one_artifact(
    project: Project, group: str, artifact_name: str, version: str
) -> None:
    metadata = project.pack / "mods/client.pw.toml"
    metadata.write_text(
        metadata.read_text(encoding="utf-8").split("[update.modrinth]")[0]
        + f'[source.maven]\ngroup = "{group}"\nartifact = "{artifact_name}"\n'
        + f'version = "{version}"\n',
        encoding="utf-8",
    )
    write_lock(project.pack)
    with pytest.raises(PackError, match="invalid Maven"):
        read_source(project.pack)


def test_omitted_type_is_required_even_with_legacy_mandatory_false() -> None:
    content = make_jar(
        """\
        [[mods]]
        modId = "owner"
        [[dependencies.owner]]
        modId = "needed"
        mandatory = false
        [[dependencies.owner]]
        modId = "optional"
        type = "optional"
        mandatory = true
        [[dependencies.not_a_declared_mod]]
        modId = "ignored_by_fml"
        """
    )

    _, requirements = jar_mods(content)

    assert requirements == [JarRequirement("owner", "needed", "BOTH")]


def test_upstream_update_requires_rechecking_compatibility_override(
    tmp_path: Path, artifact: Artifact
) -> None:
    (tmp_path / "pack.mcmeta").write_text("{}", encoding="utf-8")
    fix = {
        "targets": {artifact.project_id: "tested-version"},
        "files": ["pack.mcmeta"],
        "reason": "codec fix",
    }
    updated = replace(artifact, version_id="new-version")

    with pytest.raises(PackError, match="Re-evaluate compatibility fix"):
        check_compat(tmp_path, [fix], (updated,))


def test_binary_structure_override_requires_a_matching_version_binding(
    tmp_path: Path, artifact: Artifact
) -> None:
    template = "data/fixture/structure/house.nbt"
    path = tmp_path / template
    path.parent.mkdir(parents=True)
    path.write_bytes(b"\x0a\x00\x00\x00")
    fix = {
        "targets": {artifact.project_id: artifact.version_id},
        "files": [template],
        "reason": "Repair malformed template entity",
    }

    check_compat(tmp_path, [fix], (artifact,))

    with pytest.raises(PackError, match="Unbound compatibility data"):
        check_compat(tmp_path, [], (artifact,))
    with pytest.raises(PackError, match="Re-evaluate compatibility fix"):
        check_compat(tmp_path, [fix], (replace(artifact, version_id="new-version"),))


def test_exception_text_cannot_authorize_an_unrelated_beta(
    source: SourcePack, artifact: Artifact
) -> None:
    unapproved = replace(artifact, release_type="beta", exception="Copied approval text")

    with pytest.raises(PackError, match="Unapproved experimental dependency"):
        audit_metadata(source, (unapproved,))


@pytest.mark.parametrize(
    ("project_id", "version_id", "release_type"),
    [
        ("COlSi5iR", "avwWYPbG", "alpha"),
        ("qtPMklut", "7rX3dlFf", "alpha"),
        ("qyVF9oeo", "85fA7L1W", "beta"),
        ("xFCYuAs8", "Js4y2r4d", "beta"),
        ("u58R1TMW", "IITF0PRC", "beta"),
    ],
)
def test_selected_prerelease_exception_is_exact(
    artifact: Artifact, project_id: str, version_id: str, release_type: str
) -> None:
    approved = replace(
        artifact,
        project_id=project_id,
        version_id=version_id,
        release_type=release_type,
        exception="Owner selected this exact experimental file.",
    )
    validate_release(approved)
    for changed in (
        replace(approved, project_id="other-project"),
        replace(approved, version_id="other"),
        replace(approved, release_type="alpha" if release_type == "beta" else "beta"),
        replace(approved, exception=None),
    ):
        with pytest.raises(PackError, match="Unapproved experimental dependency"):
            validate_release(changed)


def test_actual_jar_dependencies_are_checked_on_the_installed_side(
    tmp_path: Path, artifact: Artifact, source: SourcePack
) -> None:
    path = tmp_path / artifact.filename
    path.write_bytes(
        make_jar(
            """\
            [[mods]]
            modId = "config_library"
            [[dependencies.config_library]]
            modId = "kotlinforforge"
            type = "REQUIRED"
            side = "BOTH"
            """
        )
    )
    actual = replace(artifact, sha512=digest(path, "sha512"))

    with pytest.raises(PackError, match="client.*kotlinforforge"):
        audit_jars(tmp_path, (actual,), {}, manifest=source.manifest)


def test_fabric_mod_requires_bridge_and_api_on_client(
    tmp_path: Path, artifact: Artifact, source: SourcePack
) -> None:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(
            "fabric.mod.json",
            json.dumps(
                {
                    "id": "smoke",
                    "environment": "client",
                    "depends": {
                        "fabricloader": ">=0.18.4",
                        "fabric-api": "*",
                        "minecraft": ">=1.21",
                    },
                }
            ),
        )
    path = tmp_path / artifact.filename
    path.write_bytes(buffer.getvalue())
    fabric = replace(artifact, sha512=digest(path, "sha512"), loaders=("fabric",))
    with pytest.raises(PackError, match="client.*fabricloader"):
        audit_jars(tmp_path, (fabric,), {}, manifest=source.manifest)
    bridge = make_jar('[[mods]]\nmodId = "connector"\n')
    api = make_jar('[[mods]]\nmodId = "fabric_api"\nprovides = ["fabric-api"]\n')
    audit_jars(
        tmp_path,
        (fabric,),
        {"mods/bridge.jar": bridge, "mods/api.jar": api},
        manifest=source.manifest,
    )


def test_fabric_metadata_cannot_bypass_client_bridge(
    source: SourcePack, artifact: Artifact
) -> None:
    with pytest.raises(PackError, match="without its client bridge"):
        audit_metadata(source, (replace(artifact, loaders=("fabric",)),))


def test_bundled_library_satisfies_dependency(
    tmp_path: Path, artifact: Artifact, source: SourcePack
) -> None:
    nested = make_jar('[[mods]]\nmodId = "library"\n')
    outer = make_jar(
        """\
        [[mods]]
        modId = "parent"
        [[dependencies.parent]]
        modId = "library"
        type = "required"
        side = "BOTH"
        """,
        bundled=nested,
    )
    path = tmp_path / artifact.filename
    path.write_bytes(outer)
    actual = replace(artifact, sha512=digest(path, "sha512"), side="both")

    identifiers, _ = jar_mods(outer)

    assert identifiers == {"parent", "library"}
    assert audit_jars(tmp_path, (actual,), {}, manifest=source.manifest) == {
        "client": 2,
        "server": 2,
    }


@pytest.mark.parametrize(("minimum", "accepted"), [("21.1.262", False), ("21.1.219", True)])
def test_nested_loader_requirement_cannot_hide_behind_compatible_parent(
    tmp_path: Path, artifact: Artifact, source: SourcePack, minimum: str, accepted: bool
) -> None:
    nested = make_jar(
        '[[mods]]\nmodId = "nested_api"\n'
        '[[dependencies.nested_api]]\nmodId = "neoforge"\n'
        f'versionRange = "[{minimum},)"\n'
    )
    outer = make_jar(
        '[[mods]]\nmodId = "parent"\n'
        '[[dependencies.parent]]\nmodId = "neoforge"\n'
        'versionRange = "[21.1.169,)"\n',
        bundled=nested,
    )
    path = tmp_path / artifact.filename
    path.write_bytes(outer)
    actual = replace(artifact, sha512=digest(path, "sha512"))

    if accepted:
        assert audit_jars(tmp_path, (actual,), {}, manifest=source.manifest) == {
            "client": 2,
            "server": 0,
        }
    else:
        with pytest.raises(PackError, match=r"nested_api.*21\.1\.262.*21\.1\.230"):
            audit_jars(tmp_path, (actual,), {}, manifest=source.manifest)


@pytest.mark.parametrize(
    ("expression", "accepted"),
    [
        (None, True),
        ("", False),
        (" ", True),
        ("*", True),
        ("21.1.262", True),
        ("[21.1.219,)", True),
        ("[21.1.262,)", False),
        ("[21.1.230]", True),
        ("[21.1.230.0]", True),
        ("(21.1.230,)", False),
        ("(,21.1.230]", True),
        ("(,21.1.230)", False),
        ("[21.1.230-beta,21.1.230]", True),
        ("[21.0.110-beta, 21.2.0)", True),
        ("[21.0.143,21.1),[21.1.1,21.2),", True),
        ("[21,21.1),[21.1.250,)", False),
    ],
)
def test_neoforge_ranges_follow_pinned_maven_semantics(
    expression: str | None, accepted: bool
) -> None:
    assert neoforge_range_matches(expression, ("21.1.230",)) is accepted


@pytest.mark.parametrize(
    "expression",
    [
        "[21.1.230-rc1,)",
        "[21.1.231,21.1.230]",
        "[21.1.230,21.1.230)",
        "[21,22],[21.1,23]",
        "[21,22)garbage",
        "[21,22",
    ],
)
def test_unsupported_or_invalid_neoforge_range_is_not_silently_accepted(expression: str) -> None:
    with pytest.raises(PackError, match="NeoForge"):
        neoforge_range_matches(expression, ("21.1.230",))


def test_neoforge_range_requires_the_actual_installed_version() -> None:
    with pytest.raises(PackError, match="installed version"):
        neoforge_range_matches("[21.1.219,)", ())


def test_neoforge_range_respects_the_installed_side(
    tmp_path: Path, artifact: Artifact, source: SourcePack
) -> None:
    template = (
        '[[mods]]\nmodId = "owner"\n'
        '[[dependencies.owner]]\nmodId = "neoforge"\n'
        'versionRange = "[21.1.262,)"\nside = "CLIENT"\n'
    )
    path = tmp_path / artifact.filename
    path.write_bytes(make_jar(template.replace('side = "CLIENT"', 'side = "SERVER"')))
    selected = replace(artifact, sha512=digest(path, "sha512"), side="client")
    assert audit_jars(tmp_path, (selected,), {}, manifest=source.manifest) == {
        "client": 1,
        "server": 0,
    }
    path.write_bytes(make_jar(template))
    selected = replace(selected, sha512=digest(path, "sha512"))
    with pytest.raises(PackError, match="on client"):
        audit_jars(tmp_path, (selected,), {}, manifest=source.manifest)


def test_pinned_fml_neoforge_compatibility_fallback_is_preserved(source: SourcePack) -> None:
    compatible = make_jar(
        '[[mods]]\nmodId = "owner"\n'
        '[[dependencies.owner]]\nmodId = "neoforge"\n'
        'versionRange = "[21.0.166]"\n'
    )
    assert audit_jars(Path(), (), {"mods/owner.jar": compatible}, manifest=source.manifest) == {
        "client": 1,
        "server": 1,
    }


def test_build_rejects_incompatible_owned_bundle_before_export(
    project: Project, source: SourcePack, monkeypatch: pytest.MonkeyPatch
) -> None:
    nested = make_jar(
        '[[mods]]\nmodId = "nested_api"\n'
        '[[dependencies.nested_api]]\nmodId = "neoforge"\n'
        'versionRange = "[21.1.262,)"\n'
    )
    selected = replace(
        source,
        dependencies={},
        overrides={"mods/parent.jar": make_jar('[[mods]]\nmodId = "parent"\n', bundled=nested)},
    )
    (project.root / "metadata/artifacts.json").write_text("[]", encoding="utf-8")

    def unexpected_export(_project: Project, *arguments: str) -> str:
        raise AssertionError(f"Exporter ran before checking dependencies: {arguments}")

    monkeypatch.setattr(cli, "run_packwiz", unexpected_export)

    with pytest.raises(PackError, match=r"nested_api.*21\.1\.262.*21\.1\.230"):
        cli.build(project, selected)


def test_owned_mod_dependency_is_audited_with_remote_mods(
    tmp_path: Path, source: SourcePack
) -> None:
    companion = make_jar(
        '[[mods]]\nmodId = "companion"\n'
        '[[dependencies.companion]]\nmodId = "generator"\n'
        'type = "required"\nside = "BOTH"\n'
    )
    overrides = {"mods/companion.jar": companion}

    with pytest.raises(PackError, match="companion.*generator"):
        audit_jars(tmp_path, (), overrides, manifest=source.manifest)

    overrides["mods/generator.jar"] = make_jar('[[mods]]\nmodId = "generator"\n')
    assert audit_jars(tmp_path, (), overrides, manifest=source.manifest) == {
        "client": 2,
        "server": 2,
    }


def test_missing_bundled_jar_reports_the_referenced_entry() -> None:
    missing_path = "META-INF/jarjar/missing.jar"
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(
            "META-INF/jarjar/metadata.json", json.dumps({"jars": [{"path": missing_path}]})
        )

    with pytest.raises(PackError, match="Invalid or missing JAR entry") as failure:
        jar_mods(buffer.getvalue())

    assert missing_path in str(failure.value)


def test_invalid_dependency_side_is_rejected() -> None:
    manifest: Table = {
        "mods": [{"modId": "owner"}],
        "dependencies": {"owner": [{"modId": "needed", "type": "required", "side": "BTOH"}]},
    }

    with pytest.raises(PackError, match="Invalid dependency side for owner: BTOH"):
        declared_mods(manifest)


def test_version_only_requirement_must_resolve_to_an_included_artifact(
    source: SourcePack, artifact: Artifact
) -> None:
    owner = replace(
        artifact,
        requirements=(ArtifactRequirement(project_id=None, version_id="missing-version"),),
    )

    with pytest.raises(PackError, match="Missing required version.*missing-version"):
        audit_metadata(source, (owner,))


def test_version_only_requirement_resolves_to_an_included_artifact(
    source: SourcePack, artifact: Artifact
) -> None:
    library = replace(
        artifact, project_id="library-project", version_id="library-version", filename="library.jar"
    )
    owner = replace(
        artifact,
        requirements=(ArtifactRequirement(project_id=None, version_id=library.version_id),),
    )
    pinned = next(iter(source.dependencies.values()))
    library_dependency = replace(
        pinned,
        name="Library fixture",
        filename=library.filename,
        project_id=library.project_id,
        version_id=library.version_id,
    )
    dependencies = {**source.dependencies, "mods/library.jar": library_dependency}
    source_with_library = replace(source, dependencies=dependencies)

    audit_metadata(source_with_library, (owner, library))


def test_compatibility_override_requires_an_exact_target(
    tmp_path: Path, artifact: Artifact
) -> None:
    (tmp_path / "pack.mcmeta").write_text("{}", encoding="utf-8")
    unbound_fix = {"targets": {}, "files": ["pack.mcmeta"], "reason": "unbound fix"}

    with pytest.raises(PackError, match="must bind at least one exact target version"):
        check_compat(tmp_path, [unbound_fix], (artifact,))


def test_rejected_launch_does_not_leave_previous_result(project: Project) -> None:
    target = project.runtime / "test"
    target.mkdir(parents=True)
    previous = target / "smoke-result.json"
    previous.write_text('{"ready": true}', encoding="utf-8")

    with pytest.raises(PackError, match="EULA"):
        run(project, target, None, [], CaptureSettings(timeout_seconds=1))

    assert not previous.exists()


def test_missing_eula_is_not_acceptance(tmp_path: Path) -> None:
    assert not eula_accepted(tmp_path / "eula.txt")


@pytest.mark.parametrize("contents", ["# eula=true\n", "eula=true\neula=false\n"])
def test_commented_or_overridden_acceptance_does_not_launch(tmp_path: Path, contents: str) -> None:
    path = tmp_path / "eula.txt"
    path.write_text(contents, encoding="utf-8")

    assert not eula_accepted(path)


def test_active_acceptance_is_recognized(tmp_path: Path) -> None:
    path = tmp_path / "eula.txt"
    path.write_text("# URL and owner comment\neula = true\n", encoding="utf-8")

    assert eula_accepted(path)


@pytest.mark.parametrize("error", ["", "[main/ERROR] Sable client class on dedicated server"])
def test_saved_world_still_fails_if_errors_were_logged(project: Project, error: str) -> None:
    (project.pack / "global_packs/required_data").mkdir(parents=True)
    output = f"All dimensions are saved\n{error}\n"
    capture = CaptureResult(ready=True, exit_code=0, output=output)

    assert write_result(project, project.root, capture) is (not error)
    report = json.loads((project.root / "smoke-result.json").read_text(encoding="utf-8"))
    assert report["errors"] == ([error] if error else [])


def test_matching_export_is_accepted(export: ExportFixture) -> None:
    verify_mrpack(export.write(), export.source, (export.artifact,))


def test_failed_build_preserves_previous_archive_and_checksum(
    project: Project,
    export: ExportFixture,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output = project.root / "dist/aeroscapes-reimagine-0.1.mrpack"
    output.parent.mkdir()
    previous_archive = export.write().read_bytes()
    output.write_bytes(previous_archive)
    checksum_path = output.with_suffix(".mrpack.sha256")
    previous_checksum = f"{digest(output)}  {output.name}\n".encode()
    checksum_path.write_bytes(previous_checksum)

    def export_invalid_archive(_project: Project, *arguments: str) -> str:
        candidate = Path(arguments[arguments.index("--output") + 1])
        candidate.write_bytes(b"invalid ZIP from a failed exporter")
        return "export finished\n"

    monkeypatch.setattr(cli, "run_packwiz", export_invalid_archive)

    (project.root / "metadata/artifacts.json").write_text("[]", encoding="utf-8")
    selected = replace(export.source, dependencies={})
    with pytest.raises(zipfile.BadZipFile):
        cli.build(project, selected)

    assert output.read_bytes() == previous_archive
    assert checksum_path.read_bytes() == previous_checksum


def test_export_cannot_install_client_mod_on_server(export: ExportFixture) -> None:
    with pytest.raises(PackError, match="sides differ"):
        verify_mrpack(export.write(server_side="required"), export.source, (export.artifact,))


def test_exported_defaults_must_match_source(export: ExportFixture) -> None:
    with pytest.raises(PackError, match="override differs"):
        verify_mrpack(export.write(config=b'{"volume": 1}'), export.source, (export.artifact,))


def test_exported_loader_must_match_the_source(export: ExportFixture) -> None:
    with pytest.raises(PackError, match="platform differs"):
        verify_mrpack(export.write(loader_version="different"), export.source, (export.artifact,))


@pytest.mark.parametrize(
    ("contents", "message"),
    [("[{", "invalid JSON"), ("{}", "expected an array"), ("[null]", "expected a table/object")],
)
def test_malformed_metadata_reports_an_actionable_cli_error(
    project: Project,
    capsys: pytest.CaptureFixture[str],
    contents: str,
    message: str,
) -> None:
    (project.root / "metadata/artifacts.json").write_text(contents, encoding="utf-8")

    assert main(["--root", str(project.root), "check"]) == 1

    output = capsys.readouterr()
    assert message in output.err
    assert "artifact" in output.err
    assert "Traceback" not in output.err


def test_boolean_is_not_an_artifact_file_size(
    project: Project, artifact: Artifact, capsys: pytest.CaptureFixture[str]
) -> None:
    record = artifact_record(artifact)
    record["file_size"] = True
    (project.root / "metadata/artifacts.json").write_text(json.dumps([record]), encoding="utf-8")

    assert main(["--root", str(project.root), "check"]) == 1

    output = capsys.readouterr()
    assert "file_size: expected a nonnegative integer" in output.err
    assert "Traceback" not in output.err


def test_cli_discovers_project_from_a_subdirectory(
    project: Project, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.chdir(project.pack / "mods")

    assert main(["check"]) == 0

    output = capsys.readouterr()
    assert "1 exact artifacts and dependency closure" in output.out
    assert output.err == ""


def test_cli_requires_provenance_for_native_template_overrides(
    project: Project, artifact: Artifact, capsys: pytest.CaptureFixture[str]
) -> None:
    relative = "data/nova_structures/structure/lone_citadel/frame/example.nbt"
    template = project.pack / "global_packs/required_data/aeroscapes_reimagine_compat" / relative
    template.parent.mkdir(parents=True)
    template.write_bytes(b"staged native template")
    (project.root / "metadata/compat_fixes.json").write_text(
        json.dumps([{"targets": {artifact.project_id: artifact.version_id}, "files": [relative]}]),
        encoding="utf-8",
    )
    write_lock(project.pack)

    assert main(["--root", str(project.root), "check"]) == 1

    output = capsys.readouterr()
    assert "native_template_upgrades.json" in output.err
    assert "Traceback" not in output.err


@pytest.mark.parametrize("distribution", ["unknown", 42, ["bundle"]])
def test_invalid_artifact_distribution_is_rejected(
    artifact: Artifact, distribution: object
) -> None:
    record = artifact_record(artifact)
    record["distribution"] = distribution
    with pytest.raises(PackError, match="invalid export distribution"):
        Artifact.parse(record)


def test_external_download_requires_explicit_bundling(
    source: SourcePack, artifact: Artifact
) -> None:
    dependency = replace(
        source.dependencies["mods/client.jar"], url="https://maven.example.org/client.jar"
    )
    source = replace(source, dependencies={"mods/client.jar": dependency})
    with pytest.raises(PackError, match="export distribution"):
        audit_metadata(source, (artifact,))
    audit_metadata(source, (replace(artifact, distribution="bundle"),))


@pytest.mark.parametrize("mutation", [None, "hash", "size", "side", "download", "sha1"])
def test_bundled_client_dependency_preserves_bytes_and_installation_side(
    tmp_path: Path, source: SourcePack, artifact: Artifact, mutation: str | None
) -> None:
    bundled = replace(artifact, distribution="bundle")
    if mutation == "sha1":
        bundled = replace(bundled, sha1="f" * 40)
    manifest = {
        "formatVersion": 1,
        "game": "minecraft",
        "name": source.manifest.name,
        "versionId": source.manifest.version,
        "dependencies": {"minecraft": "1.21.1", "neoforge": "21.1.230"},
        "files": [],
    }
    if mutation == "download":
        old = ExportFixture(tmp_path / "old.mrpack", source, artifact)
        with zipfile.ZipFile(old.write()) as archive:
            manifest = json.loads(archive.read("modrinth.index.json"))
    content = b"pinned mod fixture"
    if mutation == "hash":
        content = b"pinned mod fixturE"
    elif mutation == "size":
        content += b"extra"
    name = "client-overrides/mods/client.jar"
    if mutation == "side":
        name = "overrides/mods/client.jar"
    output = tmp_path / "bundled.mrpack"
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("modrinth.index.json", json.dumps(manifest))
        archive.writestr("overrides/config.json", source.overrides["config.json"])
        archive.writestr(name, content)
    if mutation is None:
        verify_mrpack(output, source, (bundled,))
    else:
        with pytest.raises(PackError):
            verify_mrpack(output, source, (bundled,))
