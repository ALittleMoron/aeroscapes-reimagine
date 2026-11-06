Fairytale Sun and Moon

Original pixel artwork for Aeroscapes Reimagine, GPL-3.0-or-later.
Composition is AI-assisted deterministic SVG pixel placement, without third-party
image inputs or generative image models. The SVG files are the editable sources;
the PNGs are lossless renders with transparent margins and no smoothing.
The full license is ../licenses/celestial-artwork-GPL-3.0.txt.

Render from this resourcepack's root with librsvg's rsvg-convert:

rsvg-convert artwork/sun.svg -o assets/minecraft/textures/environment/sun.png
rsvg-convert artwork/moon_phases.svg -o assets/minecraft/textures/environment/moon_phases.png

The Sun canvas is 128x128. The Moon sheet is 384x192, with 96x96 cells in the
native Minecraft 1.21.1 order, left to right and then top to bottom:
full, waning gibbous, third quarter, waning crescent,
new, waxing crescent, first quarter, waxing gibbous.
The invisible new Moon has an empty SVG group. Each other named group masks the
same crater map; transparent pixels do not paint a dark disk over the sky.
