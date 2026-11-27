package dev.aeroscapes.races;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum Profession {
  BLACKSMITH("smithing", "material_repair", "frugal_repair"),
  BUILDER("building", "long_reach", "quick_dismantling"),
  FARMER("farming", "extra_harvest", "careful_tilling"),
  COOK("cooking", "hearty_meal", "quick_meal");

  private final String practiceSkill;
  private final List<String> starters;

  Profession(String practiceSkill, String... starters) {
    this.practiceSkill = practiceSkill;
    this.starters = List.of(starters);
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public String practiceSkill() {
    return practiceSkill;
  }

  public List<String> starters() {
    return starters;
  }

  public List<ItemStack> kit() {
    return switch (this) {
      case BLACKSMITH -> List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COAL, 4));
      case BUILDER -> List.of(new ItemStack(Items.STONE_AXE), new ItemStack(Items.COBBLESTONE, 16));
      case FARMER -> List.of(new ItemStack(Items.STONE_HOE), new ItemStack(Items.WHEAT_SEEDS, 6));
      case COOK ->
          List.of(
              new ItemStack(Items.BOWL, 2),
              new ItemStack(Items.RED_MUSHROOM),
              new ItemStack(Items.BROWN_MUSHROOM),
              new ItemStack(
                  BuiltInRegistries.ITEM
                      .getOptional(ResourceLocation.parse("farmersdelight:flint_knife"))
                      .orElseThrow()));
    };
  }

  @Nullable
  public static Profession byId(String id) {
    return switch (id) {
      case "blacksmith" -> BLACKSMITH;
      case "builder" -> BUILDER;
      case "farmer" -> FARMER;
      case "cook" -> COOK;
      default -> null;
    };
  }
}
