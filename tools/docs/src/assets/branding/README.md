# Mysterious Islands title cover

The owner selected this cover on 2026-10-28 for the English/Russian documentation
homepages and language landing page, and authorized its use in the project
README on 2026-10-31. It combines a real owner screenshot with
existing font lettering, outlines, an offset shadow and two rules. No generative
image model was used. The SVG layout was written with AI assistance; that does
not establish an exception to Modrinth's AI-image policy. The Modrinth icon and
uploader inputs are unchanged.

`mysterious-islands.svg` is the editable source. It embeds a 1920 × 1200 JPEG at
quality 94, resized without cropping from `2026-10-28_13.58.40.png`. Its original
SHA-256 is `4f5adb704e6af6a9cad2479925df77c20fd819a79e7018065a48e2cf50a29f32`;
the [screenshot record](../screenshots/README.md) owns its capture provenance.
The embedded background keeps the SVG portable; the website serves only the
prebuilt WebP exports, not this source SVG.

The source uses Liberation Serif Bold for AEROSCAPES and Liberation Sans Bold
for REIMAGINE. The unmodified fonts are under SIL OFL 1.1; see
[`FONT-LICENSE.txt`](FONT-LICENSE.txt). Font files are not redistributed here.
Fontconfig resolves those family names at render time. For the same lettering,
use the original installed files, identified by these SHA-256 values:

| Font file                  | SHA-256                                                            |
| -------------------------- | ------------------------------------------------------------------ |
| `LiberationSerif-Bold.ttf` | `28f2d4300ee366d1ff9ca95df967a27e77987c87857fad0d9c85034405aae39d` |
| `LiberationSans-Bold.ttf`  | `769673c4355020b1e28a14c366a152da410ab6b16239fe883ebc35b73624835b` |

## Render after editing

These are manual authoring commands, not website build dependencies. Run from
`tools/docs/` with the fonts installed, librsvg (`rsvg-convert`) and ImageMagick.
The selected exports were made with ImageMagick 7.1.2-34.

```sh
rsvg-convert src/assets/branding/mysterious-islands.svg --output /tmp/aeroscapes-cover.png
magick /tmp/aeroscapes-cover.png -colorspace sRGB -strip -quality 88 -define webp:method=6 src/assets/branding/mysterious-islands.webp
magick /tmp/aeroscapes-cover.png -colorspace sRGB -resize 640x -strip -quality 86 -define webp:method=6 src/assets/branding/mysterious-islands-small.webp
```

The exports are 1920 × 1200 and 640 × 400. `PackCover.astro` serves both with
`srcset`, full-frame dimensions, early loading and localized alternative text.
The title cover sits in the page content after the text hero to avoid Starlight's
default square hero-image transformation. Original gallery photos retain their
own captions and contain no added lettering.
