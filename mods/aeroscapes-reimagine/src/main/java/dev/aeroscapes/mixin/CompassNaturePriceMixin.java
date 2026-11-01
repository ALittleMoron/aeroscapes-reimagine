package dev.aeroscapes.mixin;

import com.chaosthedude.naturescompass.util.BiomeUtils;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The owned range selector supplies the only price; native sync and entry gates stay free. */
@Mixin(value = BiomeUtils.class, remap = false)
public abstract class CompassNaturePriceMixin {
  @Inject(method = "getXpLevelsForBiome", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact native price getter hook.
  private static void aeroscapes$noNativePrice(
      ResourceLocation key, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(0);
  }
}
