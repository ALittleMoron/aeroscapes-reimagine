package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.SeabedProjection;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JigsawPlacement.class)
public abstract class SpawnSeabedProjectionMixin {
  @WrapOperation(
      method =
          "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Active only while assembling the owned seabed wrapper.
  private static int aeroscapes$physicalProjection(
      ChunkGenerator generator,
      int x,
      int z,
      Heightmap.Types type,
      LevelHeightAccessor height,
      RandomState random,
      Operation<Integer> original) {
    return SeabedProjection.height(
        generator, random, height, x, z, original.call(generator, x, z, type, height, random));
  }
}
