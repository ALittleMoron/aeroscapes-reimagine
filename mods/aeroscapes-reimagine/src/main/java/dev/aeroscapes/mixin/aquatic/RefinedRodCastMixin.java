package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.aquatic.RefinedRodCastCleanup;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBakery.class)
public abstract class RefinedRodCastMixin {
  @Inject(method = "loadBlockModel", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Runs before the loaded geometry is baked.
  private void aeroscapes$deployedTackle(
      ResourceLocation location, CallbackInfoReturnable<BlockModel> callback) {
    BlockModel model = callback.getReturnValue();
    if (model.customData.hasCustomGeometry()) return;
    RefinedRodCastCleanup.removeDecoration(
        location, ((BlockModelElementsAccess) model).aeroscapes$elements());
  }
}
