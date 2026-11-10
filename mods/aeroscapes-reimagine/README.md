# Build the owned mod from source

This is the editable project for `aeroscapes-reimagine-0.1.0.jar`, licensed under
GPL-3.0-or-later. See `LICENSE` and `src/main/resources/META-INF/NOTICE.txt` for
terms and origins. The source archive includes the project's Java sources,
resources, regression fixtures, build configuration, Gradle wrapper and dependency
verification metadata. It also includes the pack's backup configuration,
the two owned resource packs and their notices, which the quality checks read.
`SOURCE-MANIFEST.json` at the archive root identifies the
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
Amazon Corretto 25.0.5.0.10. Network access is needed for the wrapper and verified
build dependencies on the first build; no existing project cache is required.

The wrapper pins Gradle 9.9.1 and its distribution checksum. The build pins
NeoForge 21.1.244 for Minecraft 1.21.1, ModDev 2.0.148, Spotless 8.11.0,
google-java-format 1.36.1, ktfmt 0.65 and PMD 7.29.0. Compile-only integration
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

Keep the extracted `pack/` directory beside `mods/`; the checks validate the
shipped configuration and generated equipment resources there. Minecraft resource
contracts use the artifact resolved by the Gradle build, independently of the
location of your Gradle cache.

The original celestial atlas can be regenerated with
`sh ./gradlew --no-daemon generateCelestialAtlas`. Its deterministic Java pixel
composition uses no external images. `celestialLayoutTest` checks the sky hook,
directions, time continuity and visibility without starting a Minecraft client.
The added sky layer is client-only; `celestialSky` in the native
`aeroscapes_reimagine-client.toml` controls it independently of other atmosphere.

Language supplements and their separate licenses are recorded in
`src/main/resources/META-INF/localization-sources.json`. Exact-value Russian
corrections preserve changed provider translations. Client adapters translate
literal Catnip, NeoForge and Puzzles configuration text while retaining native
settings and metadata; runtime-dependent default/range values remain unchanged.
The corresponding bytecode contracts and pure fallback behavior are checked by
`atmosphereContractTest`. Saplanting's classloader language path has a separate
Russian dictionary and native language-selection adapter.

Ten ore variants use blackstone as their host material. Native ore/blob writes
select those variants without changing their shape or random draws. Loot,
experience, tool requirements and processing retain the pinned source behavior.
The client composes their sprites from installed ore and blackstone textures;
provider PNGs are not bundled. The separate server fixture contains opt-in ore,
crypt placement/reload and copied-save upgrade/rollback checks.

For local installation, replace the matching owned JAR in an existing compatible
pack's `mods/` directory with your result. Keep the runtime dependencies and pack
data matching that pack version. The source project does not include third-party
Minecraft/mod binaries. It builds without the pack repository's Python tools,
Makefile, private evidence, saved worlds or caches.

## Aquatic, movement and survival integration

