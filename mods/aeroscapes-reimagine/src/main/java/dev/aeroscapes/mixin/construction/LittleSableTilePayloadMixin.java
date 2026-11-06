package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.LittleSablePayloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.littletiles.common.block.entity.BETiles;

@Pseudo
@Mixin(targets = "team.creative.littletiles.common.block.entity.BETiles", remap = false)
public abstract class LittleSableTilePayloadMixin {
  @Inject(
      method = {
        "setLevel(Lnet/minecraft/world/level/Level;)V",
        "loadAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
        "updateTiles(ZZ)V"
      },
      at = @At("RETURN"),
      require = 3)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Payload changes can retain the same Minecraft BlockState.
  private void aeroscapes$payloadChanged(CallbackInfo callback) {
    LittleSablePayloads.changed((BETiles) (Object) this);
  }
}
