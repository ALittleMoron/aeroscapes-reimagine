package dev.aeroscapes.mixin.engineering.fuel;

import com.forsteri.createliquidfuel.core.BurnerStomachHandler;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.aeroscapes.engineering.fuel.ConservedFuel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BurnerStomachHandler.class, remap = false)
public abstract class LiquidFuelAccountingMixin {
  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$creditPaidFuel(SmartBlockEntity entity, CallbackInfo callback) {
    if (entity instanceof BlazeBurnerBlockEntity burner) ConservedFuel.tick(burner);
    callback.cancel();
  }

  @Inject(method = "tryUpdateFuel", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$disableCopyOnlyInsertion(
      SmartBlockEntity entity,
      ItemStack stack,
      boolean overflow,
      boolean simulate,
      CallbackInfoReturnable<Boolean> original,
      CallbackInfo callback) {
    callback.cancel();
  }
}
