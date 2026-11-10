# Extended vanilla interface art

These original SVGs cover the beacon, enchanting table, horse and llama inventory,
loom, stonecutter, villager trades, book and lectern pages, recipe book, effect cards,
and game mode selector in Minecraft 1.21.1 with NeoForge 21.1.244.

Render each SVG at its declared dimensions with `rsvg-convert`. The PNG path relative
to `assets/minecraft/textures/gui/` mirrors the SVG path relative to this directory.
Container panels retain the pinned atlas dimensions and item slot positions. The
recipe book panel starts at (1, 1). Its popup retains the 32-pixel nine-slice
geometry with a four-pixel border. Keep the book pages light so native black
book text and its editing cursor are legible.

Vanilla draws enchanting costs, rune text, beacons' powers, book text and recipe
icons at runtime. The companion adapts the rune and unavailable level colors for
the corresponding Atlas option sprites and does not recolor the rendered items.
