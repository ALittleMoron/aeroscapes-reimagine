package dev.aeroscapes.atmosphere;

import java.util.Set;

public final class AdvancementCriterionTranslationsTest {
  private AdvancementCriterionTranslationsTest() {}

  public static void verify() {
    String adventure = "betteradvancements.criterion.minecraft:adventure/adventuring_time.";
    String food = "betteradvancements.criterion.minecraft:husbandry/balanced_diet.";
    String mobs = "betteradvancements.criterion.minecraft:adventure/kill_all_mobs.";
    Set<String> available =
        Set.of(
            "biome.minecraft.badlands",
            "biome.addon.new_island",
            "item.minecraft.chicken",
            "entity.minecraft.chicken",
            "block.addon.new_crop");
    require(
        AdvancementCriterionTranslations.resolve(
                adventure + "minecraft:badlands", "minecraft:badlands", available::contains)
            .equals("biome.minecraft.badlands"),
        "Static criterion aliases resolve through effective player text");
    require(
        AdvancementCriterionTranslations.resolve(
                adventure + "addon:new_island", "addon:new_island", available::contains)
            .equals("biome.addon.new_island"),
        "Runtime biome criteria use their exact native translation");
    require(
        AdvancementCriterionTranslations.resolve(food + "chicken", "chicken", available::contains)
            .equals("item.minecraft.chicken"),
        "Unnamespaced food criteria resolve as items");
    require(
        AdvancementCriterionTranslations.resolve(
                mobs + "minecraft:chicken", "minecraft:chicken", available::contains)
            .equals("entity.minecraft.chicken"),
        "Entity criteria do not use colliding food names");
    require(
        AdvancementCriterionTranslations.resolve(
                food + "addon:new_crop", "addon:new_crop", available::contains)
            .equals("block.addon.new_crop"),
        "Block items keep their native block names");
    String custom = adventure + "minecraft:badlands";
    require(
        AdvancementCriterionTranslations.resolve(
                custom, "minecraft:badlands", key -> key.equals(custom))
            .equals(custom),
        "Explicit provider and player criterion labels win");
    String unknown = "betteradvancements.criterion.addon:custom.chicken";
    require(
        AdvancementCriterionTranslations.resolve(unknown, "chicken", available::contains)
            .equals(unknown),
        "Unverified advancement domains retain native fallback");
    require(
        AdvancementCriterionTranslations.resolve(
                adventure + "Not an ID", "Not an ID", available::contains)
            .equals(adventure + "Not an ID"),
        "Non-resource criterion labels retain native fallback");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
