# Development tools and checks

Use the root [Makefile](Makefile) from the repository root. Gameplay data belongs in `pack/`; Java source belongs in `mods/aeroscapes-reimagine/`; Python tools are the installed `tools/pack/src/aeroscapes_pack/` package. Tests import the package normally. Do not load source by path or modify `sys.path` to compensate for an obsolete layout.

## Environment

Python is 3.14, selected by [`tools/pack/.python-version`](tools/pack/.python-version). [`pyproject.toml`](tools/pack/pyproject.toml) owns Python dependencies and quality config; [`uv.lock`](tools/pack/uv.lock) pins packages and hashes. uv manages the ignored `tools/pack/.venv/`. Root Make sets `UV_CACHE_DIR`, `UV_PYTHON_INSTALL_DIR` and `UV_PYTHON_BIN_DIR` beneath `.cache/uv/`; no requirements-file or global Python-package workflow exists.

Builds require JDK 25 through `JAVA_HOME` and `JAVA_HOME_25_X64`, plus JDK 21 through `JAVA_HOME_21_X64` for NeoForge's Minecraft artifact tooling. Gradle discovers both explicit paths; a runtime JRE is insufficient. The owned mod retains Java 21 bytecode, while the pinned C2ME OpenCL module requires Java 25 at runtime. [`toolchain.json`](toolchain.json) pins the pack runtime; CI verifies and extracts that Corretto archive directly because setup-java's Corretto provider accepts only a major version. CI configures both build JDKs. The owning Gradle wrapper, plugin/tool versions and dependency verification remain in [`mods/aeroscapes-reimagine/`](mods/aeroscapes-reimagine/). Compilation uses `-Xlint:all -Werror`; checks include Spotless, PMD and policy tests. Gradle dependencies are hash-verified and cache under `.cache/gradle/`.

Documentation is a separate [`tools/docs/`](tools/docs/) Node project: Node 26.8.2, npm 11.19.1, Astro 7.4.2, Starlight 0.42.7, TypeScript 6.0.3, Prettier 3.9.10 with `prettier-plugin-astro` 1.1.2, and `@astrojs/check` 0.9.12. Owning manifests/lock are authoritative. Select an already installed pinned Node through `PATH` or mise; do not update global tools. Locked installation uses `npm ci --ignore-scripts`, with npm cache in `.cache/npm/`.

Go 1.27.3 runs the pinned actionlint module in [`tools/actions/`](tools/actions/) during `make verify`; its manifest and checksum lock own those tooling dependencies. Packwiz bootstrap/refresh/export also needs Go. Packwiz's commit and download installer hashes live in [`toolchain.json`](toolchain.json). `make verify` does not invoke Minecraft. Tool execution and headless checks target the current POSIX environment; Windows and extra Python versions are not supported claims.

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

For a direct uv call in a restricted work directory, preserve the same project/cache selection:

```sh
export UV_CACHE_DIR="$PWD/.cache/uv"
export UV_PYTHON_INSTALL_DIR="$PWD/.cache/uv/python"
export UV_PYTHON_BIN_DIR="$PWD/.cache/uv/bin"
uv run --locked --project tools/pack aeroscapes-pack check
```

Checks prove their own boundaries. Static compilation/style/policy tests do not start Minecraft, establish real generation, validate access transformations at runtime, measure FPS or prove visual acceptance. Python analyzers cover the owned tooling, not third-party code or generated worlds.

## Change dependencies and source

New Python dev tools belong in the dev group through `uv add --project tools/pack --dev name==version`; explain their role/overlap and update the lock in the same change. Update only the dependency relevant to the task and run `make sync verify` afterward. npm dependency changes likewise update the owning exact manifest and lock together.

Before accepting the first owned source in another language, choose and pin its real formatter and linter/type checker/compiler for the selected version, integrate it into `make verify` and CI, and run it. Do not add hypothetical unused toolchains. JSON/TOML and workflow YAML already have checks.

Ruff has substantive diagnostics rather than arbitrary argument/branch/statement quotas. Assess function boundaries by meaning and callers. Vulture findings must be checked against actual call sites; add only concrete documented references for proven dynamic callbacks. Static success does not prove naming, responsibility boundaries or security completeness.

The download implementation uses standard streamed copying, socket timeout, HTTPS/redirect validation, expected checksum and atomic cache publication. A socket timeout bounds an individual network operation, not the total transfer duration. Do not recreate unsupported global byte/time limits without a concrete need. Service commands use bounded checked subprocesses; long-running Minecraft has its separate readiness, log limit and graceful shutdown path.

## Diagnostic fixture practices

Use the separate [`src/regression/`](mods/aeroscapes-reimagine/src/regression/) mod for repeatable server cases. Its Gradle `worldgenRegressionJar` output never belongs in `pack/` or `.mrpack`. The fixture entry point owns its seed selections and completion markers. Install the diagnostic JAR only in a disposable server matching the current pack, with the selected seed in `server.properties`. The [ship smoke guide](tools/pack/README.md#repeatable-ship-progression-smoke-check) provides a complete supported scenario. Tests that continue after `Done` require `aeroscapes-smoke --ready-marker` so commands/save/stop cannot preempt them.

Keep only Mixins in the Mixin config's declared package and descendants. Place `@Mod` entry points and ordinary helpers in a neighboring package. Mixin rejects direct loads from its reserved package with `IllegalClassLoadError`; successful javac does not catch this, so a fixture needs a strict dedicated boot.

When checking whether Minecraft is already active, inspect process arguments internally but report only PID and process type. A client's command line can contain authorization arguments; never print it in diagnostics. State whether a functional run permits an existing client. Comparable performance runs require the client/load policy to be explicit. Process detection does not authorize operating the owner's client.

For generation JFR, preserve the original recording and select relevant event types, fields and operation intervals. Full JSON expansion of allocation/classloader metadata can dwarf the recording. JDK 21 `jdk.jfr.consumer.RecordingFile` can stream bounded time/thread/allocation/stack data. A recording threshold excludes shorter events; summed recorded parks do not fully partition wall time. Separate complete MSPT from instrumented partial-tick intervals and rendering frame time.

## Documentation changes

Keep `docs/en/` and `docs/ru/` paired by meaning, facts and implementation status.
Describe the current player experience; mark planned systems explicitly. Record
pack and tooling changes under the existing unreleased version in both changelogs,
using adjacent factual bullets without blank lines. See the [site guide](tools/docs/README.md)
for routes, page status, formatting and media attribution.

Dependency origins and distribution constraints are documented in
[metadata](metadata/README.md) and the owning license notices. Exact pins and hashes
remain in the manifests and metadata rather than a second manual inventory.
