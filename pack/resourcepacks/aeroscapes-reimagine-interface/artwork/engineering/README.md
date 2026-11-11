# Engineering interfaces

These original Island Atlas SVGs are vector artwork written with AI
assistance. Render each source at its declared dimensions with
`rsvg-convert`. The pinned Create Big Cannons 5.11.7 and Create: Connected
1.3.3 JARs provide the screen layout and texture address contracts.

| Source | Output under `assets/` |
| --- | --- |
| `cannon-controls.svg` | `createbigcannons/textures/gui/backgrounds1.png` |
| `creative-ammo.svg` | `createbigcannons/textures/gui/backgrounds2.png` |
| `casting-icons.svg` | `createbigcannons/textures/gui/jei_icons.png` |
| `gas-mask.svg` | `createbigcannons/textures/misc/gas_mask_overlay.png` |
| `pulse-sequencer.svg` | `create_connected/textures/gui/sequencer.png` |

Big Cannons uses 179 × 83 control panels and a separate 199 × 83 creative
panel. Keep each selector at the right of its panel and the two ammunition
slots at (32, 26) and (59, 26). Timed, delayed impact and delayed inertia
fuzes share the first panel. The creative ammunition header uses a pale
surface behind the provider's dark label. The gas mask overlay leaves
the aiming area transparent.

Connected draws its 256 × 205 base and replaces seven 237 × 22 rows with
state sprites drawn from the same texture. Keep the state crops at y16,
y104, y126, y148 and y206. The light title strip and darker rows suit
the different label colors drawn by the provider.
