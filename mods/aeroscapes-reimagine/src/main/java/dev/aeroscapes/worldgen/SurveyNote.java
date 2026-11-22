package dev.aeroscapes.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.chunk.LevelChunk;

final class SurveyNote {
  private static final int MAX_STEPS = 12;
  private static final String TEXT = "aeroscapes_reimagine.survey_notes.";
  private static final List<Ore> ORES =
      List.of(
          new Ore(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, "coal"),
          new Ore(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, "copper"),
          new Ore(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, "iron"),
          new Ore(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, "gold"));

  private SurveyNote() {}

  static void place(LevelChunk chunk, IslandStop.Site camp) {
    // Neighbor FEATURES precedes LIGHT. At first load, read only this chunk to avoid reentering
    // the async chunk loader, and write only the note, without altering the path or deposit.
    if (!chunk.getBlockState(camp.anchor().above()).is(Blocks.CARTOGRAPHY_TABLE)) return;
    var selected = bestEntranceRoute(chunk, camp);
    if (selected == null) return;

    var pos = selected.sign();
    var state =
        Blocks.OAK_SIGN
            .defaultBlockState()
            .setValue(
                StandingSignBlock.ROTATION,
                RotationSegment.convertToSegment(selected.forward().getOpposite().toYRot()));
    if (chunk.setBlockState(pos, state, false) == null
        || !(chunk.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;

    var text =
        new SignText()
            .setMessage(0, Component.translatable(TEXT + "title"))
            .setMessage(1, Component.translatable(TEXT + "ore." + selected.ore().name()))
            .setMessage(2, Component.translatable(TEXT + "ahead", selected.ahead()));
    if (selected.side() != 0) {
      text =
          text.setMessage(
              3,
              Component.translatable(
                  TEXT + (selected.side() < 0 ? "left" : "right"), Math.abs(selected.side())));
    }
    var registries = chunk.getLevel().registryAccess();
    var data = new CompoundTag();
    data.put(
        "front_text",
        SignText.DIRECT_CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), text)
            .getOrThrow());
    data.putBoolean("is_waxed", true);
    sign.loadCustomOnly(data, registries);
  }

  private static Route bestEntranceRoute(LevelChunk chunk, IslandStop.Site camp) {
    Route best = null;
    for (int across = -1; across <= 1; across++) {
      best = shorter(best, route(chunk, camp.pos(across, 1, 2), camp.forward()));
      best = shorter(best, route(chunk, camp.pos(across, 1, -2), camp.forward().getOpposite()));
    }
    // The barrel occupies the reading position on the other side of the canopy.
    return shorter(best, route(chunk, camp.pos(2, 1, 0), camp.forward().getCounterClockWise()));
  }

  private static Route route(LevelChunk chunk, BlockPos sign, Direction forward) {
    if (!standable(chunk, sign)
        || !chunk.getBlockState(sign).isAir()
        || !standable(chunk, sign.relative(forward.getOpposite()))) {
      return null;
    }
    Route best = null;
    var feet = sign;
    for (int ahead = 1; ahead <= MAX_STEPS; ahead++) {
      feet = step(chunk, feet, forward);
      if (feet == null) break;
      var ore = exposedOre(chunk, feet, sign, forward, forward);
      if (ore != null) best = shorter(best, new Route(sign, forward, ahead, 0, ore));
      for (Direction side : List.of(forward.getCounterClockWise(), forward.getClockWise())) {
        var turnFeet = feet;
        for (int across = 1; ahead + across <= MAX_STEPS; across++) {
          if (best != null && ahead + across >= best.length()) break;
          turnFeet = step(chunk, turnFeet, side);
          if (turnFeet == null) break;
          ore = exposedOre(chunk, turnFeet, sign, forward, side);
          if (ore == null) continue;
          int turn = side == forward.getCounterClockWise() ? -across : across;
          best = new Route(sign, forward, ahead, turn, ore);
          break;
        }
      }
      if (best != null && ahead >= best.length()) break;
    }
    return best;
  }

  private static Route shorter(Route first, Route second) {
    if (first == null || second != null && second.length() < first.length()) return second;
    return first;
  }

  private static BlockPos step(LevelChunk chunk, BlockPos feet, Direction direction) {
    var next = feet.relative(direction);
    if (!inside(chunk, next)) return null;
    if (standable(chunk, next)) return next;
    if (clear(chunk, feet.above(2)) && standable(chunk, next.above())) {
      return next.above();
    }
    if (clear(chunk, next.above()) && standable(chunk, next.below())) {
      return next.below();
    }
    return null;
  }

  private static boolean standable(LevelChunk chunk, BlockPos feet) {
    if (!inside(chunk, feet) || !clear(chunk, feet) || !clear(chunk, feet.above())) return false;
    var floor = chunk.getBlockState(feet.below());
    return !floor.is(Blocks.MAGMA_BLOCK)
        && !floor.is(BlockTags.LOGS)
        && !floor.is(BlockTags.LEAVES)
        && NativeTerrainVolume.solid(floor);
  }

  private static boolean clear(LevelChunk chunk, BlockPos pos) {
    var state = chunk.getBlockState(pos);
    return state.isAir()
        || state.is(Blocks.SHORT_GRASS)
        || state.is(Blocks.TALL_GRASS)
        || state.is(Blocks.FERN)
        || state.is(Blocks.LARGE_FERN)
        || state.is(Blocks.DEAD_BUSH);
  }

  private static Ore exposedOre(
      LevelChunk chunk, BlockPos feet, BlockPos sign, Direction forward, Direction heading) {
    for (Direction outward : Direction.Plane.HORIZONTAL) {
      if (outward == heading.getOpposite()) continue;
      var face = feet.relative(outward);
      if (!inside(chunk, face)
          || (face.getX() - sign.getX()) * forward.getStepX()
                  + (face.getZ() - sign.getZ()) * forward.getStepZ()
              <= 0) continue;
      var lower = chunk.getBlockState(face).getBlock();
      var upper = chunk.getBlockState(face.above()).getBlock();
      for (var ore : ORES) {
        if (ore.matches(lower) || ore.matches(upper)) return ore;
      }
    }
    return null;
  }

  private static boolean inside(LevelChunk chunk, BlockPos pos) {
    return (pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z;
  }

  private record Route(BlockPos sign, Direction forward, int ahead, int side, Ore ore) {
    int length() {
      return ahead + Math.abs(side);
    }
  }

  private record Ore(Block stone, Block deepslate, String name) {
    boolean matches(Block block) {
      return block == stone || block == deepslate;
    }
  }
}
