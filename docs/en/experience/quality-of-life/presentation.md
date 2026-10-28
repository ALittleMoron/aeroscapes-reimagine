---
title: Graphics, sound and animation
description: Character rendering, ambient sound, visual details and shader choices.
status: available
---

A visible first-person body, more detailed animations, environmental sound and small visual effects add to Minecraft’s standard presentation.

## Character and camera

[First-person Model](https://modrinth.com/project/H5XMjpHi) renders the character’s body in first person, including when looking down. [Not Enough Animations](https://modrinth.com/project/MPCX6s5C) adds and improves third-person animations. 3D Skin Layers renders the outer skin layer with depth.

These mods change how the existing character is shown. Races, abilities, expanded action combat and a side-offset third-person camera are described in [character and combat](../../character-and-combat/).

## Creatures, foliage and machinery

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) gives existing creatures more expressive movement and poses. Entity Model Features and Entity Texture Features provide its client rendering support. The player animation setup remains a separate layer.

[Cubic Leaves](https://modrinth.com/resourcepack/cubic-leaves) adds depth to tree canopies while retaining a cubic shape. The included Performance edition covers vanilla and Quark foliage and most Nature's Spirit leaves; coconut and Joshua leaves retain their original models. It changes their appearance rather than tree generation.

[Create Touch-Ups](https://modrinth.com/resourcepack/create-touch-ups) adjusts Create's machinery, tools and copper textures toward a simpler vanilla style. These three resourcepacks are selected in new installations. Each can be disabled in **Options → Resource Packs**; Fresh Animations needs both of its rendering mods while enabled.

## Sound and visual details

AmbientSounds adds environmental sound, while Sounds expands the sounds of everyday interactions. Drip Sounds adds sounds for dripping particles. Subtle Effects adds small particle details and some sounds around the world.

Smooth Skies smooths distant sky colors, and Clear Void removes the black lower part of the skybox. In the interface, Chat Animation animates chat, while Smooth Gui animates menu opening.

## Rendering

Sodium handles world rendering. ImmediatelyFast batches drawing work, Flerovium reduces the work needed to render models and particles, and BadOptimizations reuses lighting and sky calculations. Entity Culling and More Culling skip rendering hidden objects and block faces.

Vanillin uses Flywheel to render vanilla chests, shulker boxes and bells.

## Shaders

The pack includes [Complementary Reimagined](https://modrinth.com/project/HVnmMxH1) and [MakeUp Ultra Fast](https://modrinth.com/project/izsIPI7a), with prepared starting profiles. Shaders are disabled by default and remain optional for play.

Colorwheel connects Iris shaders to the Flywheel rendering used by Create and Vanillin.
