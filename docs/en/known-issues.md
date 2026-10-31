---
title: Compatibility and support
description: Supported modes, saved worlds and reporting problems.
status: available
---

## Game mode and world type

Singleplayer is the main game mode. Multiplayer and a separate server distribution are deferred.

Only the Default world type is currently supported for the island world.

## Saved worlds

Create a new world for the Y−128…383 height and island layout described in [world generation](../experience/world/generation/). Generation changes apply to new chunks. Existing islands and structures are not rebuilt automatically, and lower layers are not added beneath explored terrain; boundaries between old and new terrain may differ.

Replacing the external [Aero Islands](https://modrinth.com/mod/tGVwBATM) mod removes its biomes and custom content. Saves created with that mod have no supported conversion; create a new world when making that switch.

Keep an untouched world backup before updating. To roll back, restore both the previous pack and that backup.

The Open Sky biome applies to newly generated chunks. Previously explored empty space keeps its saved biomes, so biome colors and weather can change at the boundary. A save written with Open Sky contains a new biome ID; use the untouched backup when returning to a version that predates it.

## In-game language

Project-owned in-game text is written in English first. Biome names and selected missing interface labels have Russian translations; a complete Russian game localization remains planned.

## Feedback

The author makes the final design decisions and considers suggestions and bug reports in the [project tracker](https://github.com/ALittleMoron/aeroscapes-reimagine/issues).

When reporting a problem, include the pack version, expected and actual behavior, and a short way to reproduce it. For world-generation issues, add the seed, coordinates and whether the chunks were newly generated. For visual problems, graphics settings and the shader name are useful.
