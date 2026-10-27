// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces.MineShaftRoom;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure.Type;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MineshaftStructure.class)
public abstract class IslandMineshaftMixin {
  // Keep the published instance lifetime during parity cleanup; no concurrent-state redesign.
  @Unique private int surfaceHeight;
  @Shadow @Final private Type type;

  @Redirect(
      method = "generatePiecesAndAdjust",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin target above.
  private int islandSeaLevel(ChunkGenerator generator) {
    return this.surfaceHeight - 8;
  }

  @Redirect(
      method = "generatePiecesAndAdjust",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getMinY()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin target above.
  private int islandMinY(ChunkGenerator generator) {
    return this.surfaceHeight - 32;
  }

  @Inject(method = "generatePiecesAndAdjust", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin target above.
  private void prepareIslandHeight(
      StructurePiecesBuilder builder,
      GenerationContext context,
      CallbackInfoReturnable<Integer> callback) {
    ChunkPos chunkPos = context.chunkPos();
    WorldgenRandom chunkRandom = context.random();
    MineShaftRoom mineshaftRoom =
        new MineShaftRoom(0, chunkRandom, chunkPos.getBlockX(2), chunkPos.getBlockZ(2), this.type);
    // Height uses this first assembly's center; vanilla then appends a second assembly.
    // Both assemblies and their RNG consumption are part of the retained output.
    builder.addPiece(mineshaftRoom);
    mineshaftRoom.addChildren(mineshaftRoom, builder, chunkRandom);
    if (!builder.isEmpty()) {
      this.surfaceHeight =
          context
              .chunkGenerator()
              .getBaseHeight(
                  builder.getBoundingBox().getCenter().getX(),
                  builder.getBoundingBox().getCenter().getZ(),
                  Types.WORLD_SURFACE_WG,
                  context.heightAccessor(),
                  context.randomState());
    }
  }
}
