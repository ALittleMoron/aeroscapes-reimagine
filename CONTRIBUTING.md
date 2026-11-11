# Development tools and checks

Use the root [Makefile](Makefile) from the repository root. Gameplay data belongs in `pack/`; Java source belongs in `mods/aeroscapes-reimagine/`; Python tools are the installed `tools/pack/src/aeroscapes_pack/` package. Tests import the package normally. Do not load source by path or modify `sys.path` to compensate for an obsolete layout.

## Environment

Python 3.14.10 is selected by [`tools/pack/.python-version`](tools/pack/.python-version). [`pyproject.toml`](tools/pack/pyproject.toml) owns Python dependencies and quality config; [`uv.lock`](tools/pack/uv.lock) pins packages and hashes. uv manages the ignored `tools/pack/.venv/`. Root Make sets `UV_CACHE_DIR`, `UV_PYTHON_INSTALL_DIR` and `UV_PYTHON_BIN_DIR` beneath `.cache/uv/`; no requirements-file or global Python-package workflow exists.

Builds require JDK 25 through `JAVA_HOME` and `JAVA_HOME_25_X64`, plus JDK 21 through `JAVA_HOME_21_X64` for NeoForge's Minecraft artifact tooling. Gradle discovers both explicit paths; a runtime JRE is insufficient. The owned mod retains Java 21 bytecode. [`toolchain.json`](toolchain.json) pins the pack runtime; CI verifies and extracts that Corretto archive directly because setup-java's Corretto provider accepts only a major version. CI configures both build JDKs. The owning Gradle wrapper, plugin/tool versions and dependency verification remain in [`mods/aeroscapes-reimagine/`](mods/aeroscapes-reimagine/). Compilation uses `-Xlint:all -Werror`; checks include Spotless, PMD and policy tests. Gradle dependencies are hash-verified and cache under `.cache/gradle/`.

Documentation is a separate [`tools/docs/`](tools/docs/) Node project: Node 26.13.0, npm 12.2.2, Astro 7.4.10, Starlight 0.42.10, TypeScript 6.0.3, Prettier 3.9.10 with `prettier-plugin-astro` 1.1.2, and `@astrojs/check` 0.9.12. Owning manifests/lock are authoritative. Select an already installed pinned Node through `PATH` or mise; do not update global tools. Locked installation uses `npm ci --ignore-scripts`, with npm cache in `.cache/npm/`.

Go 1.27.4 runs the pinned actionlint module in [`tools/actions/`](tools/actions/) during `make verify`; its manifest and checksum lock own those tooling dependencies. Packwiz bootstrap/refresh/export also needs Go. Packwiz's commit and download installer hashes live in [`toolchain.json`](toolchain.json). `make verify` does not invoke Minecraft. Tool execution and headless checks target the current POSIX environment; Windows and extra Python versions are not supported claims.

## Supported commands

Start with:

```sh
make sync
make verify
```

`make sync` installs both locked Python and documentation dependencies. `uv sync --locked` already rejects an outdated lock; adding a preceding lock check repeats the same contract. CI uses locked commands and does not rewrite locks during verification.

