# Third-party provenance and integration

Exact files, hashes, sides and embedded/API license differences live in [`metadata/artifacts.json`](../metadata/artifacts.json). [`worldgen_adoption.json`](../metadata/worldgen_adoption.json) records incorporated Aero data and Java behavior comparisons; [`compat_fixes.json`](../metadata/compat_fixes.json) binds static repairs; [`runtime_data_patches.json`](../metadata/runtime_data_patches.json) owns local JSON recipes; [`native_template_upgrades.json`](../metadata/native_template_upgrades.json) retains historical DFU reference hashes. [Public credits](../docs/en/credits.md) and `pack/licenses/THIRD-PARTY-NOTICES.txt` preserve attribution. The pack remains unpublished; development changes do not authorize a release.

## Working principle

Keep original upstream JARs byte-identical, retain notices, and prefer independently authored field-level integration over distributing complete modified upstream resources. Bind each change to exact provider/platform versions, distinguish artifact descriptors from file-specific terms, and preserve the scope of those terms when changing a dependency or extending an integration. Runtime processing does not itself relicense source material. Re-evaluate new scope using the actual files and applicable terms; do not treat the current pack's arrangement as blanket permission for future versions or other projects.

Unchanged files hosted on Modrinth are referenced through their official downloads under its [specific-file modpack policy](https://support.modrinth.com/en/articles/8797527-obtaining-modpack-permissions). Redistribution of a separate unchanged MIT artifact retains its embedded license; Flywheel's packaging is described below. Do not substitute the C2ME base's MIT license for the separate OpenCL module's terms.

## Local resource integration

The complete modified NBT and JSON payloads listed below have been removed from editable pack inputs. The owned mod reads unchanged installed originals and applies independent, minimal field operations in memory. It does not write generated provider data into the distributed pack. Hash-bound recipes guard original JSON identity before applying edits; native resource/registry loading consumes the results. Minimal empty pools, project predicates/tags and grounded-feature references remain project integrations rather than copied full definitions.

| Provider                                | Current local processing, including previously inherited structure wrappers                                                                                                                                                                                   |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CTOV 3.6.3 / `ztzRUnQ7`                 | 28 NBT: 17 rice-ID farms and 11 Allay-listener cages. 93 JSON recipes: 78 structure definitions and 15 pools, including the tower alias. Four independently defined optional empty pools remain.                                                              |
| Dungeons and Taverns 4.4.4 / `BYUUUeZA` | 830 templates use the standard Minecraft 1.21.1/NeoForge 21.1.230 STRUCTURE DFU in `StructureTemplateManager.readStructure`; no project DFU cache or preconverted copies. 133 JSON recipes: 80 structures, 51 pools and two Minecraft-namespace advancements. |
| Philips Ruins 1.21.1-2.0 / `EQHGxQAv`   | Eight NBT material repairs; 23 JSON recipes: 17 structures, five pools, one biome tag.                                                                                                                                                                        |
| Galosphere 1.5.3 / `Y3deS8Po`           | Two NBT repairs: five missing inventory entries and obsolete silver block; four JSON recipes: two structures, one pool, one ore placement.                                                                                                                    |
| Minecraft 1.21.1                        | 57 ancient-city templates gain missing AIR above their bottom layer during local resource load. The unused old `data/minecraft/structures/end_city/ship.nbt` was also removed: 1.21.1 resolves the singular `structure/` path.                                |

There are 253 effective JSON recipes replacing 254 physical copies (one resource had two pack owners). Namespace does not identify an author: some Dungeons resources live under `minecraft:`. Parity fixtures in the owned mod contain only IDs and hashes. They compare complete typed repaired NBT and native loaded serialization with the retired baseline, and effective JSON with previous definitions. DFU cost is measured separately from world generation; preparation moves into first template loads and is not a claim about FPS or full startup time. See [diagnostic fixture practices](../CONTRIBUTING.md#diagnostic-fixture-practices) for opt-in checks.

## Aeroscapes and Aero Islands

The reference is [Isabel's Aeroscapes 1.0.8](https://modrinth.com/modpack/aeroscapes/version/q4P5coPI), author Isabel / nuclearobelisk, project `x308hQIU`, version `q4P5coPI`. Its internal manifest says 1.0.7; this is upstream identity. [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) applies to that author's contribution, not all dependency resources. Selected data are reorganized, descriptions/predicates updated and the obsolete full noise override omitted. Derivative datapacks retain notices. Otherworld D&D remains an inspected gameplay reference without copied code/assets; no reference logos, menus, profile state, music or textures are transferred.

The exact Aero Islands 1.1.1 JAR (`tGVwBATM` / `bzJ2edxy`) declares GNU GPL v3.0+. Direct archive comparison confirms **160 byte-identical and 37 changed Aero JSON**, plus one separately identified Minecraft resource, in the owned mod. The external Aero JAR/custom content is excluded. Retain GPL-3.0-or-later for the complete owned mod in 0.1.0 and preserve data origins. A differing CC0 public source branch cannot silently replace the selected artifact's identity or license.

The owner reports rewriting Java with Aero Islands as inspiration. Commit `977b4d6b9f9597da90d0dd6a2212427f58b3f3b0` introduced the seven compared files and historical adaptation labels together. Eight original class hashes map to those seven files; bytecode confirms the unusual pillar taper, selected parameter-draw/material-pass ordering and mineshaft initial assembly. These are substantive behavior correspondences, not proof of the writing process or a legal classification of all Java. The present layout/terrain/biome-source implementation was added later (`e75e0ec9f8fafc40e49dd9b59a373108ca0d4dac`) and is not in that comparison inventory. Blanket claims that the entire Java generator is a port, or that every subsequent RNG draw is unchanged, were too broad; grounded basalt checks intentionally alter later draws. Java inventory `mode=reference` keeps verified historical hashes without asserting copied expression. This does not remove the incorporated data or the selected GPL obligations.

The modified `aeroscapes:fluid_level_floodedness` JSON retains the original within a natural-density condition and returns −1 outside; final terrain density is not overridden. The separate Aeroscapes-derived datapack retains its recorded CC BY-SA attribution. Resource addresses required by consumers do not transfer ownership.

## Notices and corresponding source

The owned JAR includes the complete GPL text and `META-INF/NOTICE.txt`, with origins, changes and source instructions. `pack/licenses/aeroscapes-reimagine-source.zip` accompanies the binary as a pack override. It contains the whole editable mod project, main/regression/policy sources/resources, Gradle scripts/wrapper/verification, PMD config, licenses, build README and provenance inventories. `SOURCE-MANIFEST.json` hashes every included input and binds the exact binary SHA-256. Recipients need no private repository, promised future URL or limited seven-file excerpt. `make companion` regenerates it; `make source-archive` refreshes it explicitly; `make check` rejects stale/missing source. Independent build instructions require JDK 25 and JDK 21 and the pinned wrapper; no ignored research/cache directory is required. This implements the source-access route of [GPLv3 §§1, 5–6](https://www.gnu.org/licenses/gpl-3.0.en.html), without relicensing unrelated provider content.

## Terralith data

Terralith 2.5.8 / `MuJMtPGQ` retains the bundled [Stardust Labs license](https://github.com/Stardust-Labs-MC/license/blob/main/license.txt). The active integration includes 30 Terralith-namespace JSON resources: 25 structure definitions, three cave-biome tags and two village pools. The climate delegate in `minecraft/dimension/overworld.json`, the biome-gated surface restoration and the amethyst placement remain separate version-bound resources. Namespace does not identify the source author.

The selected climate table has 1,653 rows; 1,573 are identical to the pinned JAR's 1,713 native rows, 140 native rows are omitted and 80 selected rows are changed/added. The surface restoration wraps the complete native `surface_rule` in a biome condition. Keep these adaptation boundaries in the artifact review record and re-evaluate them on provider updates. General structure/loot/compatibility provisions must be read with their exact exclusions rather than treated as universal modification terms.

## Nature's Spirit data

The 18 adapted `data/natures_spirit/` JSON files from 2.2.5 / `MGjl80vc` use the MIT part of the [split license](https://github.com/Team-Hibiscus/NaturesSpiritFabric/blob/master/LICENSE): nine ore placements, four village definitions and five pools. Preserve the complete Team Hibiscus copyright, permission and warranty notice at `pack/licenses/Natures-Spirit-data-MIT.txt`. The unchanged original JAR's descriptor says ARR; assets and model packages retain their separate terms. Do not relabel the complete JAR or future releases MIT. The chert interval orders authored endpoints; its intended interval is inferred rather than an upstream design claim.

## TerraBlender fix provenance

The owned stateless End-area correction adapts the approach of Partonetrain's CC0-1.0 [TerrablEndFix](https://github.com/Partonetrain/terrablendfix/blob/8c29736c54a677742d4bb309713b82f229642be6/neoforge/src/main/java/info/partonetrain/terrablendfix/mixin/LayeredNoiseUtilMixin.java). It substitutes only the erroneous island-biome input and has no process-global first-call flag. The published TerrablEndFix JAR is not installed; inspection found that its stateful form fixed only the first initialization. The upstream defect is also described in [TerraBlender issue 179](https://github.com/Glitchfiend/TerraBlender/issues/179).

Compile-only API `com.github.glitchfiend:TerraBlender-neoforge:1.21.1-4.1.0.8` comes from official Forge Maven without transitives or bundling. Gradle strict verification pins its JAR/POM. Runtime TerraBlender remains the unchanged Modrinth file `6e8GCrLb`, with original LGPLv3. Maven/runtime ZIP entries match except `META-INF/MANIFEST.MF`, so archive hashes are not interchangeable. The [owned TerraBlender adapter](../mods/aeroscapes-reimagine/src/main/java/dev/aeroscapes/mixin/TerraBlenderEndIslandMixin.java) records the exact-version/config dependence.

## Artifact metadata discrepancies

Treat the selected file's embedded terms and metadata as evidence alongside API labels; record discrepancies rather than silently normalizing them. Current notable cases include:

- Throwable Rope Connector 0.4.3: Modrinth API says MIT; selected JAR says All Rights Reserved.
- Iris/Flywheel Compat 2.4.0: API says CC0; selected file contains MIT.
- Moonlight 3.0.18: project API says LGPL with an extra condition; selected `LICENSE_Moonlight Lib.md` and manifest say Supplementaries Team License v1.5.

License files remain inside unchanged downloaded artifacts. Create retains MIT, Create Food GPL-3.0, Fungi Delight MIT and Delightful Creators CC0 in the recorded audit; these examples do not license the entire combined pack. Provider API dependency records can also differ from actual JAR declarations: preserve both and explain actual compatibility, as done for Rope Connector/Aeronautics and Supplementaries/Moonlight. Re-evaluate terms and actual dependency contracts when changing those selected files.

## Explicit Flywheel provider

`pack/mods/flywheel.pw.toml` pins official Maven Flywheel 1.0.6, client-only. Its bytes match Create 6.0.10's nested copy and its embedded `META-INF/LICENSE.md` retains MIT and Jozufozu's copyright. Vanillin 1.1.3 contains 1.0.4 under another Maven coordinate; an explicit provider prevents NeoForge's JarJar selection from satisfying Sable/Simulated with that older mod. Create retains its nested server library.

The Maven coordinate, URL and hashes remain provenance. `metadata/artifacts.json` selects `distribution=bundle`. The pinned packwiz Modrinth exporter uses its normal domain restrictions to place this exact file at `client-overrides/mods/flywheel-neoforge-1.21.1-1.0.6.jar`, outside the remote download manifest. Python verification checks the reviewed export mode, exact side-specific archive path, size, SHA-512 and SHA-1; a common/server copy or a remote-manifest entry is rejected. Reconsider the explicit provider only when the replacement combination selects the required version without it.

## C2ME OpenCL extension

The C2ME base is MIT. The separate OpenCL module `7rX3dlFf` retains its embedded ARR/no-redistribution notice and is supplied through the unchanged official Modrinth file under that platform's modpack policy. The owned adapter uses public compiler registries and independent density/basin/city integration; it does not copy kernels or distribute extracted module classes/nested JARs. Gradle's extracted APIs and LWJGL bindings are compile-only. A different distribution channel is a separate scope to assess.

The provider API lists ScalableLux as required, while this exact OpenCL descriptor declares it optional. The artifact audit preserves both records; ScalableLux is installed separately for lighting.
