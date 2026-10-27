package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaterAnimal.class)
public abstract class BasinAnimalSpawnMixin {
  @ModifyExpressionValue(
      method = "checkSurfaceWaterAnimalSpawnRules",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned fish/dolphin/squid shared spawn predicate.
  private static int aeroscapesReimagine$basinWaterline(
      int nativeSea,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    if (!(level instanceof ServerLevelAccessor server)) return nativeSea;
    IslandTerrain terrain = IslandTerrain.from(server.getLevel().getChunkSource().randomState());
    if (terrain == null) return nativeSea;
    var basin = terrain.basin(position.getX(), position.getZ());
    return basin != null && basin.water(position.getX(), position.getY(), position.getZ())
        ? basin.waterline()
        : nativeSea;
  }
}
