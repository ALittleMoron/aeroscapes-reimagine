# Aeroscapes pack tools

Private Python 3.14 package for building and validating the Aeroscapes Reimagine
modpack. It provides `aeroscapes-pack` and the bounded technical-server checker
`aeroscapes-smoke`; it is not installed into Minecraft.

From the repository root, use `make sync verify`. For direct CLI calls from that
root, use `uv run --locked --project tools/pack aeroscapes-pack check`.

This directory owns its `pyproject.toml`, `uv.lock`, `.python-version`, `src/`
and `tests/`. The CLI discovers the modpack repository by walking up from the
working directory; outside the checkout, pass `--root /path/to/repository`.

Development commands and verification scope are documented in
[development guide](../../CONTRIBUTING.md).

Dependency metafiles use `[update.modrinth]`, `[update.curseforge]` with positive
integer `project-id` and `file-id`, or, for a pinned official Maven download,
`[source.maven]` with `group`, `artifact` and exact `version`.
The CurseForge metadata identity is `curseforge:<project-id>` with the file ID
as its exact version identity.
The Maven form has no automatic updater. Its metadata identity is
`maven:<group>:<artifact>`; download hashes, installation sides and exported
files are checked by the same artifact audit as Modrinth dependencies.

Exports use packwiz's normal download-host restrictions. Artifact metadata
explicitly selects `distribution=bundle` for the unchanged client-only Flywheel
Maven file and ImmersiveThunder CurseForge file, so they are stored in
`client-overrides` rather than the remote download
manifest. Verification checks its side, size and exact SHA-512/SHA-1; unreviewed
export-mode changes are rejected. See the
[third-party integration reference](../../metadata/README.md).

`build` fetches the pinned artifacts and audits native JARs before exporting.
The audit checks required dependency presence and required NeoForge ranges,
including nested JarJar modules on each installation side. Numeric and `-beta`
Maven interval bounds, exact ranges and unions follow the pinned loader's rules;
unsupported endpoint syntax fails explicitly. For Minecraft 1.21.1 with NeoForge
21.1.230, the audit retains FML 4.0.42's NeoForge 21.0.166 compatibility fallback.
Minecraft, Java, FML loader and other mods' version ranges, optional/incompatible
dependency rules and native JarJar selection remain launch checks. A successful
audit does not establish a successful Minecraft client start.

## Automated releases

CI verifies source, Java, Python, documentation and workflow definitions, then
builds and verifies a `.mrpack`. Branch pushes and pull requests run these checks;
PRs receive no publication credentials. Only successful **main push** exports are
retained as immutable Actions artifacts. Tags do not trigger duplicate pack CI.

The build runs `make release-check` before retaining an artifact. It reads the
original Actions event through `GITHUB_EVENT_PATH`, checks new pack versions
against the final PR title or main-push commit subject, and validates both target
changelog sections. It uses only `contents: read`; no release token is exposed to
PR code. Editing a PR title reruns CI. An export identical to an already published
version permits documentation/infrastructure updates without a new number or
numbered title. The gate fails closed on unavailable release metadata.
Read-only callers may not see drafts; pending-version reservations and interrupted
publication recovery remain the responsibility of the queue and publisher.

A separate publication workflow wakes after main CI, every 30 minutes, or through
`workflow_dispatch`. It follows actual push `before..head` receipts in CI run
names; intermediate commits inside one push are not separate releases. A
repository variable, `RELEASE_QUEUE_STATE`, records progress and any publication
started from a successful CI attempt. The coordinator exits while waiting for CI,
so waiting successors cannot occupy all runners. One workflow lock covers GitHub,
channel reconciliation and the entire Pages deployment. Wakeups may coalesce
because each run discovers the pending work again.

Release notes come from the current version's EN/RU sections in that exact head
snapshot. A failed integration-title check never supplies a publication artifact.
A corrective push can keep the same target and accumulated notes: the queue skips
the failed predecessor and processes the successful correction. Preserve the
number in the final merge/squash title as well as the PR title.

### One-time setup

