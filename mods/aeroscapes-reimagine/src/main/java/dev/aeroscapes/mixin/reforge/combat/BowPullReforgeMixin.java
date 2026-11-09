package dev.aeroscapes.mixin.reforge.combat;

import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemProperties.class)
abstract class BowPullReforgeMixin {
  @ModifyArg(
      method = "<clinit>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/item/ItemProperties;register(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/item/ClampedItemPropertyFunction;)V",
              ordinal = 0),
      index = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ClampedItemPropertyFunction aeroscapes$bowPull(
      ClampedItemPropertyFunction original) {
    return (stack, level, entity, seed) ->
        original.unclampedCall(stack, level, entity, seed) / RangedReforges.bowDrawTime(stack);
  }
}
