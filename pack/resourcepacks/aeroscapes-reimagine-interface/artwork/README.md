# Island Atlas artwork

These SVGs are the editable sources for the pack's menu, loading screen, widgets and inventory.
The artwork is original vector composition written with AI assistance. The colors and
Liberation Serif / Liberation Sans lettering follow the project's documentation site.
Rendering uses the installed fonts under SIL OFL 1.1. The pack distributes the artwork
as raster textures.

Render the SVG files with `rsvg-convert`. Keep the specified canvas sizes and transparent
padding. The inventory uses Minecraft 1.21.1's 176 × 166 logical panel in a 256 × 256 atlas.
The three widget textures use 200 × 20 canvases with three-pixel nine-slice borders.

Render `splash.svg` to a temporary PNG and convert its 960 × 240 horizontal canvas into the
two-band layout expected by both loading overlays:

```sh
rsvg-convert splash.svg --output /tmp/aeroscapes-splash.png
magick /tmp/aeroscapes-splash.png -crop 480x240 +repage -append PNG32:../assets/aeroscapes_reimagine/textures/gui/interface/splash.png
```

The original container panels under `containers/` use Minecraft 1.21.1's slot positions
and texture dimensions. Each station SVG renders to the corresponding 256 × 256 PNG in
`assets/minecraft/textures/gui/container/`. The chest atlas uses a variable-height top
strip and a shared inventory strip beginning at y126. Furnace, blast furnace and smoker
share the same panel. The three creative tab panels go in `container/creative_inventory/`.
The four creative tab state SVGs each supply seven numbered 26 × 32 sprites under
`gui/sprites/container/creative_inventory/`. The scroller sources render to the same
sprite directory. [The EMI atlas](emi.md) has separate provenance and render instructions.

The reforging source preserves Hero’s Tiered’s 176 × 166 panel, its 18 × 18 button states
at x176, x194 and x212, the occupied-slot tile at (8,84) and the transparent unique-item
indicator at (0,166). Render it into `assets/tieredneo/textures/gui/reforging_screen.png`.

The pinned third-party panel index under `assets/aeroscapes_reimagine/atlas/panels.json`
contains SHA-256 digests of the installed providers’ PNG bytes. It ships no copies of
those PNGs. The companion mod substitutes a few neutral panel and slot colors at decode
time, leaving alpha and colored states intact. Chipped’s workbench and the Iron’s Lib
transmog table also have verified replacements for their painted panel fills. Keep the light panel surface where the
provider draws dark text. When updating an entry, verify the actual screen’s asset path,
all cropped regions, dynamic states, label contrast and the new provider file digest.
