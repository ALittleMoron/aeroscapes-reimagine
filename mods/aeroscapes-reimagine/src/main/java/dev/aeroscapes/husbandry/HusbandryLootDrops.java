package dev.aeroscapes.husbandry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;

/** Direct trait rewards use the same real furnace recipes as ordinary cooked loot. */
public final class HusbandryLootDrops {
  private static final TagKey<DamageType> FIRE_LOOT =
      TagKey.create(
          Registries.DAMAGE_TYPE,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "fire_loot"));

  private HusbandryLootDrops() {}

  public static ItemEntity spawn(
      LivingEntity animal, ItemLike item, int count, DamageSource source) {
    ItemStack stack = new ItemStack(item, count);
    if (stack.has(DataComponents.FOOD)
        && (animal.isOnFire() || source.is(FIRE_LOOT))
        && animal.level() instanceof ServerLevel level) {
      var input = new SingleRecipeInput(stack);
      var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
      if (recipe.isPresent()) {
        ItemStack cooked = recipe.get().value().assemble(input, level.registryAccess());
        if (!cooked.isEmpty()) {
          cooked.setCount(cooked.getCount() * count);
          stack = cooked;
        }
      }
    }
    return animal.spawnAtLocation(stack);
  }
}
