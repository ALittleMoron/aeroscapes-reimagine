---
title: Changelog
description: Version history.
status: available
---

## 0.2.2

- Disable item model previews in the starting [Legendary Tooltips](https://modrinth.com/mod/legendary-tooltips) configuration to prevent overlap with [Quark](https://modrinth.com/mod/quark)'s attribute icons.
- Fix a crash when opening settings sections for [Equipment Compare](https://modrinth.com/mod/equipment-compare) and [Legendary Tooltips](https://modrinth.com/mod/legendary-tooltips).
- Check the full ground footprint of Ocean Fortress from [Philips Ruins](https://modrinth.com/mod/KdJhOYVV) and the rock cover above Pink Salt Shrine from [Galosphere](https://modrinth.com/mod/Kn7xyQ1v), rejecting unsuitable island edges.
- Add [Jade Addons](https://modrinth.com/mod/jade-addons-forge), [EMI Enchanting](https://modrinth.com/mod/emi-enchanting), [EMIffect](https://modrinth.com/mod/emiffect) and [Enchantment Descriptions](https://modrinth.com/mod/enchantment-descriptions) for machine, enchantment and effect information.
- Add [Smart Tooltip Scroll](https://modrinth.com/mod/smart-tooltip-scroll), [Equipment Compare](https://modrinth.com/mod/equipment-compare) and [Legendary Tooltips](https://modrinth.com/mod/legendary-tooltips), with English/Russian settings and a native editor for the latter two.
- Fill missing enchantment and effect descriptions in English and Russian while preserving the warning names of internal enchantments.

## 0.2.1

- Fit the underground rooms of Creeping Crypt from [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) inside suitable islands while preserving its surface entrance.
- Align held lantern and soul lantern handles with [Punchy](https://modrinth.com/mod/punchy-fpa)'s first-person grip when using [Extended Illumina NEA](https://modrinth.com/resourcepack/extended-illumina-not-enough-animations).
- Restrict rain-impact particles to rainy biomes while retaining [Particle Rain](https://modrinth.com/mod/particle-rain)'s weather sounds.
- Use blackstone variants for the eight vanilla ores, [Create](https://modrinth.com/mod/create) zinc and [Galosphere](https://modrinth.com/mod/galosphere) palladium when deposits form in blackstone terrain.
- Fill missing English and Russian content, description and interface translations, including generated storage wood names; correct lost formatting arguments and make [Saplanting](https://modrinth.com/mod/saplanting)'s Russian command language selectable.
- Add client-only [BetterF3](https://modrinth.com/mod/betterf3), [Cherished Worlds](https://modrinth.com/mod/cherished-worlds) and [Leave My Bars Alone](https://modrinth.com/mod/leave-my-bars-alone).
- Track authored ore-tag extensions separately from adopted generator data during pack validation.

## 0.2.0

- Complete the selected audiovisual atmosphere stage developed through the preceding 0.1.x releases.
- Assign empty space away from islands to an empty biome with a small terrain buffer.
- Keep rain and thunderstorm weather enabled in the air between islands.
- Check terrain only for matching climate candidates during native three-dimensional biome searches.
- Restore [Quark](https://quarkmod.net/)'s cave biome selection through the island biome source.
- Preserve native stronghold rings and underground structure eligibility when separating island biomes from empty sky.
- Preserve bees placed from structure templates during background generation by isolating their AI constructor's random access.
- Restore buried treasure chests on basalt beaches in newly generated chunks after the usual rock search finds no site.
- Fill missing English and Russian biome names and selected item, entity, effect and interface translations.
- Add dedicated-server coverage for Overworld, Nether and End biomes, natural feature and structure block writes, and native placement filters and weighted selection, plus teleport chunk-delivery diagnostics.

## 0.1.7

- Add biome color variation, campfire smoke, decorative birds, eating animations and selected interaction and hit particles.

## 0.1.6

- Add three small decorative planets, brighter accent stars and a fixed northern guide star, fading with daylight, weather and the horizon.
- Enable **Hear yourself** in the initial [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) profile.
- Pin Forgified Fabric API to a build whose bundled modules support the current NeoForge; reject incompatible bundled NeoForge requirements before export.
- Add client-side Puddles & Floods with Forgified Fabric API, disabling floods, raised water and drip puddles; disable Complementary water-height waves to keep puddles above the ground.
- Add distance-dependent lightning strike sounds with ImmersiveThunder, retaining Sounds footsteps and interaction audio.
- Give the vanilla/MakeUp star field a wider size range and increase Complementary star density.
- Pin official CurseForge files by project/file ID and verify their unchanged client override exports.

## 0.1.5

- Add Fresh Animations: Quivers for skeletons, strays and bogged, retaining their current Fresh Animations models and movement through a client compatibility adapter.

## 0.1.4

- Add standalone Punchy with restrained initial hand/camera settings and an adapter for the current EMF hand-render state; retain Fresh Animations, FA Player and Not Enough Animations.
- Add Refined Tools with native Modefite and Puzzle, bundled MidnightLib and independent missing-parent model shims.
- Add Particle Rain with a precipitation-only Sable movement opt-out, dimension-aware height bounds and corrected altitude-independent wind.
- Keep material rain audio with Cool Rain and rain impacts with Subtle Effects, disabling duplicate Particle Rain ripples and splatter.
- Prevent Puzzle from replacing the logo texture already retained by the NeoForge loading screen.
- Render held Refined tridents through their block model and separate the Refined compatibility resourcepack.
- Restore Punchy rod-tip capture in both Flerovium fast item renderers, including its shader path.
- Replace the weather platform with a ready-made Aeronautics airship by Tommobean, created by one cheat command with a fueled engine, native levers and steering wheel; preserve editing, save/reload, return and removal.
- Wait for the client to load the test airship before placing its pilot in the seat.

## 0.1.3

- Add material-dependent rain sounds and craftable wind chimes with native Sable support.
- Add an Otherworld-style shoulder camera and guard redundant zero-sway rotation on Sable ships.
- Add restrained vanilla sky colors and smaller stars through Polytone and the atmosphere resourcepack.
- Add Fusion Connected Blocks, Fusion Block Transitions and Create Style Sophisticated Backpacks as initial resourcepack selections.
- Add Beautiful Enchanted Books for distinct enchanted-book appearances.
- Add Better Clouds and route the prepared shader profiles through vanilla clouds.
- Add Sound Physics Remastered for reverberation and sound occlusion.
- Add Exposure photography and Immersive Melodies instruments.
- Add Visual Workbench with persistent crafting-table contents and update Puzzles Lib for it.
- Allow the exact owner-approved Sound Physics Remastered beta file while retaining checks against unapproved prereleases.
- Guard Dramatic Doors conversions that reference door variants absent from Chipped.

## 0.1.2

- Add Handcrafted furniture and Chipped building variants with Resourceful Lib and Athena.
- Replace Cubic Leaves with Motschen’s Better Leaves.
- Remove First-person Model after body clipping and downward-view obstruction during flight; retain the player animation packs.
- Add Polymorph for choosing between matching crafting results, including EMI recipe transfer.
- Add Fusion Connected Glass with Fusion, Falling Leaves, Fresh Animations: Player Extension, and portable light through LambDynamicLights and Extended Illumina NEA.
- Select the new resourcepacks in fresh client profiles and keep flowering azalea particles with Subtle Effects to avoid duplicate ambient falling leaves.

## 0.1.1

- Add the author's manifesto on AI and authorship, discussing Modrinth's rules in English and Russian.
- Group Character and combat, Magic and Settlements in the sidebar, each with an Overview page.
- Apply the Night Sky and Gold website palette in light and dark modes.
- Use the selected Mysterious Islands title cover on the documentation homepages.
- Add real pack screenshots to the README, homepage, gallery and world guides, with lightweight web images and English/Russian captions.
- Fix the Inventory Profiles Next settings section so refill indicators are hidden without disabling sorting or refill.
- Add Fresh Animations with EMF/ETF, Cubic Leaves and Create Touch-Ups, enabled for new client profiles.
- Validate selected resourcepack files against the pack inputs and show resourcepacks in the dependency table.
- Keep accumulated changes under one version until publication and define numbered stage, core-content and integration milestones.
- Render download links from neutral installation instructions without rewriting changelog headings.
- Check pack versions in integration titles and both changelogs before retaining release exports; exempt updates with unchanged pack content.
- Build release notes from the current version's section in the checked source snapshot.
- Add a dedicated release guide and group release navigation separately from compatibility and support.

## 0.1.0

### Build and publication

- Verify pack exports for pull requests and branch pushes, and add pinned GitHub Actions validation.
- Add opt-in GitHub releases, Modrinth uploads and documentation deployment with persistent publication progress and scheduled recovery.
- Add a separate CurseForge export preflight and reviewed file mapping; keep the channel disabled.
- Add an English Modrinth project profile linking to English and Russian documentation, a credited icon candidate and opt-in synchronization of page text and icon.

### Foundation and compatibility

- Bundle the unchanged client-only Flywheel 1.0.6 artifact with its MIT notice instead of leaving a Maven URL in the exported download list.
- Include complete corresponding source and build instructions beside the GPL-licensed owned mod.
- Replace distributed modified CTOV, Dungeons and Taverns, Philips Ruins, Galosphere and ancient-city resources with local loading transformations; remove the unused legacy end-city ship template.
- Add a repeatable server smoke check for ship progression and assembly.
- Set the default Minecraft sound sliders to 15%.
- Add ScalableLux with a vanilla-lighting adapter for Sable ships and repair Create auxiliary light on them.
- Add opt-in client commands for showing the local test monitor and recording a chosen duration without game keybindings.
- Install the pinned Corretto archive directly in CI so Java 25 setup accepts the exact build.
- Add an opt-in, bounded trace for Sable sublevel tracking and movement events.
- Update Item Descriptions to its NeoForge build to avoid loading the Fabric Jade plugin.
- Add the missing AmbientSounds suspense category for pale-garden ambience.
- Remove a source of decoration-placement differences caused by C2ME lighting neighbouring chunks prematurely.
- Prepare native feature indexes while opening a world despite TerraBlender cancelling the usual validation call, and pace singleplayer terrain batches using client feedback.
- Pin Flywheel 1.0.6 explicitly so Vanillin's older bundled copy cannot block startup with Sable and Simulated.
- Add ImmediatelyFast, More Culling, Flerovium, BadOptimizations and block-entity-only Vanillin with conservative defaults; replace Iris/Flywheel Compat with Colorwheel.
- Add Structure Layout Optimizer with Resourceful Config; keep layout-changing template deduplication disabled.
- Add C2ME and OpenCL generation with an owned island, basin and city-cavern adapter; move the pack runtime to Java 25.
- Keep chunk generation on the CPU until GPU programs are ready, including after a compilation failure, so waiting for the GPU cannot block saving and shutdown.
- Compile island density calculations and reuse basin geometry and density bounds while preserving interpolation and biome sampling order.
- Reuse island-column geometry within each aquifer to reduce repeated calculations during chunk generation.
- Mark the bundled default options as Minecraft 1.21.1 data to avoid legacy keybinding conversion when loading fresh settings.
- Establish the pinned Minecraft 1.21.1 / NeoForge 21.1.230 foundation with Create, Aeronautics and the owned island generator based on Aero Islands.
- Update Sable, Sodium, Aeronautics, Iris and their related compatibility mods together; resolve Sable’s dedicated-side client-class errors and filter Gyro’s invalid placeholder physics entry.
- Update Supplementaries to 3.6.8 and Moonlight to 3.0.18 to address the Sodium fluid-renderer crash.
- Repair incompatible recipes, mushroom tags, advancements and datapack entries.
- Remove the duplicate ModernFix biome-temperature patch, overlapping Quark inventory features, Prickle, Structurify and the redundant Terralith spire override.
- Disable Quark hotbar swapping and hide Inventory Profiles Next AutoRefill indicators while retaining refill.

### World generation and structures

- Reduce added small and exceptionally large island selection by about 15%, retaining surviving layout sizes and positions.
- Correct trial chambers appearing almost entirely above ground by choosing sites where most exterior roofs are covered by rock.
- Remove erroneous badlands spires built through the void down to the world floor while retaining spires on land; limit long basalt bridges between islands with large height gaps.
- Rework the smooth geometric forms of added islands and mountains with varied elevations, ridges, cavities, ledges and irregular chasms, and give ocean basins uneven seabeds.
- Remove solid terrain filling around underground rooms that blocked passages.
- Give ancient cities shared caverns with uneven ceilings and reject sites that would cut through an ocean basin floor.
- Add support checks for Dungeons and Taverns combat shrines and submerged conduit ruins; Witch Villa recognizes the installed mods’ swamp biomes.
- Extend the Overworld to Y−128…383, add lower and small islands, and introduce rare broad islands with mountain relief.
- Replace void in some ocean regions with deep island basins and matching ocean and shore biomes; monuments select suitable sites on their seabeds.
- Adapt icebergs, blue ice and aquatic spawning to local basin water levels.
- Preserve existing effective ore heights and add separate lower placements.
- Replace the external Aero Islands mod with owned generation using Lithostitched; retain its natural island density and existing integrations, substitute vanilla biomes/materials, and exclude Aero’s custom content.
- Select a naturally supported starting island with the required size and spawn height.
- Prevent Nature’s Spirit pillars from reaching the world floor and aquifers from creating unsupported stone spheres in the void.
- Restore Terralith biome selection, surface materials, village entries and seasonal tower eligibility.
- Fix affected Minecraft, Philips Ruins, Dungeons and Taverns and Terralith complexes hanging beyond island edges by checking the whole assembly and rejecting unsuitable sites.
- Restore stronghold floors; preserve the original rooms and rewards of strongholds and ancient cities.
- Repair template references, obsolete blocks and items, Allay listener data and hanging-entity anchors; remove the dangling Philips Ruins `ancient_dungeon_6` pool entry.
- Extend amethyst geode placement into high islands.
- Correct Nature’s Spirit’s transition between rock layers to Y−8…25 in new chunks.
- Restore natural Small End Islands and their native feature by correcting TerraBlender’s island-biome input; pin its standard edge and island region sizes.
- Reuse native noise volumes for structure terrain checks.
- Use Minecraft’s standard data conversion when loading 830 original Dungeons and Taverns templates, preserving loaded data and authored layouts.

### Structure searches and maps

- Continue direct player structure searches across server ticks and cancel replaced or disconnected searches.
- Yield structure-start chunk waits between ticks and release each owned search ticket on completion, failure or cancellation.
- Avoid creating and syncing empty region files during native structure scans while preserving cartographer trades, targets and maps.

### Development tools and documentation

- Separate repository checks from local development-assistant tooling and document standalone development commands and dependency provenance.
- Prepare automated GitHub Releases, documentation deployment and optional Modrinth publication after main-branch verification.
- Report a content fingerprint for verified modpack exports independently of ZIP timestamps.
- Support exact, audited Maven downloads alongside Modrinth dependency pins.
- Refactor island feature generation into explicit shape, material and vegetation stages while preserving behavior.
- Strengthen compatibility and owned-JAR audits to reject invalid versions, missing metadata, duplicate and unreadable entries.
- Add typed pack tooling, verified downloads, strict server smoke checks and shared Python/Java validation.
- Reject conflicting archive destinations and improve smoke-process cleanup and error detection.
- Add map-persistence and transition-height regressions and platform, artifact and result-hash checks for local template conversion.
- Add a bilingual documentation site with nested gameplay sections, a pack inventory and link checks.
- Separate agent context from public pages and add editorial rules and a compatibility procedure for new content.
