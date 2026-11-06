package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldCarver.class)
public abstract class IslandCarverProtectionMixin {
  @WrapOperation(
      method = "carveEllipsoid",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carveBlock(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/levelgen/carver/CarverConfiguration;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/world/level/chunk/CarvingMask;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lnet/minecraft/world/level/levelgen/Aquifer;Lorg/apache/commons/lang3/mutable/MutableBoolean;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Wrap the virtual call, including modded overrides.
  private boolean aeroscapes$protectLiners(
      WorldCarver<?> carver,
      CarvingContext context,
      CarverConfiguration configuration,
      ChunkAccess target,
      Function<BlockPos, Holder<Biome>> biomes,
      CarvingMask mask,
      BlockPos.MutableBlockPos position,
      BlockPos.MutableBlockPos below,
      Aquifer aquifer,
      MutableBoolean reachedSurface,
      Operation<Boolean> original) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain != null) {
      int x = position.getX();
      int y = position.getY();
      int z = position.getZ();
      if (y <= configuration.lavaLevel.resolveY(context)
          || terrain.column(x, z).liner(y)
          || !((IslandCarvingContext) (Object) context)
              .aeroscapes$support(target)
              .solid(x, y - 4, z)) return false;
    }
    return original.call(
        carver,
        context,
        configuration,
        target,
        biomes,
        mask,
        position,
        below,
        aquifer,
        reachedSurface);
  }
}