All switches default to off. Follow the project's
[pack versioning](../../CONTRIBUTING.md#pack-versioning): set the next intended
version in `pack/pack.toml` and accumulate its changes in both changelogs until
publication. Local exports and further changes retain that target. Changelog
headings contain only the number, such as `## 0.1.1`. The next substantive change
after publication starts the next patch; the owner's stage-release preparation
instruction selects the next minor milestone. Use plain numbers throughout.
Published versions cannot be overwritten.
The workflow does not set secrets, repository variables, permissions or project
settings for you.

1. Install these workflows with all destinations disabled and wait for CI at the
   current main head to complete. Set `RELEASE_BASELINE_SHA` to that exact commit.
   It is the boundary before automatic publication, not a request to release that
   commit. Do not change it or manually edit/delete the queue state after starting.
2. Create repository secret `RELEASE_TOKEN` with a repository-scoped fine-grained
   GitHub token: **Contents write, Workflows write, Variables write, Actions read,
   Administration read**. This token is required for persistent reconciliation
   even when only Pages is enabled. Keep it out of files and logs. Publication
   steps receive it; historical source snapshots are parsed, never executed with it.
3. Enable GitHub immutable releases. Allow the token to create `v<version>` tags
   under repository tag rules. Set `RELEASE_ENABLED=true` to enable GitHub releases.
4. To enable Pages, choose GitHub Actions as its source, permit the `github-pages`
   environment to deploy automatically, and set `PAGES_ENABLED=true`.
5. Modrinth is optional and disabled when `MODRINTH_ENABLED` is unset or `false`;
   its project/token variables can remain unconfigured. To enable it later for
   an existing Modrinth **modpack** project, set `MODRINTH_PROJECT_ID`, secret
   `MODRINTH_TOKEN` with version-creation access, and `MODRINTH_ENABLED=true`.
   GitHub publication must also be enabled: Modrinth receives the exact published
   GitHub file, verified again against its original source and checksum.
   Set the required content disclosures in Modrinth's UI and then
   `MODRINTH_DISCLOSURES_CONFIRMED=true`; the stable API cannot verify them.
   Page synchronization is separately opt-in with `MODRINTH_PROFILE_ENABLED=true`
   and needs project-write access. Review the
   [profile sources, artwork provenance and enablement instructions](publishing/modrinth/README.md)
   before enabling it. A local `make modrinth-profile-check` validates the text and
   selected icon without network access; it also runs in `make verify`.
6. Leave `CURSEFORGE_ENABLED` unset or `false`. Its separate workflow and exporter
   are retained, but its outstanding requirements below prevent publication.
7. Push the intended release version to main after initializing the baseline.
   The baseline commit itself is not published, including after a manual dispatch.
   If it already contains the intended first version, a subsequent checked push
   can publish that still-unpublished version without another version bump.
   Changing repository variables alone does not start CI; scheduled reconciliation
   or a manual dispatch can discover later completed pushes. Settings changes are
   not a substitute for a successful source CI run.

Release versions must be strictly newer than previous published versions.
Reusing a published version with different pack contents fails. ZIP timestamps
and manifest formatting do not constitute a content change. Successful published assets are never replaced;
partially uploaded drafts resume from the same successful CI attempt and exact
retained artifact, including after a later failed CI rerun. Empty failed GitHub
upload placeholders may be removed only inside a draft.

### Recovery and limits

Scheduled runs reconcile a failed or missed publication wakeup. GitHub publication
and each channel report their own result. A Modrinth or CurseForge failure does
not prevent the other channels or Pages from running. Project moderation is a
separate state from successful upload; no workflow can automatically approve it.
Pages describes verified published GitHub files, flags private downloads, and
checks main again immediately before deployment. Invalid published checksums fail
visibly. The workflow lock prevents an older deployment overtaking a newer one.

A successful CI attempt is saved before the first GitHub release write. Its
original artifact is reused after ambiguous release/checkpoint responses; source
from another commit cannot silently replace it. A failed current CI push remains
pending for a rerun. Once a newer push supersedes a failed validation that never
started publication, that failed push is consumed without a release. Rerunning
that superseded push later does not retroactively publish it.

Modrinth uploads use a persistent per-project/version receipt. An interrupted
request is reconciled against the recorded version ID or exact listed metadata
before another upload is considered. If a request has an ambiguous outcome and no
matching version is visible, the workflow fails safely and scheduled runs keep
checking; it never blindly creates a duplicate. A request that never reached the
service may therefore need owner inspection before its intent can be reset. Keep
the receipt and service evidence; do not delete state to force a retry.

Force pushes, skipped/deleted CI push receipts, expired artifacts and changed
baseline/state fail visibly rather than guessing a release. Artifacts are kept
for 90 days (repository retention policy may impose a lower limit); GitHub's
rerun limits still apply. GitHub may delay/drop scheduled events or disable
inactive schedules, so this is not a guarantee against service outages. Protect
main from history rewrites and avoid `[skip ci]` on publication pushes. First
acceptance requires a real GitHub Actions prerelease, Pages deployment and
Modrinth upload/retry with the configured credentials. Local API tests do not
establish that acceptance.

### Optional CurseForge channel

`make curseforge-check` validates the source and reviewed channel mapping and
reports readiness without publishing. The exporter can create a deterministic
client ZIP once all dependencies have a permitted path:

```sh
uv run --locked --project tools/pack python -m aeroscapes_pack.curseforge_pack --output /absolute/path/pack.zip
```

[`metadata/curseforge.json`](../../metadata/curseforge.json) binds every selected
dependency to its primary SHA-512. There are 98 hosted candidates matching the
primary SHA-1 and seven reviewed platform variants whose differences are archive
metadata or generated package names; these have separate exact hashes. They do
not change the current Modrinth pack. The OpenCL module is hosted on CurseForge
and referenced by file ID, never bundled under its no-redistribution notice.

Four files remain blocked: Flywheel, No Void Structures, Custom Biome Saplings
(Terralith), and XP From Crops. Obtain exact hosted alternatives or confirmed
CurseForge non-hosted approval and redistribution rights; do not omit them or
substitute same-name files. The NeoForge manifest uses `neoforge-21.1.230`; live
CurseForge import/validation remains unverified.

`release-curseforge` checks published immutable GitHub assets against their exact
source and prepares the channel archive. It deliberately has **no upload POST**:
the documented Upload API provides neither an idempotency key nor a documented
owner listing of private pending uploads. Before enabling the channel, implement
and verify a durable upload journal with an explicit initialization/recovery
contract or an authoritative pending-file reconciliation capability. A public
file list alone cannot prove an ambiguous upload did not succeed. This disabled
preflight is not a completed unattended CurseForge publisher. It consumes no
CurseForge token and cannot affect GitHub, Modrinth or Pages. Its optional workflow
requires `CURSEFORGE_PROJECT_ID` only when explicitly enabled.

## Repeatable ship progression smoke check

Run from the repository root with the build JDKs configured as described in the
[development guide](../../CONTRIBUTING.md). Supply the Java 25
executable and a file containing the owner's accepted Minecraft EULA:

```sh
make smoke-ship JAVA_BINARY=/absolute/path/to/java25/bin/java EULA_FILE=/absolute/path/to/eula.txt
```

The target builds the separate diagnostic JAR, installs the current pack in a
new disposable server under `.runtime/`, uses seed 1 and waits for the explicit
completion marker before saving and stopping. It never opens a client or
changes an existing world. The printed directory retains the smoke result and
log. The setup requires the same network and loopback access as the existing
technical-server installer; the owner must accept the EULA before supplying
the file.

The fixture checks loaded crafting and smelting recipes, their outputs and
crafting remainders, selected Create processing recipe data, tool harvest
permissions and loot tables, and selected resources, a log and a water source
in a finite natural sample near the seed-1 spawn. Ingredients supplied to recipe
tests are not tracked through a player's complete inventory. A missing resource
fails this sample but is not a universal generation failure or proof of an
unreachable survival path.

The server also runs a water-powered Create press, captures a Blaze and heats a
basin for brass, and uses a saw and press to make an engine assembly through
the actual sequenced recipe. Unpowered and unheated controls check the press
and mixer boundaries. The mixer and engine stages use diagnostic creative
motors; the water wheel is checked separately. The fixture supplies some
ingredients and does not prove travel to their sources. It also uses the
server-side glue selection and physics assembler to create a physical object
with a portable engine. It checks coal consumption and shaft rotation before
assembly, preserved engine burn time, glued blocks and an unglued control.
Complete resource acquisition, client controls, flight and the owner's
save/reload checks remain separate work. A passing marker is not owner
acceptance of the survival route.
