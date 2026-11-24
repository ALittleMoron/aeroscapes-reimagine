package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.enchanting.EquipmentCurses;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantRandomlyFunction.class)
abstract class RandomLootEnchantmentMixin {
  @ModifyExpressionValue(
      method = "run",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/Optional;orElseGet(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Object aeroscapes$excludeEquipmentCurses(Object pool) {
    // Unfiltered pools include the whole registry or tags such as the urn's vanilla curse pool.
    @SuppressWarnings("unchecked")
    var enchantments = (Stream<Holder<Enchantment>>) pool;
    return enchantments.filter(effect -> !effect.is(EquipmentCurses.SOURCED));
  }
}
