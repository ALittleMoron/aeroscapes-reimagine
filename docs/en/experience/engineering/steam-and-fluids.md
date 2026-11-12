---
title: Steam, controls and fluid delivery
description: Build a separate steam plant, control production and order fluids through Create logistics.
status: available
---

[Create: Steam Powered](https://modrinth.com/mod/create-steam-powered/version/w3zLn2Kv) turns steam into a fluid that can be stored and transported. A boiler, a pipe network and engines with matching flywheels form a separate power plant. Create's ordinary tank boilers remain available alongside it.

## Build a steam plant

Supply water to a boiler and place a burning chamber or a Blaze Burner underneath. The boiler accepts water and exposes steam for extraction. Pump that steam into a tank, then pipe it to the engines. Each engine needs its matching flywheel two blocks in front, with the flywheel axle perpendicular to the engine. Ponder shows the arrangement.

The native Bronze family accepts brass ingots and sheets; the Cast Iron family uses ordinary iron ingots and sheets. Steel equipment accepts the ingots and sheets from the [foundry](../mods/#mechanical-processing-and-foundry). A separate bronze or cast-iron production chain is unnecessary. EMI shows the actual accepted materials.

| Equipment family | Full boiler output | Engine consumption | Mechanical capacity at 32 RPM |
| ---------------- | ------------------ | ------------------ | ----------------------------- |
| Bronze           | 12 mB/t            | 12 mB/t            | 16,384 SU                     |
| Cast Iron        | 24 mB/t            | 24 mB/t            | 32,768 SU                     |
| Steel            | 48 mB/t            | 48 mB/t            | 65,536 SU                     |

A matching burning chamber can supply the full boiler output. An ordinary Blaze Burner supplies 75%; superheat reaches the tier limit. Passive heat supplies no steam, and weaker external heaters produce proportionally less. The [liquid fuels](../fuel/#supply-ordinary-blaze-burners) from the fuel system provide ordinary heat; Blaze Cakes retain their superheat role. One ordinarily heated boiler therefore cannot continuously feed an equally tiered engine at full consumption: add boilers, distribute steam or use stored reserves.

The configured conversion is up to 12 mB of steam per mB of water, with whole-mB water debits. A full boiler vents surplus steam. Engines need an initial steam reserve and warm up before delivering power. An engine drawing directly from a boiler has 70% of its normal capacity; piping steam into the engine's own tank provides full capacity. A connected engine consumes steam even without useful shaft load. Steam canisters hold 1,000 mB and return the empty container when emptied. Refilling an engine from a canister requires space for the full 1,000 mB.

## Control production

[Create: Extra Gauges](https://modrinth.com/mod/extra-gauges/version/xtIBXji9) adds counters, numerical comparisons, logic and string processing to factory panels. For example, count completed production pulses, compare the count with a target and use the result to stop an input clutch or light a signal. A counter advances once per rising edge; holding the input high does not count repeatedly. After reaching its configured threshold, the next pulse resets its count.

Use the existing RSGauges switches, timers and signal displays for physical control panels. Extra Gauges' ports connect those ordinary redstone signals with the panel logic. Manual switches and simple threshold controls remain useful without expressions or advanced logic. `True`, `False` and example expressions are technical string data used by the native conversion rules.

## Deliver fluids by order

[Create: FluidLogistics](https://modrinth.com/mod/createfluidlogistic/version/HiLD5edv) connects fluid packages to Create's stock links, factory gauges, addresses and delivery routes. Attach a Fluid Packager to a fluid inventory, connect it to the logistics network and request a fluid at the destination. Use the native receiving Fluid Packager or Unpackager to feed the destination inventory. The Unpackager can serve machinery with mixed item and fluid inputs.

The logistics system recognizes the pack's molten metals, crop and petroleum fuels, steam and other registered fluids. The receiving machine still decides which fluids it accepts: a boiler needs water, a steam engine needs steam and a diesel engine needs suitable fuel. Packages carry up to 10,000 mB. Orders and machine transfers retain exact mB quantities, including a foundry ingot's 90 mB; a rejected or incomplete delivery retains its remaining cargo. A copper bucket holds 5,000 mB, and retains exact partial transfers.

The Infinite Fluid Tank accepts **water and lava only**, using Create's native bottomless threshold. It rejects oil, derived fuels, molten metals and steam. Store and deliver those fluids with ordinary or multi-fluid tanks. Moving a fuel package or placing a filled tank does not make a finite oil field inexhaustible.

The Hand Pointer configures supported machines and logistics connections. Its native model and configuration modes remain available; reforging with brass uses the same handling and alignment effects as other building tools. Fluid containers remain containers rather than reforged equipment.
