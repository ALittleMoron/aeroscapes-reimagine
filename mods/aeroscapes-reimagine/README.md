# Build the owned mod from source

This is the editable project for `aeroscapes-reimagine-0.1.0.jar`, licensed under
GPL-3.0-or-later. See `LICENSE` and `src/main/resources/META-INF/NOTICE.txt` for
terms and origins. The source archive includes the project's Java sources,
resources, regression fixtures, build configuration, Gradle wrapper and dependency
verification metadata. `SOURCE-MANIFEST.json` at the archive root identifies the
associated binary by SHA-256 and hashes every included source input. The binary
itself is not inside the source archive.

The archive's `metadata/` directory contains `worldgen_adoption.json`,
`runtime_data_patches.json` and `native_template_upgrades.json`, which record
origins and exact reference hashes. The template audit contains hashes rather
than third-party template payloads. These records preserve provenance; they do
not grant additional rights to the referenced projects.

## Requirements

Use a POSIX system with a JDK 25 and a JDK 21. Gradle runs and compiles with JDK 25;
NeoForge's Minecraft tooling additionally needs JDK 21. The mod produces Java 21
bytecode. JRE installations alone are insufficient. The current pack runtime is
Amazon Corretto 25.0.5.0.9. Network access is needed for the wrapper and verified
build dependencies on the first build; no existing project cache is required.

The wrapper pins Gradle 9.2.1 and its distribution checksum. The build pins
NeoForge 21.1.230 for Minecraft 1.21.1, ModDev 2.0.148, Spotless 8.10.4,
google-java-format 1.36.1, ktfmt 0.64 and PMD 7.28.0. Compile-only integration
APIs are downloaded using the exact coordinates in `build.gradle.kts` and checked
against `gradle/verification-metadata.xml`. These APIs are not bundled into the
resulting JAR.

## Build

Extract the archive, then enter `mods/aeroscapes-reimagine/` within it. Set these
variables to the absolute paths of your installed JDKs:

```sh
export JAVA_HOME_25_X64=/absolute/path/to/jdk-25
export JAVA_HOME_21_X64=/absolute/path/to/jdk-21
export JAVA_HOME="$JAVA_HOME_25_X64"
export PATH="$JAVA_HOME/bin:$PATH"
sh ./gradlew --no-daemon check jar
```

The result is `build/libs/aeroscapes-reimagine-0.1.0.jar`. `check` runs formatting,
PMD, policy tests and compilation of the separate server regression fixture. It
does not start Minecraft. `jar` alone builds the main binary without the quality
checks. The separate `aeroscapes-worldgen-regression` fixture is not installed as
part of the pack.

The original celestial atlas can be regenerated with
`sh ./gradlew --no-daemon generateCelestialAtlas`. Its deterministic Java pixel
composition uses no external images. `celestialLayoutTest` checks the sky hook,
directions, time continuity and visibility without starting a Minecraft client.
The added sky layer is client-only; `celestialSky` in the native
`aeroscapes_reimagine-client.toml` controls it independently of other atmosphere.

For local installation, replace the matching owned JAR in an existing compatible
pack's `mods/` directory with your result. Keep the runtime dependencies and pack
data matching that pack version. The source project does not include third-party
Minecraft/mod binaries. It builds without the pack repository's Python tools,
Makefile, private evidence, saved worlds or caches.

## Source archive in the pack repository

From the full repository root, `make companion` builds and checks the JAR, copies
it into `pack/mods/`, then regenerates
`pack/licenses/aeroscapes-reimagine-source.zip`. `make source-archive` regenerates
that ZIP for the current JAR without invoking Gradle. Run `make refresh` after
updating pack files. Pack validation rejects a source ZIP that no longer matches
the current Java, resources, build inputs, provenance inventory or binary hash.
The ZIP travels with the binary as a pack override; recipients can extract it
without accessing a private repository or an external source-hosting account.