| Command                | Scope                                                                                                |
| ---------------------- | ---------------------------------------------------------------------------------------------------- |
| `make lint`            | Ruff diagnostics/imports/annotations and Python format check                                         |
| `make typecheck`       | ty, with enabled diagnostics treated as errors                                                       |
| `make security`        | Bandit on owned executable Python                                                                    |
| `make deadcode`        | Vulture on application/test references, confidence 60                                                |
| `make yaml`            | Strict yamllint for workflow YAML                                                                    |
| `make workflows`       | Pinned actionlint for GitHub Actions expressions and reusable-workflow contracts                     |
| `make check`           | JSON/TOML, packwiz hash chain, dependency/provenance/compatibility consistency                       |
| `make test`            | Python behavior tests                                                                                |
| `make companion-check` | Java compilation/checks, policy tests, regression-JAR build and equality of built/pack companion JAR |
| `make companion`       | Same owned-mod checks, then explicitly copy the JAR into `pack/`; follow with `make refresh`         |
| `make docs-check`      | Documentation format, Astro/TypeScript checks, Node tests, static build and local links              |
| `make docs-build`      | Build the local public documentation site                                                            |
| `make docs-preview`    | Preview the built site locally; this is not publication                                              |
| `make quality`         | Supported language/tooling quality checks and companion comparison                                   |
| `make verify`          | Full supported checks, including docs-check, data and behavior, without Minecraft                    |
| `make format`          | Apply the supported formatters; verify again afterward                                               |
| `make bootstrap`       | Obtain/check the pinned packwiz tool                                                                 |
| `make refresh`         | Refresh the packwiz index after an intentional pack edit                                             |
| `make build`           | Export and verify a local `.mrpack`                                                                  |
| `make release-check`   | CI integration gate after export; uses the GitHub event and read-only release metadata              |

For a direct uv call in a restricted work directory, preserve the same project/cache selection:

```sh
export UV_CACHE_DIR="$PWD/.cache/uv"
export UV_PYTHON_INSTALL_DIR="$PWD/.cache/uv/python"
export UV_PYTHON_BIN_DIR="$PWD/.cache/uv/bin"
uv run --locked --project tools/pack aeroscapes-pack check
```

Checks prove their own boundaries. Static compilation/style/policy tests do not start Minecraft, establish real generation, validate access transformations at runtime, measure FPS or prove visual acceptance. Python analyzers cover the owned tooling, not third-party code or generated worlds.

## Change dependencies and source

When adding or updating a mod, compare the exact JAR’s dependency ranges against the resolved client and server inventories. Include optional dependencies wherever they are installed. A dedicated-server boot cannot exercise client-only version constraints.

For interface themes, start from the pinned client inventory and trace the screens,
provider-specific tabs and controls to the resources their renderers actually use.
A shared panel can still leave a separate screen unthemed, and a mod can inherit
vanilla text colors while using an overridden background. Check the drawn regions,
labels and control states offline before identifying the gaps that require a live client.

New Python dev tools belong in the dev group through `uv add --project tools/pack --dev name==version`; explain their role/overlap and update the lock in the same change. Update only the dependency relevant to the task and run `make sync verify` afterward. npm dependency changes likewise update the owning exact manifest and lock together.

Before accepting the first owned source in another language, choose and pin its real formatter and linter/type checker/compiler for the selected version, integrate it into `make verify` and CI, and run it. Do not add hypothetical unused toolchains. JSON/TOML and workflow YAML already have checks.

Ruff has substantive diagnostics rather than arbitrary argument/branch/statement quotas. Assess function boundaries by meaning and callers. Vulture findings must be checked against actual call sites; add only concrete documented references for proven dynamic callbacks. Static success does not prove naming, responsibility boundaries or security completeness.

The download implementation uses standard streamed copying, socket timeout, HTTPS/redirect validation, expected checksum and atomic cache publication. A socket timeout bounds an individual network operation, not the total transfer duration. Do not recreate unsupported global byte/time limits without a concrete need. Service commands use bounded checked subprocesses; long-running Minecraft has its separate readiness, log limit and graceful shutdown path.

## Diagnostic fixture practices

