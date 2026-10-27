---
title: Releases and limitations
description: Release status, supported modes and working with saved worlds.
status: available
---

## Releases

No stable release has been published yet. Installation availability is described on the [installation page](../install/).

## Game mode and world type

Singleplayer is the main game mode. Multiplayer and a separate server distribution are deferred.

Only the Default world type is currently supported for the island world.

## Saved worlds

Create a new world for the Y−128…383 height and island layout described in [world generation](../experience/world/generation/). Generation changes apply to new chunks. Existing islands and structures are not rebuilt automatically, and lower layers are not added beneath explored terrain; boundaries between old and new terrain may differ.

Replacing the external Aero Islands mod removes its biomes and custom content. Saves created with that mod have no supported conversion; create a new world when making that switch.

Keep an untouched world backup before updating. To roll back, restore both the previous pack and that backup.

## In-game language

Project-owned in-game text is written in English first. A complete Russian game localization is planned separately.

## Feedback

The author makes the final design decisions and considers suggestions and bug reports in the [project tracker](https://github.com/ALittleMoron/aeroscapes-reimagine/issues).

When reporting a problem, include the pack version, expected and actual behavior, and a short way to reproduce it. For world-generation issues, add the seed, coordinates and whether the chunks were newly generated. For visual problems, graphics settings and the shader name are useful.
