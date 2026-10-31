package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.regression.StructureCommitObserver;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructureStart.class)
public abstract class StructureCommitScopeMixin {
  @WrapMethod(method = "placeInChunk")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin scopes actual native structure placement.
  private void aeroscapesRegression$structureScope(
      WorldGenLevel region,
      StructureManager manager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox bounds,
      ChunkPos chunk,
      Operation<Void> original) {
    if (!StructureCommitObserver.armed()) {
      original.call(region, manager, generator, random, bounds, chunk);
      return;
    }
    var previous = StructureCommitObserver.enter((StructureStart) (Object) this, region, chunk);
    try {
      original.call(region, manager, generator, random, bounds, chunk);
    } finally {
      StructureCommitObserver.restore(previous);
    }
  }
}