The `aquatic`, `movement` and `survival` packages isolate exact-version content
adapters from the island generator. [Hybrid Aquatic](https://modrinth.com/mod/hybrid-aquatic)
habitats use the existing enclosed basins; the provider's global terrain wrappers
are skipped only for the owned island source. Local waterlines, seabed placement
and [Tide](https://modrinth.com/mod/tide) depth conditions follow actual basin water.
Fishing retrieval invokes the aquatic encounter once before ordinary catch
rewards. Both native encounter hooks, conditional hook perks and block-only
floating crates retain their original behavior; missing catch records complete
the native aquatic fish-item selection. Pending conch encounters use dimension
saved data across restarts. Wrapper discovery traverses the exact native
Blueprint and Lithostitched delegates, retaining the outer resolvers while an
invocation-local sampler carries the isolated TerraBlender/Biolith source clone.

[Paragliders](https://www.curseforge.com/minecraft/mc-mods/paragliders) retains its
native stamina, rewards and exchanges. Its rotated underground template bounds
are corrected before natural support checks and chunk references are computed.
[Yori3o’s Grappling Hooks](https://modrinth.com/mod/yori3os-grappling-hooks) retains
native motion and its provider's ship integration. Client display adapters cover
literal settings without changing parser values or personal key assignments.

[Legendary Survival Overhaul](https://www.curseforge.com/minecraft/mc-mods/legendary-survival-overhaul)
and [Nutritional Balance](https://www.curseforge.com/minecraft/mc-mods/nutritional-balance)
are tuned in the pack, with a shared live nutrition panel for the standalone and
lunchbox screens. On the pinned loader, global SERVER configuration belongs in
`config/`; an existing world's `serverconfig/` can override it. The data pack
assigns actual foods to the provider's nutrient tags and removes recipes and
loot for the disabled limb/health systems while retaining their registry IDs.

The separate regression JAR has `movement`, `aquatic` and `survival` opt-ins in
`WorldgenRegression`. They exercise loaded provider behavior on disposable
dedicated worlds; the survival report includes registered food/drink coverage.
They do not operate a client or establish visual, input or shader acceptance.
The optional aquatic biome list preserves old definition decoding. The active
dimension data supplies the current habitat roster on load, while saved FULL
chunks retain their stored biomes and terrain. Rollback restores an untouched
world with its matching older pack.

## Ecology integration

The `ecology` package adapts [Spawn](https://modrinth.com/mod/spawn-mod),
[Fowl Play](https://modrinth.com/mod/fowlplay),
[Hybrid Birds](https://modrinth.com/mod/hybrid-birds) and
[This Rocks!](https://modrinth.com/mod/this-rocks-forge) to the island terrain.
Provider and loader gates belong to `EcologyMixinPlugin`; the original provider
JARs remain unchanged. Native bird population defaults are retained.

Spawn operations use a thread-local, nested first-air sea level above the actual
basin water. Native pieces, random streams and saved origins remain authoritative.
Its small islands require a complete natural basin host and open clearance;
marine decoration does not erase the underlying ocean family's structure
eligibility. The three ordinary marine biomes use physical basin selection,
while ant gardens retain native climate selection. Client tint reads bounded
per-level basin metadata sent before chunk meshes; an exact merged-handler
transform preserves the provider's original tint calculation.

The native preliminary surface cutoff can lie above deep basin floors.
`SpawnMarineSurfaceMixin` admits only the native marine soil band below an
observed basin water column: one block for deep warm ocean, nine for seagrass.
It retains the original biome, water and stone-depth rules; dry ground, cave
floors below the basin liner and unrelated biome rules keep the native cutoff.

Spawn restores climate tuples after Biolith's parameter cache. Retrieve the final
transformed `MultiNoiseBiomeSource.parameters()` result through
`EffectiveBiomeParametersAccess` before cloning initialized TerraBlender trees.
The inner Biolith cache alone is not that result. Bind the resulting clone to
both native fields and the Biolith cache.

`TemplateStructureSupport` checks the original loaded palettes for
[Explorify](https://www.curseforge.com/minecraft/mc-mods/explorify)'s land buildings
and the selected Spawn jigsaws. It keeps native assembly and checks material
bearings against connected natural solid intervals. Seabed projection runs before
native jigsaw height clamps; terrain-matching elements also check their projected
content against build bounds. `ExposedIslandSurfaces` uses the generator's
uncarved interpolation to distinguish lower exterior layers from cave floors.

Dodo's synchronous initial search keeps its native spiral and monument exclusion.
Its native tag check rejects unsuitable sites directly; querying a different
height cannot provide a reliable non-ocean biome to signal rejection.
`SpawnIslandColumns` reuses scalar heights and compact, sea-independent native
column summaries within one search. The bounded caches are discarded in `finally`;
other structure calls do not retain them. The full water footprint is checked
before native noise sampling, and tile rectangles outside the clearance circle
are skipped without changing the support or open-sky predicates.

The separate regression JAR includes `groupTwo`, `ecology` and biome-coverage
branches. Source selection, observed native structure painting and stored FULL
biomes are distinct evidence. The paint observer records exact native extents
and requires a matching reference-linked piece; it does not turn painted cells
into source-selection witnesses. Keep the diagnostic JAR out of the pack.

## Source archive in the pack repository

From the full repository root, `make companion` builds and checks the JAR, copies
it into `pack/mods/`, then regenerates
`pack/licenses/aeroscapes-reimagine-source.zip`. `make source-archive` regenerates
that ZIP for the current JAR without invoking Gradle. Run `make refresh` after
updating pack files. Pack validation rejects a source ZIP that no longer matches
the current Java, resources, build inputs, provenance inventory or binary hash.
The ZIP travels with the binary as a pack override; recipients can extract it
without accessing a private repository or an external source-hosting account.

### Equipment resource generation

`./gradlew generateEquipmentModels` regenerates
the original equipment meshes/materials in the two existing owned resource packs.
`equipmentModelsCheck` checks these outputs as part of `check`. Independent rod
geometry retains the pinned Refined Tools display transforms through parent
references; the generator does not copy provider assets. `rodModelContractTest`
checks cast/idle meshes, mirrored reels, atlas coordinates and Punchy's actual
tip detector; `hookCompatibilityTest` checks selective binding migration and
the animated handle projection.

The vanilla rod uses the same owned construction and explicit context/cast routes.
Its idle tackle is clipped to the shaft; deployed meshes omit that tackle.
`rodModelContractTest` checks both families, their cast routing and native line-tip detection.
Selected equipment uses authored hand geometry while GUI, ground and fixed views
retain the native presentation. Native glider emergency descent remains;
client adapters reconcile its arm pose with the installed animation stack.
