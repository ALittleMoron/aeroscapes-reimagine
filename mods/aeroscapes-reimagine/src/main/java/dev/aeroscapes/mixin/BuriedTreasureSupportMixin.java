package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(BuriedTreasurePieces.BuriedTreasurePiece.class)
public abstract class BuriedTreasureSupportMixin {
  @Unique
  private static final TagKey<Block> TREASURE_SUPPORT =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "buried_treasure_support"));

  @Unique private boolean aeroscapesReimagine$treasureFallback;

  @WrapMethod(method = "postProcess")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin retains the native placement and loot helper.
  private void aeroscapesReimagine$retryTreasureSupport(
      WorldGenLevel region,
      StructureManager manager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox placementBounds,
      ChunkPos placementChunk,
      BlockPos pivot,
      Operation<Void> original) {
    StructurePiece piece = (StructurePiece) (Object) this;
    BoundingBox before = piece.getBoundingBox();
    original.call(region, manager, generator, random, placementBounds, placementChunk, pivot);
    // Native failure changes no blocks or RNG and leaves this exact box object intact.
    if (piece.getBoundingBox() != before) return;
    aeroscapesReimagine$treasureFallback = true;
    try {
      original.call(region, manager, generator, random, placementBounds, placementChunk, pivot);
    } finally {
      aeroscapesReimagine$treasureFallback = false;
    }
  }

  @ModifyExpressionValue(
      method = "postProcess",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/Block;defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;"),
      slice =
          @Slice(
              from =
                  @At(
                      value = "FIELD",
                      target =
                          "Lnet/minecraft/world/level/block/Blocks;STONE:Lnet/minecraft/world/level/block/Block;",
                      opcode = Opcodes.GETSTATIC),
              to =
                  @At(
                      value = "FIELD",
                      target =
                          "Lnet/minecraft/world/level/block/Blocks;ANDESITE:Lnet/minecraft/world/level/block/Block;",
                      opcode = Opcodes.GETSTATIC)),
      require = 1,
      allow = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native identity comparison must use the exact support state.
  private BlockState aeroscapesReimagine$additionalTreasureSupport(
      BlockState nativeStone, @Local(ordinal = 1) BlockState support) {
    return aeroscapesReimagine$treasureFallback && support.is(TREASURE_SUPPORT)
        ? support
        : nativeStone;
  }
}
