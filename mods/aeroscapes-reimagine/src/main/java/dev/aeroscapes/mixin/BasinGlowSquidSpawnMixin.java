package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlowSquid.class)
public abstract class BasinGlowSquidSpawnMixin {
  @ModifyExpressionValue(
      method = "checkGlowSquidSpawnRules",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/ServerLevelAccessor;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned native deep-water glow-squid predicate.
  private static int aeroscapesReimagine$basinDepth(
      int nativeSea,
      @Local(argsOnly = true) ServerLevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return BasinSeaLevel.water(nativeSea, level, position);
  }
}
