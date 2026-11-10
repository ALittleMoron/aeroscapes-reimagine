# Tom’s Simple Storage Mod controls

The SVG sheet contains original Atlas artwork for the GUI sprite IDs in
[Tom’s Simple Storage Mod 2.4.2](https://modrinth.com/mod/toms-storage/version/WQAMoIMC).
The pinned JAR supplies the position, size and state contract. These controls
keep the clear and scroll indicators and distinguish disabled, hovered and
selected states. The small list buttons use dark fills for the provider’s
white labels, and the square icon buttons use lighter fills behind item and
status icons.

Rasterize `toms_storage_widgets.svg` with `rsvg-convert` at its native size.
Crop each group from x=0 and the y coordinate in its `translate` attribute.
Take the width and height from the corresponding sprite or add one pixel to
the first rectangle’s width and height. Use the group ID for the PNG filename
and keep the eight two-pixel nine-slice definitions beside their PNGs.
