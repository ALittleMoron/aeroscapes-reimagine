---
title: Graphics, sound and animation
description: Character rendering, ambient sound, visual details and shader choices.
status: available
---

More detailed animations, environmental sound and small visual effects add to Minecraft’s standard presentation.

## Character and camera

[Not Enough Animations](https://modrinth.com/project/MPCX6s5C) adds and improves third-person animations. 3D Skin Layers renders the outer skin layer with depth. First person uses the normal hand view: rendering the whole animated body could obstruct the view during flight.

[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension) adds more expressive movement, poses and hand animations to the player. It uses the same Entity Model Features and Entity Texture Features engines as Fresh Animations for creatures.

These mods change how the existing character is shown. Races, abilities, expanded action combat and a side-offset third-person camera are described in [character and combat](../../character-and-combat/).

## Creatures, foliage and machinery

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) gives existing creatures more expressive movement and poses. Entity Model Features and Entity Texture Features provide its client rendering support. The player animation setup remains a separate layer.

[Motschen’s Better Leaves](https://modrinth.com/resourcepack/better-leaves) gives tree canopies a fuller silhouette. It covers vanilla and Quark foliage, most Nature’s Spirit leaves and Chipped’s leaf variants. Nature’s Spirit coconut leaves retain their original model. The pack changes leaf appearance rather than tree generation.

[Falling Leaves](https://modrinth.com/mod/fallingleavesforge) adds drifting leaf particles. Cherry blossoms retain their existing particles, and the pack leaves flowering azalea petals to Subtle Effects to avoid a second ambient leaf effect on those blocks.

[Create Touch-Ups](https://modrinth.com/resourcepack/create-touch-ups) adjusts Create's machinery, tools and copper textures toward a simpler vanilla style.

## Glass and portable light

[Fusion Connected Glass](https://modrinth.com/resourcepack/fusion-connected-glass), supported by Fusion, joins the textures of adjacent vanilla glass blocks and panes. It includes stained and tinted glass; it does not automatically change glass added by other mods.

[LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) provides client-side light from held light sources. [Extended Illumina NEA](https://modrinth.com/resourcepack/extended-illumina-not-enough-animations) gives held torches and lanterns three-dimensional models and adjusted poses. These models and the emitted light are separate features; disabling the resourcepack does not disable LambDynamicLights.

These resourcepacks and the small atmosphere integration pack are selected in new installations. Each can be disabled in **Options → Resource Packs**. Fresh Animations and its Player Extension need both EMF and ETF while enabled. Existing profiles keep their selected packs and settings.

## Sound and visual details

AmbientSounds adds environmental sound, while Sounds expands the sounds of everyday interactions. Drip Sounds adds sounds for dripping particles. Subtle Effects adds small particle details and some sounds around the world.

Smooth Skies smooths distant sky colors, and Clear Void removes the black lower part of the skybox. In the interface, Chat Animation animates chat, while Smooth Gui animates menu opening.

## Rendering

Sodium handles world rendering. ImmediatelyFast batches drawing work, Flerovium reduces the work needed to render models and particles, and BadOptimizations reuses lighting and sky calculations. Entity Culling and More Culling skip rendering hidden objects and block faces.

Vanillin uses Flywheel to render vanilla chests, shulker boxes and bells.

## Shaders

The pack includes [Complementary Reimagined](https://modrinth.com/project/HVnmMxH1) and [MakeUp Ultra Fast](https://modrinth.com/project/izsIPI7a), with prepared starting profiles. Shaders are disabled by default and remain optional for play.

Colorwheel connects Iris shaders to the Flywheel rendering used by Create and Vanillin.
