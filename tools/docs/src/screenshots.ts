import panorama from "./assets/screenshots/island-panorama.webp";
import panoramaThumb from "./assets/screenshots/island-panorama-thumb.webp";
import village from "./assets/screenshots/island-village.webp";
import villageThumb from "./assets/screenshots/island-village-thumb.webp";
import arch from "./assets/screenshots/sunlit-arch.webp";
import archThumb from "./assets/screenshots/sunlit-arch-thumb.webp";
import rocky from "./assets/screenshots/rocky-islands.webp";
import rockyThumb from "./assets/screenshots/rocky-islands-thumb.webp";
import vines from "./assets/screenshots/glowing-vines.webp";
import vinesThumb from "./assets/screenshots/glowing-vines-thumb.webp";
import sculk from "./assets/screenshots/sculk-islands.webp";
import sculkThumb from "./assets/screenshots/sculk-islands-thumb.webp";
import cherry from "./assets/screenshots/cherry-village.webp";
import cherryThumb from "./assets/screenshots/cherry-village-thumb.webp";
import cliffs from "./assets/screenshots/forested-cliffs.webp";
import cliffsThumb from "./assets/screenshots/forested-cliffs-thumb.webp";

export const screenshots = {
  panorama: {
    image: panorama,
    thumbnail: panoramaThumb,
    en: {
      alt: "Forested floating islands above clouds, with pink trees in the foreground.",
      caption: "Grassy, forested and autumn-colored islands above the clouds.",
    },
    ru: {
      alt: "Парящие лесные острова над облаками и розовые деревья на переднем плане.",
      caption: "Луга, леса и осенние краски на островах над облаками.",
    },
  },
  village: {
    image: village,
    thumbnail: villageThumb,
    en: {
      alt: "Village houses on a grassy floating island above a stone well and a gap in the terrain.",
      caption: "A village among grassy ledges and open gaps between islands.",
    },
    ru: {
      alt: "Деревенские дома на травянистом парящем острове, ниже — каменный колодец и разрыв в рельефе.",
      caption:
        "Деревня среди травянистых уступов и промежутков между островами.",
    },
  },
  arch: {
    image: arch,
    thumbnail: archThumb,
    en: {
      alt: "Sunlight through an opening in a huge floating rock formation with glowing vines underneath.",
      caption:
        "Sunlight reaches through an opening in the rock above the void.",
    },
    ru: {
      alt: "Солнце в проёме огромной парящей скалы со светящимися лианами на нижней стороне.",
      caption: "Солнечный свет проходит сквозь проём в скале над пустотой.",
    },
  },
  rocky: {
    image: rocky,
    thumbnail: rockyThumb,
    en: {
      alt: "Jagged floating rock islands above red and yellow trees, with sculk in the foreground.",
      caption:
        "Rocky heights, autumn trees and sculk share the same landscape.",
    },
    ru: {
      alt: "Зубчатые скалистые острова над красными и жёлтыми деревьями, на переднем плане — скалк.",
      caption: "Скалистые высоты, осенние деревья и скалк в одном ландшафте.",
    },
  },
  vines: {
    image: vines,
    thumbnail: vinesThumb,
    en: {
      alt: "Glowing yellow berries and hanging vines beneath a dark floating island.",
      caption: "Glowing vines along the underside of an island.",
    },
    ru: {
      alt: "Светящиеся жёлтые ягоды и свисающие лианы под тёмным парящим островом.",
      caption: "Светящиеся лианы на нижней стороне острова.",
    },
  },
  sculk: {
    image: sculk,
    thumbnail: sculkThumb,
    en: {
      alt: "An activated sculk sensor on a sculk-covered ledge, with suspended islands and pointed rock formations beyond.",
      caption: "An activated sculk sensor beneath the floating islands.",
    },
    ru: {
      alt: "Активированный скалк-сенсор на покрытом скалком уступе, за ним — парящие острова и острые скалы.",
      caption: "Активированный скалк-сенсор под парящими островами.",
    },
  },
  cherry: {
    image: cherry,
    thumbnail: cherryThumb,
    en: {
      alt: "A village house and stone well among pink flowering trees on a small island.",
      caption: "A village surrounded by pink flowering trees.",
    },
    ru: {
      alt: "Деревенский дом и каменный колодец среди розовых цветущих деревьев на небольшом острове.",
      caption: "Деревня в окружении розовых цветущих деревьев.",
    },
  },
  cliffs: {
    image: cliffs,
    thumbnail: cliffsThumb,
    en: {
      alt: "Forests and pink trees along a broad island with steep, uneven stone cliffs.",
      caption: "Forested slopes and exposed stone ledges on a broad island.",
    },
    ru: {
      alt: "Леса и розовые деревья на широком острове с крутыми неровными каменными склонами.",
      caption: "Лесистые склоны и открытые каменные уступы большого острова.",
    },
  },
};

export type ScreenshotId = keyof typeof screenshots;
