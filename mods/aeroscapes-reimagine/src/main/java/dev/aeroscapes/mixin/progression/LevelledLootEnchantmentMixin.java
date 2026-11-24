package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.enchanting.EquipmentCurses;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantmentHelper.class)
abstract class LevelledLootEnchantmentMixin {
  @ModifyExpressionValue(
      method =
          "enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/RegistryAccess;Ljava/util/Optional;)Lnet/minecraft/world/item/ItemStack;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/Optional;orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Object aeroscapes$excludeEquipmentCurses(Object pool) {
    @SuppressWarnings("unchecked")
    var enchantments = (Stream<Holder<Enchantment>>) pool;
    return enchantments.filter(effect -> !effect.is(EquipmentCurses.SOURCED));
  }
}
