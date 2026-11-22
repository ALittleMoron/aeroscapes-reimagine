package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "com.yungnickyoung.minecraft.ribbits.world.processor.PillarProcessor",
    remap = false)
abstract class RibbitPillarMixin {
  @Shadow @Final public BlockState targetBlock;
  @Shadow @Final public BlockState targetBlockOutput;

  @Inject(method = "processBlock", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by Mixin during native template placement.
  private void aeroscapes$requirePillarFooting(
      LevelReader level,
      BlockPos piece,
      BlockPos center,
      StructureBlockInfo local,
      StructureBlockInfo world,
      StructurePlaceSettings settings,
      CallbackInfoReturnable<StructureBlockInfo> callback) {
    if (!(level instanceof WorldGenRegion region)
        || !region.getCenter().equals(new ChunkPos(world.pos()))
        || !world.state().is(targetBlock.getBlock())) return;
    WorldGenLevel generation = region;
    if (IslandTerrain.from(generation.getLevel().getChunkSource().randomState()) == null) return;
    // The native processor ignores its length field and writes directly down to the world floor.
    var cursor = world.pos().mutable();
    var plants = new ArrayList<BlockPos>();
    for (int depth = 1;
        depth <= SupportedStructure.WETLAND_STILT_DEPTH
            && cursor.getY() > level.getMinBuildHeight();
        depth++) {
      cursor.move(Direction.DOWN);
      var state = level.getBlockState(cursor);
      if (state.isAir() || !state.getFluidState().isEmpty()) continue;
      if (state.isFaceSturdy(level, cursor, Direction.UP)) {
        for (BlockPos plant : plants) region.setBlock(plant, Blocks.AIR.defaultBlockState(), 2);
        return;
      }
      if (!state.is(BlockTags.REPLACEABLE)
          && !state.is(BlockTags.SMALL_FLOWERS)
          && !state.is(Blocks.RED_MUSHROOM)
          && !state.is(Blocks.BROWN_MUSHROOM)) break;
      plants.add(cursor.immutable());
    }
    callback.setReturnValue(new StructureBlockInfo(world.pos(), targetBlockOutput, world.nbt()));
  }
}
