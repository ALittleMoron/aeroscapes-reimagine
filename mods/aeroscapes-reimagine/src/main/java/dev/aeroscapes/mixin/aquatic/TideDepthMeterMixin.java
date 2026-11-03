package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.worldgen.BasinWaterline;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.li64.tide.registries.items.DepthMeterItem", remap = false)
public abstract class TideDepthMeterMixin {
  @ModifyExpressionValue(
      method = "getResult",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // HUD samples use the same physical basin surface.
  private int aeroscapes$localDepth(int nativeSea, ServerLevel level, ServerPlayer player) {
    var position = player.blockPosition();
    return BasinWaterline.at(
        level.getChunkSource().randomState(), position.getX(), position.getZ(), nativeSea);
  }
}
