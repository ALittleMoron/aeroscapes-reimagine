package dev.aeroscapes.enchanting;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class EquipmentCurses {
  public static final TagKey<Enchantment> SOURCED =
      TagKey.create(
          Registries.ENCHANTMENT,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "equipment_curses"));
  private static final int MANUFACTURE_ODDS = 200;

  private EquipmentCurses() {}

  public static void rollManufactured(
      ItemStack result, RegistryAccess registries, RandomSource random) {
    var curses = compatible(result, registries);
    if (!curses.isEmpty() && random.nextInt(MANUFACTURE_ODDS) == 0) {
      apply(result, curses, random);
    }
  }

  static List<Holder<Enchantment>> compatible(ItemStack item, RegistryAccess registries) {
    if (item.getCount() != 1 || !item.isDamageableItem() || CurseCleansing.isCursed(item)) {
      return List.of();
    }
    var effects = EnchantmentHelper.getEnchantmentsForCrafting(item);
    return registries.registryOrThrow(Registries.ENCHANTMENT).getTag(SOURCED).stream()
        .flatMap(tag -> tag.stream())
        .filter(item::supportsEnchantment)
        .filter(
            curse ->
                effects.keySet().stream()
                    .allMatch(effect -> Enchantment.areCompatible(effect, curse)))
        .toList();
  }

  static void apply(ItemStack item, List<Holder<Enchantment>> curses, RandomSource random) {
    item.enchant(curses.get(random.nextInt(curses.size())), 1);
  }

  static boolean hasUsefulEnchantment(ItemStack item) {
    return EnchantmentHelper.getEnchantmentsForCrafting(item).keySet().stream()
        .anyMatch(effect -> !effect.is(EnchantmentTags.CURSE));
  }
}
