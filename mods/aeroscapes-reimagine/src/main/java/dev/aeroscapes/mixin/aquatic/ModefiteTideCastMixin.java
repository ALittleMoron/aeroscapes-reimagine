package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.client.TideItemModelProperties;
import com.li64.tide.registries.items.TideFishingRodItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "timmychips.modefiteitemdefinitions.property.resolver.condition.FishingRodCastBool",
    remap = false)
public abstract class ModefiteTideCastMixin {
  @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned property resolver.
  private void aeroscapes$activeRod(
      ItemStack stack,
      LivingEntity entity,
      @Coerce Object definition,
      CallbackInfoReturnable<Boolean> callback) {
    if (stack.getItem() instanceof TideFishingRodItem)
      callback.setReturnValue(
          TideItemModelProperties.CAST_FUNCTION.unclampedCall(stack, null, entity, 0) > 0);
  }
}