Use the separate [`src/regression/`](mods/aeroscapes-reimagine/src/regression/) mod for repeatable server cases. Its Gradle `worldgenRegressionJar` output never belongs in `pack/` or `.mrpack`. The fixture entry point owns its seed selections and completion markers. Install the diagnostic JAR only in a disposable server matching the current pack, with the selected seed in `server.properties`. The [ship smoke guide](tools/pack/README.md#repeatable-ship-progression-smoke-check) provides a complete supported scenario. Tests that continue after `Done` require `aeroscapes-smoke --ready-marker` so commands/save/stop cannot preempt them.

Keep only Mixins in the Mixin config's declared package and descendants. Place `@Mod` entry points and ordinary helpers in a neighboring package. Mixin rejects direct loads from its reserved package with `IllegalClassLoadError`; successful javac does not catch this, so a fixture needs a strict dedicated boot.

For a moving terrain-delivery measurement, use `-Daeroscapes.regression.flightChunks=true` and require `Flight terrain measurement completed`. By default the fixture warms the current tracking view, sends 200 native one-block movement packets, then holds position until delivery catches up. Optional `aeroscapes.regression.originX` and `originZ` select a cold chunk centre; `flightY` selects block height and `flightStep` sets blocks per packet (greater than zero, at most two). `flightCold=true` starts after the nearby 25 chunks arrive, while the rest of the view may still be generating. Its nine-chunk ACK feedback is an ideal sink, not a client. Packets follow server ticks, so this fixture does not reproduce a real client's packet backlog during a server stall or competition with rendering. Report wall time separately from server ticks; holder visibility can precede the later sending stage and neighbor lighting.

For opt-in live chunk diagnostics, create the empty file `config/aeroscapes-chunk-diagnostics.enabled` before starting the game. Server and client emit `[Aeroscapes chunk flow]` samples to the normal log at most once per second for five minutes per session. The samples cover existing server holders/pending delivery, client cache, frame rate and native renderer debug counters. They do not request chunks or change queues. Remove the marker before the next launch to disable the probe. The owner performs all client launches and flight.

The same marker enables `[Aeroscapes liveness]` samples from a bounded independent observer, including while the server is waiting inside `getChunk`. It records synchronous wait coordinates/status, tick and frame heartbeat ages and GC totals. No full thread dumps or automatic JFR recording are taken. Client samples include focus and screen identity to correlate map/Alt+Tab transitions.

Client samples also summarize intervals between consecutive rendered frames while focused, unpaused and without a screen or loading overlay. The count, measured active time, mean/maximum interval and strict `>50 ms`/`>100 ms` counts describe wall-clock frame pacing, including presentation waits; they do not identify CPU, GPU or GC costs. The threshold counts overlap. A zero interval count means no measured active frames, not hitch-free play. Menus, focus loss and world transitions discard cross-boundary intervals. A long hitch stretches a reporting window, so normalize counts by measured active time. Route, altitude, fresh terrain and graphics settings must match before claiming a numerical before/after improvement; the tick-paced headless sink cannot supply a client frame-time baseline.

`maxFrameEndNanos` anchors the longest measured interval to `System.nanoTime()`. On the tested Linux JDK 25 runtime, unified JVM logs with the `timenanos` decorator use that same clock. Correlate actual GC pauses and safepoint stops with the interval ending at that timestamp; concurrent-cycle duration alone is not a pause. Keep the collector and heap unchanged when first adding logging, and archive rotated logs before another launch. Launcher-specific JVM arguments are not part of the standard `.mrpack` format.

When checking whether Minecraft is already active, inspect process arguments internally but report only PID and process type. A client's command line can contain authorization arguments; never print it in diagnostics. State whether a functional run permits an existing client. Comparable performance runs require the client/load policy to be explicit. Process detection does not authorize operating the owner's client.

For generation JFR, preserve the original recording and select relevant event types, fields and operation intervals. Full JSON expansion of allocation/classloader metadata can dwarf the recording. JDK 21 `jdk.jfr.consumer.RecordingFile` can stream bounded time/thread/allocation/stack data. A recording threshold excludes shorter events; summed recorded parks do not fully partition wall time. Separate complete MSPT from instrumented partial-tick intervals and rendering frame time.

Do not repeatedly sample a live client with `jcmd Thread.print`: full dumps can introduce second-long safepoint pauses. Prefer bounded JFR for interval analysis and check `jdk.ExecuteVMOperation` and `jdk.SafepointBegin` before attributing pauses to gameplay or rendering. If losing window focus relieves the fault, start recording before asking the owner to switch away from the game.

## Documentation changes

Keep `docs/en/` and `docs/ru/` paired by meaning, facts and implementation status.
Describe the current player experience and useful choices; mark planned systems
explicitly. Explain implementation details only where they help the player use
a setting or understand a practical limitation.

Public changelogs compare the previous release with the final delivered player
experience. Include visible features and fixes, performance improvements, compatibility
changes and required player actions. Exclude internal diagnostics, test fixtures,
unchanged behavior and experiments or reversals that never reached a release.
Describe an optimization by its player benefit without inventing measured gains.
Keep developer diagnostics in this guide or the owning project README. Use the
current target version in both changelogs, with adjacent factual bullets and no blank
lines between them. See the [site guide](tools/docs/README.md) for routes, page
status, formatting and media attribution.

Dependency origins and distribution constraints are documented in
[metadata](metadata/README.md) and the owning license notices. Exact pins and hashes
remain in the manifests and metadata rather than a second manual inventory.

## Pack versioning

The [release guide](docs/en/releases.md) owns the meaning of the three version
parts and the project's milestones. Use plain `major.minor.patch` numbers.
Accumulate a change group under its selected target until integration into main;
preparatory commits and local exports retain that number. A separate substantive
group after that integration starts the next patch, even if CI or publication of
the previous group failed. Recovering that same integration retains its number.
Do not automatically create an empty successor version: main pushes feed publication.

At the start of a task that changes the pack or its tooling, or adds a changelog
entry, inspect the current main integration, actual non-draft GitHub releases,
CI outcome and existing release queue, even when keeping the version recorded
locally. Recheck before exporting if these may have advanced during the task.
A local export, changelog heading or tag alone does not establish publication;
absence of a release does not establish that its version was never integrated.
A main push reserves its version even if CI fails. Distinguish recovery of that
group from new content before choosing a target. Published contents are immutable.

One final PR or push to main carries one pack version. A push may contain several
preparatory commits; the checked head is the source snapshot for its release.
Include the exact pack version in the PR title and the final main commit's first
line, for example `feat: [0.1.1] animations and foliage`. A `v` prefix is accepted.
Before merging, preserve that number in the final squash or merge commit title;
a generic default merge title is insufficient. Intermediate commit titles do not
all need the number.

After building, CI runs `make release-check` using the event JSON and its
`contents: read` GitHub token. For a new version it checks the integration title
and exactly one non-empty section in each changelog before artifact retention.
An export identical to the same published version is exempt from a new version
and title requirement. This is based on exported content, not changed paths:
public site documentation and infrastructure are outside the pack, while files
distributed in the pack affect its identity. A push containing accumulated pack
changes is a pack integration even if its final commit only edits documentation.

Correct a PR title to rerun its checks. If a main-push title fails, use a normal
follow-up commit with the correct title and the same target version. Failed CI
retains no publication artifact; the queue consumes that failed push before
publishing the corrected successful successor. Never amend a published release
or force-push to repair its title. Read-only API failures also stop this gate.

The owner's instruction to prepare the accumulated changes for a new stable
release selects the next stage milestone: `0.1.1` becomes `0.2.0`, then a later
stage becomes `0.3.0`. A milestone can record completion of content already
delivered in patch releases; it does not require adding more content.
Keep the milestone's goal distinct from the final release's changes: 0.2.0 means
the selected atmosphere work is complete, while 0.3.0 means the selected QoL work
is complete. Fixes to other systems may accompany either release without
redefining that goal, even if the final release adds no new milestone features.
A mention of a future milestone or its theme, a candidate discussion, or approval
to add content does not request this promotion. Continue ordinary patch development
until the owner explicitly asks to prepare the stage release or set the current target.
A selected minor milestone target stays unchanged through its preparation and
integration; publication recovery does not select another milestone. The major milestones `1.0.0` and
`2.0.0` need the owner's corresponding scope decision. Preparing a release does
not itself authorize a commit or push.

CI rejects a new major version that skips the preceding published stable major.
For example, `5.0.0` requires a stable `4.x` release. The transition from `0.x`
to `1.0.0` is technically valid; the agreed core must still be complete and
accepted before the owner selects that milestone. When a requested release
number contradicts the accepted roadmap or current stage, explain the conflict
and confirm the intended target before applying or publishing it. A literal
version number alone does not establish milestone completion.

Set the target in `pack/pack.toml` before exporting. Use one matching section in
each changelog, headed only by the number, such as `## 0.1.1`, without a release
status suffix. When the current changes are promoted to a stage milestone before
integration, move them into that milestone's section; preserve integrated sections.
Release descriptions use only the target section from the checked source
snapshot. Keep checks and remaining owner acceptance in the handoff, outside release notes.
The owned mod and build tools retain their independent versions.

A version increment does not guarantee saved-world compatibility. Describe any
affected content or migration separately and follow the
[saved-world guidance](docs/en/known-issues.md#saved-worlds).

## Weather and ship fixture

With cheats enabled in the Overworld, stand on solid ground and run `/aeroscapes_test_ship`. The permission-2 player-only command imports Tommobean's [Basic Aeronautics Airship](https://createmod.com/schematics/basic-aeronautics-airship) into a new Sable plot in loaded clear air and seats its owner after the client confirms the ship has loaded. If loading fails, the player stays in place and can retry `board` near the ship. Existing terrain is not replaced. Each new world can create its vessel with the same command, without carrying a saved world between versions.

Use the ordinary right-click interaction on the levers and steering wheel. Facing the wheel, the right lever sets lift (8 starts near neutral), the left sets propulsion (0 slow, 13 fast, 15 stopped), and the wheel turns the front control surfaces. Do not increase the drive beyond 13: the author's transmission reaches the native rotation limit. No global keybindings change. The portable engine starts with ordinary coal-block fuel and can be refueled. The command preheats the native envelope once; later lift responds normally and gradually to heating, altitude, trim and added mass. At altitudes where the downloaded envelope cannot support its native mass, creation fails safely; start lower.

The whole downloaded construction, including its native steering-bearing child and honey glue, is retained. Players may add blocks, containers and machinery. Connect additions to the hull; Sable treats detached components as separate vessels. More payload can require lift adjustment or additional envelopes. The engine, native radio channels, control settings, balloon gas, steering joint and added blocks save normally. Each created ship receives private native radio-frequency pairs before joining a network.

- `/aeroscapes_test_ship board` returns to the original pilot seat. Restore a Create seat at its original location if it was removed.
- `/aeroscapes_test_ship leave` stops propulsion and returns to the saved safe starting point.
- `/aeroscapes_test_ship remove` returns the owner and deletes their tagged vessel and its tracked swivel-bearing parts, including additions and inventories. Other players on any tracked part block deletion. Load the complete vessel at its last location before removing it.

Old schema-1 platforms keep their safety hold until explicitly removed; remove one before creating the new vessel in that world. New vessels have no artificial hold. Before downgrading, remove newly created test vessels or restore the matching older world copy.

The downloaded file is pinned at `pack/config/aeroscapes_reimagine/test_airship.nbt`; its attribution, hash and current reuse scope are in `metadata/test_airship.json` and the third-party notice. It is an owner-local test fixture; a public redistribution grant has not been established. Keep this separate from permission to use the unchanged public download locally.

For a fishing-line problem, cast first and run `/aeroscapes_rod_trace`. It records at most 120 local rows/120 client ticks in `logs/latest.log`, including selected geometry and hand/line projection; it is off by default. Both Flerovium fast item-render paths forward the real rod mesh to Punchy's existing capture. Third-person tool actions log one scope summary and at most one reached arm-transform sample per category (`Aeroscapes tool pose`); those observations do not establish visual acceptance.

Build the dedicated fixture with `worldgenRegressionJar`. Select `-Daeroscapes.regression.testShip=prepare`, then `reload` on the same disposable server save. It uses an ordinary registered ServerPlayer, in-memory server transport, normal levers and the native steering-wheel packet handler. Player NBT and the ship are saved and restored. No client or socket peer is started; normal mouse grip, camera, shader and appearance remain owner checks. Never export the regression JAR or run it in an owner world.
