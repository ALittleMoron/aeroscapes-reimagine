package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.IslandMonumentStructure;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StructureStart.class)
public abstract class MonumentReloadMixin {
  @WrapOperation(
      method = "loadStaticStart",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure;regeneratePiecesAfterLoad(Lnet/minecraft/world/level/ChunkPos;JLnet/minecraft/world/level/levelgen/structure/pieces/PiecesContainer;)Lnet/minecraft/world/level/levelgen/structure/pieces/PiecesContainer;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the pinned native Mixin target above.
  private static PiecesContainer aeroscapesReimagine$preserveElevation(
      ChunkPos chunk,
      long seed,
      PiecesContainer saved,
      Operation<PiecesContainer> original,
      @Local Structure structure) {
    return structure instanceof IslandMonumentStructure
        ? IslandMonumentStructure.regenerate(chunk, seed, saved)
        : original.call(chunk, seed, saved);
  }
}
