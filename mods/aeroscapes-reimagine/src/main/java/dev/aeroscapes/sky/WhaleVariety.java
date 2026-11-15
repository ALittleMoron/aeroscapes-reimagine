package dev.aeroscapes.sky;

import net.minecraft.resources.ResourceLocation;

/** Saved ecological forms share native movement/combat and select their ecological presentation. */
public enum WhaleVariety {
  STONE,
  MOSS,
  ROOTED,
  FUNGAL,
  GRANITE,
  CHALK,
  DRIPSTONE,
  BASALT,
  LAVA,
  SANDSTONE,
  REEF,
  SNOW,
  CLOUD;

  public String id() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public ResourceLocation texture() {
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", "textures/entity/whales/" + id() + ".png");
  }

  public ResourceLocation model() {
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", "geo/whales/" + id() + ".geo.json");
  }

  public ResourceLocation animation() {
    String family =
        switch (this) {
          case CLOUD, SNOW -> "cloud";
          case REEF, MOSS, ROOTED, FUNGAL -> "reef";
          default -> "mineral";
        };
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", "animations/whales/" + family + ".animation.json");
  }

  public static WhaleVariety fromIndex(int index) {
    var varieties = values();
    return index >= 0 && index < varieties.length ? varieties[index] : STONE;
  }

  public static WhaleVariety fromId(String id) {
    for (var variety : values()) if (variety.id().equals(id)) return variety;
    return STONE;
  }
}
