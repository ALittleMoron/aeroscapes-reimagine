---
title: How the island world works
description: Floating terrain, ocean basins and how structures were adapted to islands.
status: available
---

The floating Overworld is based on Aero Islands generation. Reimagine’s own mod develops that terrain alongside other mods’ biomes and structures: moving them to islands exposed assumptions about continuous land. The Default world preset is currently supported.

C2ME distributes chunk generation across workers and compiles terrain calculations. OpenCL can move noise calculations to a compatible GPU with native double-precision support; generation falls back to the CPU when that path is unavailable. The pack’s own adapter preserves its island shapes, basin water and city caverns. Structure Layout Optimizer reduces work when assembling and placing structures.

Feature placement is prepared when the world opens. In singleplayer, ordinary terrain chunks arrive in batches adjusted to the client’s processing speed, spreading the work of receiving a large ready area.

## Islands, mountains and lower heights

Most islands used to be concentrated high above a largely empty lower world. Lower islands and additional ore placements were added so exploration could extend downward. The Overworld now spans Y−128…383; the original upper islands retain their heights and density.

The added large islands initially looked too smooth, and their mountains resembled symmetrical cones. Their terrain was reworked into connected areas at different heights, with hills, ridges, chasms and caves. One such island can contain several biomes, with plains alongside high peaks. These large formations are rare, with smaller islands scattered between them.

Badlands spires that erroneously extended through the void to the bottom of the world were corrected; those growing on the islands remain. Long accidental basalt bridges between islands separated by large height gaps were also removed.

## Oceans

A map could previously show an ocean where there was only void. Ocean regions now have a chance to contain large islands with deep basins, uneven seabeds, bays and shores. Ocean and shore biomes follow these basins, while monument, shipwreck and other water-structure placement adapts to the local waterline. These are separate island basins rather than a continuous sea beneath the islands.

Terralith, Nature’s Spirit and Galosphere provide the other landscapes. Aero’s four original biomes and its custom content are excluded. Basalt formations and geodes were converted to vanilla materials, while the project’s own fractured jungle is retained. [Places and discoveries](../discoveries/) describes the landscapes and things to find.

## How structure placement was repaired

Structure mods expected continuous land: on floating islands, parts of dungeons lacked support and large complexes often hung outside the terrain. Placement was reworked to account for the size and support of the whole complex. The pack tries to fit it near the original starting point and rejects the attempt if no suitable site is available. Individual passages and entrances can emerge from slopes; intended aerial structures and open bridges are retained.

Trial chambers could previously appear almost entirely above ground. The pack now selects sites with rock above most exterior roofs and rejects attempts without that cover. Terrain filling used to block the spaces between ancient city buildings; they now share a cavern with uneven ceilings. Sites where a city would cut through an ocean basin’s floor and send water into the cavern are also rejected.

The stricter checks have made some structures rarer. A matching biome alone does not guarantee that a particular island can accommodate a dungeon.

## Starting a game and saved worlds

New worlds use a separately selected natural starting island; its biome, resources and nearby dangers remain random. An existing world’s starting point is not moved.

Create a new world to see the complete terrain changes. Explored chunks are not rebuilt, and their boundaries with new terrain can be abrupt. Saves containing removed Aero content have no supported conversion. Back up the world before updating; rollback requires that backup and the previous pack. See [Compatibility and support](../../../known-issues/#saved-worlds) for details.

Return to [World and exploration](../).
