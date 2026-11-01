package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.util.StructureUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructureUtils.class, remap = false)
public abstract class CompassExplorerPriceMixin {
  @Inject(method = "getXpLevelsForStructure", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact native price getter hook.
  private static void aeroscapes$noNativePrice(
      ServerLevel level, ResourceLocation key, CallbackInfoReturnable<Integer> callback) {
    callback.setReturnValue(0);
  }
}
