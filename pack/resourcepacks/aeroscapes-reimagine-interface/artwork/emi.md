# EMI textures

The resource pack recolors three 256 × 256 atlases from the pinned
[EMI 1.1.24 NeoForge artifact](https://modrinth.com/mod/emi/version/5sIPA1To).
The [original MIT notice](../licenses/EMI-MIT.txt) applies. The source PNGs are
the entries with the same names under `assets/emi/textures/gui/` in the JAR
pinned by `pack/mods/emi.pw.toml`.

| Atlas | Original SHA-256 |
| --- | --- |
| `background.png` | `3365660160e0f4d2d62c2de8a235955eea6096a795f7e4125de45ed9debe06d2` |
| `buttons.png` | `70ea91ad846634b9739fca65d1836212c5a778f9262e972fe6e447416d770dbf` |
| `widgets.png` | `d02436316cedde891f159ba304fc1b40afc4418adb1779816675e7b0bff70872` |

Use exact palette replacement on opaque gray pixels. Keep the UV regions,
alpha masks, sprite geometry and colored status pixels when updating. EMI
builds the window and tabs from nine-sliced patches. The recipe card at
`(27, 0)` uses a light slate surface for dark text drawn by EMI recipes
and their add-ons. The recipes' page and category labels use a dark panel
because EMI draws them in white. The amber corner accents sit outside the
stretched center of the window patch.

The widget atlas matches the recipe-card fill around the arrows and keeps
the checkerboard for ghost slots. The button atlas distinguishes focused,
active and disabled rows. The focused border is amber and the colored
favorite and transfer indicators retain their original colors.
