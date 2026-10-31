# Pack screenshots

The project owner captured these screenshots and authorized their use in the
project README, website and gallery on 2026-10-28 (the initial eight images)
and 2026-10-31 (eight additional images). They are actual pack images,
not illustrations. The screenshots include Minecraft and third-party mod artwork;
this record does not claim ownership of those underlying assets. See the public
credits for the included projects.

The original PNGs are 2880 × 1800. Source basenames and SHA-256 hashes identify
them without making the website depend on an external profile directory.
The capture build, shader pack and shader preset have not been established;
the profile directory name is not evidence of those settings.

## Processing

ImageMagick 7.1.2-34, using its WebP delegate, produced 1920 × 1200 full images
and 640 × 400 thumbnails. Both preserve the whole frame. Only sRGB conversion,
resizing, metadata stripping and WebP compression were applied; there is no
retouching, scene compositing, added text or generative image processing.
Original PNGs remain with the owner and are not copied into Git.

For each source, run these commands from the repository root, substituting the
input path and the basename from the table:

```sh
magick INPUT.png -colorspace sRGB -resize 1920x -strip -quality 82 -define webp:method=6 tools/docs/src/assets/screenshots/NAME.webp
magick INPUT.png -colorspace sRGB -resize 640x -strip -quality 78 -define webp:method=6 tools/docs/src/assets/screenshots/NAME-thumb.webp
```

The screenshot component imports these files as asset URLs without requesting
another image transformation. Its `srcset` lets the browser select either size;
links open the full image. The world overview uses the full panorama.
The project README and documentation homepages use a separate
[title cover](../branding/README.md), composed from the sunlit-arch screenshot.
Captions and alternative text are maintained together in `src/screenshots.ts`.

## Sources

| Basename            | Original PNG              | Source SHA-256                                                     |
| ------------------- | ------------------------- | ------------------------------------------------------------------ |
| `island-panorama`   | `2026-10-28_14.12.39.png` | `80b1daf0f5567998e86f651a72887bd4cd0b9ca3ff85dfe618076dc3debe177d` |
| `island-village`    | `2026-10-28_13.57.44.png` | `958db13d7b15a1021226a4b3892153d9b5654723f125f4cfb38551ca35570669` |
| `sunlit-arch`       | `2026-10-28_13.58.40.png` | `4f5adb704e6af6a9cad2479925df77c20fd819a79e7018065a48e2cf50a29f32` |
| `rocky-islands`     | `2026-10-28_14.01.33.png` | `fd2881b420539b629934f94f69813e71fd9b0f1e90a8f75090e9f2a3c06420be` |
| `glowing-vines`     | `2026-10-28_14.07.53.png` | `f12583d9afba7d53d33d57503866a72f44197250f70ba4baec41a880bf45230f` |
| `sculk-islands`     | `2026-10-28_14.11.18.png` | `c519a9b53f7b497b7665a3d6729c334c8cc962576f95aa8625d1ca0f1fd82641` |
| `cherry-village`    | `2026-10-28_14.12.50.png` | `fbec5e6c2f0c46977664bcfed81bf30cbfbe3bbbf8a2ef50dd6390f67edb8bd5` |
| `forested-cliffs`   | `2026-10-28_14.13.23.png` | `f2249aa439858198b42dab04fbf2c41f068e83cee9eb5cb542996cb595d48dcc` |
| `cliffside-house`   | `2026-10-31_15.47.38.png` | `5eac4042120ab6ceb15db7454d20c5bb2151f7162eeb872d066f28fb23b52d82` |
| `waterfall-grove`   | `2026-10-31_15.47.57.png` | `c39db1f87b2e14f33de7f39aea4be2339946449a3388e27b8c1d806799e88994` |
| `sandstone-village` | `2026-10-31_15.48.40.png` | `e6526bd2c5824ceafc42e834c5b00c3daa9fb2c4dffbd354b73f845e10c6a5ee` |
| `misty-wetlands`    | `2026-10-31_15.48.50.png` | `da69cc1b2dc7119b27a1aad90647628063d498752bed3632b1094ea7c8a950d7` |
| `lava-arch`         | `2026-10-31_15.49.31.png` | `4754420f99e3c551235280e857fe23d70dc13e93491db063b0c2f6c0d36e9cbc` |
| `glowing-island`    | `2026-10-31_15.49.42.png` | `de0e0ce2f595d39bf380ab7cb49c7d37f4c59cbe0c96512f621a254002aa2fb2` |
| `desert-tower`      | `2026-10-31_15.50.02.png` | `4fedbb470bda7f689b2ba381e17b0659c8444e338660388882fac251ab74ea13` |
| `pale-cliffs`       | `2026-10-31_15.50.48.png` | `45ccddb1ee976b3bb143cd8a76c359cbdfaa899204a7ce9834798bcd12cb321a` |
