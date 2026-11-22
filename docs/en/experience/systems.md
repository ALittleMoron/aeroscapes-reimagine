---
title: How the systems connect
description: A map of how farming, engineering, travel, magic, equipment and survival feed into each other.
status: partial
---

Each system in the pack can be played on its own. Learning several of them makes the others easier: a farm can fuel engines, a workshop can automate a kitchen, and equipment from any system can be reforged. Solid arrows show connections that work in the current pack. Dashed arrows show planned connections that are ideas for later releases, not finished features.

## Overview

```mermaid
flowchart TB
  world["World and exploration"]
  travel["Travel and flight"]
  eng["Engineering"]
  farm["Farming and animals"]
  food["Food and survival needs"]
  magic["Magic"]
  equip["Equipment and reforging"]
  char["Character and combat"]

  farm -->|"crops become fuel"| eng
  world -->|"island oil fields"| eng
  eng -->|"aircraft and contraptions"| travel
  eng -->|"harvesters and troughs"| farm
  farm -->|"crops and animal products"| food
  eng -->|"automated cooking"| food
  magic -->|"divination rod finds ore"| world
  travel -->|"reach distant islands"| world
  world -->|"found equipment"| equip
  eng -->|"tools and gear"| equip
  magic -->|"staves, spell books, rings"| equip
  equip -->|"worn rings and necklaces lend abilities"| magic
  magic -->|"prepared combat spells"| char
  char -->|"magic talents and spell learning"| magic
  magic -->|"temporary wings and local portals"| travel
  travel -->|"gliders and hooks"| equip
  equip -->|"better weapons and armor"| char

  food -.->|"stamina for sprinting, swimming, hooks, vines"| travel
  magic -.->|"help with machines"| eng
  eng -.->|"supplies for magic"| magic
```

Engineering sits at the centre: it powers flight, farming and kitchens and draws fuel from both crops and oil. Equipment from every direction can be reforged at an anvil, as [character and combat](../character-and-combat/) explains. Building and the [convenience features](../quality-of-life/) support all of these without depending on them.

## Food, farming and fuel

```mermaid
flowchart TB
  agri["AgriCraft crop breeding"]
  fd["Farmer's Delight crops and kitchen"]
  ah["Animal Husbandry livestock"]
  create["Create machines"]
  cfood["Create: Food, Delightful Creators"]
  diesel["Diesel Generators engines"]
  oil["Island oil fields"]
  nb["Nutritional Balance food groups"]
  lso["Legendary Survival Overhaul thirst"]

  agri -->|"breeds cabbage, onions, rice, tomatoes"| fd
  create -->|"harvests keeping genetics"| agri
  create -->|"pipes water to troughs"| ah
  fd -->|"rice, seeds and crops become plant oil and ethanol"| diesel
  oil -->|"refined fuel"| diesel
  diesel -->|"rotational power"| create
  create --> cfood
  cfood -->|"automated dishes"| nb
  fd -->|"varied meals"| nb
  ah -->|"meat, eggs, milk"| fd
  fd -.->|"drinks that quench thirst"| lso
```

A garden can start the first engines before you find oil. The [fuel guide](../engineering/fuel/) covers presses, fermentation and refining; [cooking and farming](../building-and-daily-life/food/) covers crop breeding, livestock, food groups and drinking water.

## Planned connections

The dashed arrows describe the intended direction, not a release schedule. Stamina from gliding is meant to become a shared resource for sprinting, swimming, hanging on a grappling hook and climbing vines, with food affecting it. Magic and engineering are meant to support each other, so a player studying both learns each more comfortably. Drinks from the kitchen are meant to quench thirst. Bred plants and discoveries during exploration are also meant to help in other systems; their arrows will appear on this map once concrete connections are chosen.
