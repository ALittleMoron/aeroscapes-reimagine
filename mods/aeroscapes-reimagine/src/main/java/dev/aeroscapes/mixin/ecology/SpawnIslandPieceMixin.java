package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ninni.spawn.server.level.structure.BaseIslandPiece;
import com.ninni.spawn.server.level.structure.VolcanicIslandPiece;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import dev.aeroscapes.ecology.SpawnIslandSites;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({BaseIslandPiece.class, VolcanicIslandPiece.class})
public abstract class SpawnIslandPieceMixin {
  @WrapMethod(method = "postProcess")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Scope both base decoration and the later native volcano pass.
  private void aeroscapes$localIslandWaterline(
      WorldGenLevel level,
      StructureManager structures,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox bounds,
      ChunkPos chunk,
      BlockPos position,
      Operation<Void> original) {
    var piece = (BaseIslandPiece) (Object) this;
    BlockPos origin = piece.getOrigin();
    int sea =
        SpawnIslandSites.seaLevel(
            level.getLevel().getChunkSource().randomState(),
            origin.getX(),
            origin.getZ(),
            Integer.MIN_VALUE);
    if (sea != origin.getY()) {
      original.call(level, structures, generator, random, bounds, chunk, position);
      return;
    }
    ScopedSeaLevel.call(
        sea, () -> original.call(level, structures, generator, random, bounds, chunk, position));
  }
}
