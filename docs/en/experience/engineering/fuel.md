---
title: Engines, fuel and oil
description: Start with crops, then prospect finite island oil fields and refine fuel for engines and burners.
status: available
---

[Create: Diesel Generators](https://modrinth.com/mod/create-diesel-generators/version/UoPH8lO1) adds compact, modular and huge engines, crop-based fuel and oil refining. A farm can supply the first engines; finding oil creates another reason to explore and build fuel transport. Both routes remain useful.

## Start with biofuel

Press seeds in a basin to obtain plant oil. When using rice, set the basin output filter to plant oil: an unfiltered batch of nine rice can instead be compressed into a rice bag. Fermentation turns suitable crops and bone meal into ethanol. Mixing 100 mB of plant oil with 100 mB of ethanol produces 200 mB of biodiesel. EMI lists the accepted seeds, fermentable ingredients and apparatus; the mod's Ponder scenes explain the assemblies.

Plant oil and ethanol run engines directly. Biodiesel provides more mechanical power for the same fluid consumption. A normal engine produces 2048 SU on plant oil or ethanol, 4096 SU on biodiesel or gasoline, and 6144 SU on diesel. Plant oil runs it at 74 rpm; the other fuels at 96 rpm. Modular engines combine sections, while the huge engine provides a larger single-machine alternative.

## Find an oil field

Use an Oil Scanner while above the area you want to investigate. It checks the chunk you occupy **when the scan finishes**, including the remaining reserve after extraction. Then inspect the island from below for dark oil stains on the surrounding stone's texture and run the well's vertical pipe down to one of those blocks. Oil-bearing rock can be mined, but never drops, even with Silk Touch, and cannot be relocated. Removing the last oil-bearing block in a chunk permanently clears its remaining reserve; scanners and wells then report zero.

Each oil-bearing chunk starts with 48–96 buckets of crude oil. All wells in that chunk draw from the same reserve. More wells increase extraction capacity; they do not multiply the amount of oil. Once exhausted, a field stays empty after saving and reloading, although its rock remains visible. In worlds created before this addition, island markers appear in newly generated chunks. Native Nether wells can also tap finite reserves through bedrock.

Ordinary distillation turns 100 mB of crude oil into 50 mB of diesel and 50 mB of gasoline. Superheated distillation yields 75 mB of each. Crude oil itself does not fuel ordinary Blaze Burners.

At full throttle, a normal or huge engine consumes 1 mB/s; a modular engine consumes that amount per section. With ordinary refining and both products used, an average field supplies one engine for about 20 hours or four engines for about five hours. Throttle, upgrades, section count and superheated refining change that duration. Tanks, barrels and transport let extraction and engine use happen at different times.

## Supply ordinary Blaze Burners

[Create: Liquid Fuel](https://modrinth.com/mod/create-liquid-fuel/version/rLn2bDVE) accepts these fuels through pipes or fluid containers. Each paid quantum consumes 1 mB; the tank holds 1000 mB. The table gives the purchased burn-time budget per bucket.

| Fuel      | Burn time per bucket |
| --------- | -------------------- |
| Plant oil | 8 min 20 s           |
| Ethanol   | 10 min               |
| Biodiesel | 16 min 40 s          |
| Gasoline  | 20 min               |
| Diesel    | 25 min               |
| Lava      | 16 min 40 s          |

These fluids provide ordinary heat. A Blaze Cake supplies superheat; ordinary liquid fuel waits while that special fuel is active. A full tank or incompatible fluid rejects a transfer. Buckets return empty only after a whole bucket transfers; a canister can transfer part of its contents and retain the rest. A huge engine's small tank needs pipes or a divisible container rather than a bucket.

## Hand tools and chemical equipment

The hammer processes an ingredient held in the opposite hand; wire cutters provide a manual route from a steel, zinc, netherite or bronze sheet to two matching wires. Both tools can be reforged with iron for processing speed and reduced wear. Sprayer reforging improves focus; scanner reforging improves readings and, at Legendary quality, points toward a physical target in the detected chunk. EMI shows each grade and its cost.

Chemical sprayers, the incendiary sprayer, the lighter and the chemical turret retain their native fluid effects. A successful sprayer pulse costs 1 mB; a turret shot costs 3 mB, including short bursts. An empty lighter cannot ignite blocks. The lighter, track layer bag and canister keep their ordinary utility and container roles without reforging.
