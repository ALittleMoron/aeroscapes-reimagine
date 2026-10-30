---
title: Graphics, sound and animation
description: Character rendering, ambient sound, visual details and shader choices.
status: available
---

More detailed animations, environmental sound and small visual effects add to Minecraft’s standard presentation.

## Character and camera

[Not Enough Animations](https://modrinth.com/project/MPCX6s5C) adds and improves third-person animations. 3D Skin Layers renders the outer skin layer with depth. First person uses the normal hand view: rendering the whole animated body could obstruct the view during flight.

[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension) adds more expressive player movement and poses through Entity Model Features and Entity Texture Features. It remains alongside Not Enough Animations for third-person presentation. Tool and weapon swings use these providers’ existing animations.

[Punchy](https://modrinth.com/mod/punchy-fpa) handles first-person hands, with different movements for mining, chopping, digging and using items. The initial profile disables authored camera motion, extra walking/sprinting/falling movements and decorative overlays/particles. Inspection starts unbound and can be assigned in **Options → Controls**; Punchy's settings are available through **F8**. Combat rules remain unchanged.

[Refined Tools](https://modrinth.com/resourcepack/refined-tools) gives supported vanilla tools and other held items more detailed three-dimensional models, while tools keep flat inventory icons. Modefite and Puzzle provide its model support on this Minecraft version. Tools added by other mods are not automatically remodeled. Keep the **Aeroscapes Reimagine — Refined Tools compatibility** resourcepack enabled with Refined Tools; disable both together. It supplies the missing model parents and the held-trident rendering route.

[Shoulder Surfing](https://modrinth.com/mod/shoulder-surfing-reloaded) places the third-person camera slightly to the side, using the framing of Otherworld D&D. The ordinary perspective key (**F5** by default) still switches views. Movement follows the normal player direction, the camera inherits the player's field of view, and first person remains available. Shoulder switching can be assigned in **Options → Controls**.

Races, abilities and expanded action combat remain planned in [character and combat](../../character-and-combat/).

## Creatures, foliage and machinery

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) gives existing creatures more expressive movement and poses. Entity Model Features and Entity Texture Features provide its client rendering support. The player animation setup remains a separate layer.

[Fresh Animations: Quivers](https://modrinth.com/resourcepack/fresh-animations-quivers) adds a matching quiver to the backs of skeletons, strays and bogged. The pack's client adapter retains their current Fresh Animations models and movement, including the bogged's mushrooms. These are visual details, with no new equipment or ammunition rules. Keep Quivers directly above Fresh Animations in **Options → Resource Packs**; new installations select this order automatically.

[Motschen’s Better Leaves](https://modrinth.com/resourcepack/better-leaves) gives tree canopies a fuller silhouette. It covers vanilla and Quark foliage, most Nature’s Spirit leaves and Chipped’s leaf variants. Nature’s Spirit coconut leaves retain their original model. The pack changes leaf appearance rather than tree generation.

[Falling Leaves](https://modrinth.com/mod/fallingleavesforge) adds drifting leaf particles. Cherry blossoms retain their existing particles, and the pack leaves flowering azalea petals to Subtle Effects to avoid a second ambient leaf effect on those blocks.

[Create Touch-Ups](https://modrinth.com/resourcepack/create-touch-ups) adjusts Create's machinery, tools and copper textures toward a simpler vanilla style.

[Fusion Connected Blocks](https://modrinth.com/resourcepack/fusion-connected-blocks) connects textures on supported building materials, including bookshelves and polished andesite. [Fusion Block Transitions](https://modrinth.com/resourcepack/fusion-block-transitions) blends supported neighboring natural surfaces. Both use the existing Fusion engine and change block appearance rather than terrain generation.

## Glass and portable light

[Fusion Connected Glass](https://modrinth.com/resourcepack/fusion-connected-glass), supported by Fusion, joins the textures of adjacent vanilla glass blocks and panes. It includes stained and tinted glass; it does not automatically change glass added by other mods.

[LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) provides client-side light from held light sources. [Extended Illumina NEA](https://modrinth.com/resourcepack/extended-illumina-not-enough-animations) gives held torches and lanterns three-dimensional models and adjusted poses. These models and the emitted light are separate features; disabling the resourcepack does not disable LambDynamicLights.

These resourcepacks and the small atmosphere integration pack are selected in new installations. Each can be disabled in **Options → Resource Packs**. Fresh Animations and its Player Extension need both EMF and ETF while enabled. Existing profiles keep their selected packs and settings.

## Sound and visual details

[AmbientSounds](https://modrinth.com/mod/ambientsounds) adds environmental sound, while [Sounds](https://modrinth.com/mod/sound) supplies material footsteps and expands everyday interaction sounds. [Drip Sounds](https://modrinth.com/mod/dripsounds) adds sounds for dripping particles. [Subtle Effects](https://modrinth.com/mod/subtle-effects) supplies waterfall mist, water splashes and ripples, fireflies, sparks and other small details.

[Cool Rain Reforged](https://modrinth.com/mod/cool-rain-reforged) changes the sound of rainfall according to the surface it hits, including glass, metal, foliage, wool and water. Its sounds use Minecraft's **Weather** volume control.

[ImmersiveThunder](https://www.curseforge.com/minecraft/mc-mods/immersivethunder) changes individual lightning strikes according to their distance: close cracks, medium-distance thunder and distant rumbles. AmbientSounds continues to supply the storm background. Thunder uses the **Weather** volume control.

[Particle Rain](https://modrinth.com/mod/particle-rain) replaces rain and snow with wind-driven particles and adds weather fog and surface streaks. The initial profile keeps wind at lower island heights. Subtle Effects retains rain impacts and water ripples; Particle Rain's duplicate ripple/splatter effects are disabled. Cool Rain still supplies material rain sounds: keep Particle Rain's rain volume above zero, because zero suppresses that sound path too.

[Puddles & Floods](https://modrinth.com/mod/puddles-floods) adds temporary puddles on exposed flat ground during rain, including without shaders. Walking through them makes a small splash; they gradually disappear after the rain. Flooding and raised water levels are disabled. Drip puddles are also disabled because the shared particle hook can mistake Subtle Effects' villager sweat for water. The mod and its [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api) dependency are client-only here: flowing water, broken cauldrons and melting snow do not create server-side puddles. Rain placement uses the top exposed surface in each column, so a higher island shelters the ground below it.

In the interface, Chat Animation animates chat, while Smooth Gui animates menu opening.

[Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) adds reverberation and changes how obstacles muffle sounds. It processes acoustics alongside the existing environmental and interaction sounds. Sound-category volume controls remain available in Minecraft's sound settings.

## Sky

The atmosphere resourcepack uses [Polytone](https://modrinth.com/mod/polytone) to slightly deepen each biome's daytime sky colors and give dawn and dusk soft peach and amber tones. Most stars remain small, with occasional larger points that make the night sky more varied. It retains Minecraft's square sun and moon without photographic panoramas or a Milky Way backdrop; weather and night darkening remain active.

Smooth Skies smooths the distant color gradient, and Clear Void removes the black lower part of the skybox. [Complementary](https://modrinth.com/shader/complementary-reimagined) supplies its own procedural sky and stars, with a denser star field in the prepared profile; [MakeUp](https://modrinth.com/shader/makeup-ultra-fast-shaders) retains the vanilla star geometry and its varied sizes. The prepared profiles keep their existing cloud route.

Three small decorative planets add a pale, a softly banded and a reddish point to the night sky. They slowly shift against the stars as world days pass. Six brighter stars accompany the ordinary star field, while a northern guide star stays above north. The added lights fade with dawn, rain and the horizon and appear only in the Overworld.

To hide the added planets and stars, set `celestialSky = false` in `config/aeroscapes_reimagine-client.toml` and restart the game.

## Rendering

Sodium handles world rendering. ImmediatelyFast batches drawing work, Flerovium reduces the work needed to render models and particles, and BadOptimizations reuses lighting and sky calculations. Entity Culling and More Culling skip rendering hidden objects and block faces.

Vanillin uses Flywheel to render vanilla chests, shulker boxes and bells.

## Shaders

The pack includes [Complementary Reimagined](https://modrinth.com/project/HVnmMxH1) and [MakeUp Ultra Fast](https://modrinth.com/project/izsIPI7a), with prepared starting profiles. Shaders are disabled by default and remain optional for play.

The prepared Complementary profile disables **Waving Water Height** so puddles stay above the ground. Water surface normals retain their wave detail. If using your own shader settings, disable that option if puddles dip into blocks. MakeUp's prepared profile does not displace water vertices.

[Better Clouds](https://modrinth.com/mod/better-clouds) supplies the clouds. The prepared shader profiles select their **Vanilla** cloud mode so Better Clouds can use that rendering path. Keep Minecraft clouds enabled; an existing profile with clouds turned off retains that preference. Other shader profiles may need their own cloud setting.

Colorwheel connects Iris shaders to the Flywheel rendering used by Create and Vanillin.
