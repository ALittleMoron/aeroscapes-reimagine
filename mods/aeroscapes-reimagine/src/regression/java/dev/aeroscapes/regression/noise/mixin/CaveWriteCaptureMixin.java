package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.regression.CaveNetworkRegression;
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
public abstract class CaveWriteCaptureMixin {
  @WrapOperation(
      method = "carveEllipsoid",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/carver/WorldCarver;carveBlock(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/levelgen/carver/CarverConfiguration;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/world/level/chunk/CarvingMask;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lnet/minecraft/world/level/levelgen/Aquifer;Lorg/apache/commons/lang3/mutable/MutableBoolean;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Distinguishes actual carving from preexisting holes.
  private boolean aeroscapes$captureCarvedAir(
      WorldCarver<?> carver,
      CarvingContext context,
      CarverConfiguration configuration,
      ChunkAccess chunk,
      Function<BlockPos, Holder<Biome>> biomes,
      CarvingMask mask,
      BlockPos.MutableBlockPos position,
      BlockPos.MutableBlockPos below,
      Aquifer aquifer,
      MutableBoolean reachedSurface,
      Operation<Boolean> original) {
    boolean solid =
        CaveNetworkRegression.tracking(context, position) && !chunk.getBlockState(position).isAir();
    boolean result =
        original.call(
            carver,
            context,
            configuration,
            chunk,
            biomes,
            mask,
            position,
            below,
            aquifer,
            reachedSurface);
    if (solid && result && chunk.getBlockState(position).isAir())
      CaveNetworkRegression.carved(context, position);
    return result;
  }
}
