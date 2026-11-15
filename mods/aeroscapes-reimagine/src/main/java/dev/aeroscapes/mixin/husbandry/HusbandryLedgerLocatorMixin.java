package dev.aeroscapes.mixin.husbandry;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import org.athisworkshop.animalhusbandry.client.gui.FarmLedgerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FarmLedgerScreen.class, remap = false)
abstract class HusbandryLedgerLocatorMixin {
  @ModifyExpressionValue(
      method = "buildDetailsPage",
      at =
          @At(
              value = "FIELD",
              ordinal = 0,
              target =
                  "Lorg/athisworkshop/animalhusbandry/client/gui/FarmLedgerScreen;targetEntity:Lnet/minecraft/world/entity/LivingEntity;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private LivingEntity aeroscapes$hideLocator(LivingEntity original) {
    return null;
  }
}
