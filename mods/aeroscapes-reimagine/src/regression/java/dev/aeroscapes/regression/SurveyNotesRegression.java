package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

final class SurveyNotesRegression {
  private static final String PREFIX = "aeroscapes_reimagine.survey_notes.";
  private static final ResourceLocation CAMP =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "survey_camp");

  private SurveyNotesRegression() {}

  static void verify(ServerLevel level, Path output) {
    var structure =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(CAMP).orElseThrow();
    var generator = level.getChunkSource().getGenerator();
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var tested = new HashSet<ChunkPos>();
    var camps = new ArrayList<Camp>();
    var spawn = level.getSharedSpawnPos();
    boolean neighborsFirst = Boolean.getBoolean("aeroscapes.regression.surveyNotesNeighborsFirst");
    int spacing = Integer.getInteger("aeroscapes.regression.surveyNotesSpacing", 300);
    long started = System.nanoTime();
    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        var origin = spawn.offset(dx * spacing, 0, dz * spacing);
        var located =
            generator.findNearestMapStructure(
                level, HolderSet.direct(structure), origin, 12, false);
        if (located == null) continue;
        var position = new ChunkPos(located.getFirst());
        if (!tested.add(position)) continue;
        if (neighborsFirst) loadNeighbors(level, position);
        var chunk = level.getChunk(position.x, position.z);
        if (!neighborsFirst) loadNeighbors(level, position);
        var start = chunk.getStartForStructure(structure.value());
        require(start != null && start.isValid(), located.getFirst(), "Search did not find a camp");
        var piece = start.getPieces().getFirst().createTag(serialization);
        var anchor = BlockPos.of(piece.getLong("anchor"));
        var forward = Direction.from2DDataValue(piece.getInt("facing"));
        require(
            level.getBlockState(anchor.above()).is(Blocks.CARTOGRAPHY_TABLE),
            anchor,
            "Camp is missing");
        var notes = new ArrayList<Note>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
          for (int across = -1; across <= 1; across++) {
            var signPos =
                anchor.relative(direction, 2).relative(direction.getClockWise(), across).above();
            if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
              notes.add(verifyNote(level, sign, direction));
            }
          }
        }
        require(notes.size() <= 1, anchor, "Camp has multiple survey notes");
        camps.add(new Camp(anchor.toShortString(), forward.getName(), List.copyOf(notes)));
      }
    }
    int clues = camps.stream().mapToInt(camp -> camp.notes().size()).sum();
    var report =
        new Report(
            level.getSeed(), neighborsFirst, (System.nanoTime() - started) / 1e6, clues, camps);
    try {
      Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    } catch (IOException failure) {
      throw new UncheckedIOException("Could not write survey note witnesses", failure);
    }
    int expected = Integer.getInteger("aeroscapes.regression.surveyNotesMinimum", 1);
    require(
        clues >= expected,
        spawn,
        "Too few natural survey clues: " + clues + " in " + camps.size() + " camps");
    require(clues < camps.size(), spawn, "No ordinary camps sampled");
    LogUtils.getLogger()
        .info(
            "Survey notes regression passed: seed={}, camps={}, clues={}",
            level.getSeed(),
            camps.size(),
            clues);
  }

  private static void loadNeighbors(ServerLevel level, ChunkPos center) {
    ChunkPos.rangeClosed(center, 1)
        .filter(position -> !position.equals(center))
        .forEach(position -> level.getChunk(position.x, position.z));
  }

  private static Note verifyNote(ServerLevel level, SignBlockEntity sign, Direction forward) {
    var pos = sign.getBlockPos();
    var reading = pos.relative(forward.getOpposite());
    require(
        passable(level, reading)
            && passable(level, reading.above())
            && level
                .getBlockState(reading.below())
                .isFaceSturdy(level, reading.below(), Direction.UP),
        reading,
        "Survey note is obstructed from the camp side");
    require(sign.isWaxed(), pos, "Survey note is editable");
    var state = sign.getBlockState();
    require(
        state.getBlock() instanceof StandingSignBlock standing
            && Mth.degreesDifferenceAbs(
                    standing.getYRotationDegrees(state), forward.getOpposite().toYRot())
                == 0.0F,
        pos,
        "Sign faces away from the camp");
    var text = sign.getFrontText();
    require(
        line(text.getMessage(0, false), pos).getKey().equals(PREFIX + "title"),
        pos,
        "Wrong note title");
    var material = line(text.getMessage(1, false), pos).getKey();
    require(material.startsWith(PREFIX + "ore."), pos, "Missing ore name");
    String ore = material.substring((PREFIX + "ore.").length());
    var ahead = line(text.getMessage(2, false), pos);
    require(ahead.getKey().equals(PREFIX + "ahead"), pos, "Missing first direction");
    var route = new ArrayList<BlockPos>();
    route.add(pos);
    var heading = forward;
    follow(level, route, forward, steps(ahead, pos));
    var turnText = text.getMessage(3, false);
    if (!turnText.getString().isEmpty()) {
      var turn = line(turnText, pos);
      heading =
          switch (turn.getKey()) {
            case PREFIX + "left" -> forward.getCounterClockWise();
            case PREFIX + "right" -> forward.getClockWise();
            default -> throw new IllegalStateException("Invalid survey turn at " + pos);
          };
      follow(level, route, heading, steps(turn, pos));
    }
    var target = exposedOre(level, route.getLast(), pos, forward, heading, ore);
    require(target != null, route.getLast(), "No exposed " + ore + " beside the route");
    return new Note(
        pos.toShortString(),
        target.toShortString(),
        ore,
        route.stream().map(BlockPos::toShortString).toList());
  }

  private static TranslatableContents line(Component component, BlockPos pos) {
    if (component.getContents() instanceof TranslatableContents text) return text;
    throw new IllegalStateException("Survey note lost translated text at " + pos);
  }

  private static int steps(TranslatableContents text, BlockPos pos) {
    var args = text.getArgs();
    if (args.length == 1 && args[0] instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    throw new IllegalStateException("Invalid survey distance at " + pos);
  }

  private static void follow(
      ServerLevel level, List<BlockPos> route, Direction direction, int steps) {
    require(route.size() + steps <= 13, route.getFirst(), "Survey route is too long");
    for (int step = 0; step < steps; step++) {
      var previous = route.getLast();
      var next = previous.relative(direction);
      var options = new ArrayList<BlockPos>();
      for (int dy = -1; dy <= 1; dy++) {
        var feet = next.above(dy);
        if (passable(level, feet)
            && passable(level, feet.above())
            && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
            && (dy >= 0 || passable(level, next.above()))
            && (dy <= 0 || passable(level, previous.above(2)))) {
          options.add(feet);
        }
      }
      require(options.size() == 1, next, "Published directions do not have a walkable step");
      var feet = options.getFirst();
      require(
          new ChunkPos(feet).equals(new ChunkPos(route.getFirst())),
          feet,
          "Route left the camp chunk");
      route.add(feet);
    }
  }

  private static boolean passable(ServerLevel level, BlockPos pos) {
    var state = level.getBlockState(pos);
    return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
  }

  private static BlockPos exposedOre(
      ServerLevel level,
      BlockPos feet,
      BlockPos sign,
      Direction forward,
      Direction heading,
      String ore) {
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      if (direction == heading.getOpposite()) continue;
      for (BlockPos target : List.of(feet.relative(direction), feet.above().relative(direction))) {
        if (!new ChunkPos(target).equals(new ChunkPos(feet))) continue;
        if ((target.getX() - sign.getX()) * forward.getStepX()
                + (target.getZ() - sign.getZ()) * forward.getStepZ()
            <= 0) continue;
        var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock());
        if (id.getNamespace().equals("minecraft")
            && (id.getPath().equals(ore + "_ore")
                || id.getPath().equals("deepslate_" + ore + "_ore"))) return target;
      }
    }
    return null;
  }

  private static void require(boolean condition, BlockPos pos, String detail) {
    if (!condition) throw new IllegalStateException(detail + " at " + pos);
  }

  private record Report(
      long seed, boolean neighborsFirst, double elapsedMs, int clues, List<Camp> camps) {}

  private record Camp(String anchor, String facing, List<Note> notes) {}

  private record Note(String sign, String ore, String material, List<String> route) {}
}
