package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Saved terrain witnesses for island stops and Ribbits villages. */
final class IslandStopsRegression {
  private static final List<String> KINDS = List.of("survey", "garden", "den", "landing");
  private static final Map<String, String> STRUCTURES =
      Map.of(
          "survey", "survey_camp",
          "garden", "surviving_garden",
          "den", "fox_den",
          "landing", "landing_deck");

  private IslandStopsRegression() {}

  static void verify(ServerLevel level, Path output) {
    var center = new ChunkPos(level.getSharedSpawnPos());
    int radius = Integer.getInteger("aeroscapes.regression.islandStopRadius", 14);
    var chunks = ChunkPos.rangeClosed(center, radius).toList();
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    report.addProperty("chunks", chunks.size());
    long started = System.nanoTime();
    var full = chunks.stream().map(position -> level.getChunk(position.x, position.z)).toList();
    report.addProperty("elapsedMs", (System.nanoTime() - started) / 1e6);
    for (String kind : KINDS) {
      var structure = registry.getHolder(id(kind)).orElseThrow().value();
      var sites = new JsonArray();
      var anchors = new ArrayList<BlockPos>();
      for (var chunk : full) {
        var start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) continue;
        var site = site(start, serialization);
        if (Math.abs(chunk.getPos().x - center.x) < radius
            && Math.abs(chunk.getPos().z - center.z) < radius) {
          verifySite(level, kind, site);
        }
        var position = coordinates(site.anchor());
        position.addProperty("facing", site.forward().getName());
        position.addProperty(
            "biome",
            level.getBiome(site.anchor().above()).unwrapKey().orElseThrow().location().toString());
        sites.add(position);
        anchors.add(site.anchor());
      }
      if (kind.equals("survey")) verifySpacing(anchors, 96);
      if (kind.equals("landing")) verifySpacing(anchors, 128);
      report.add(kind, sites);
    }
    if (Boolean.getBoolean("aeroscapes.regression.ribbitVillage")) village(level, report);
    if (Boolean.getBoolean("aeroscapes.regression.islandStopsLocate")) locate(level, report);
    try {
      Files.writeString(output, report.toString());
    } catch (IOException failure) {
      throw new UncheckedIOException("Could not write island stop witnesses", failure);
    }
    String required = System.getProperty("aeroscapes.regression.islandStopsRequired", "");
    for (String kind : required.split(",")) {
      if (!kind.isEmpty() && (!report.has(kind) || report.getAsJsonArray(kind).isEmpty())) {
        throw new IllegalStateException("No naturally generated " + kind + " in " + output);
      }
    }
    LogUtils.getLogger().info("Island stops regression complete: {}", output);
  }

  private static ResourceLocation id(String kind) {
    return ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", STRUCTURES.get(kind));
  }

  private static void verifySpacing(List<BlockPos> anchors, int minimum) {
    for (int index = 0; index < anchors.size(); index++) {
      var point = anchors.get(index);
      for (var other : anchors.subList(0, index)) {
        int gap =
            Math.max(Math.abs(point.getX() - other.getX()), Math.abs(point.getZ() - other.getZ()));
        require(gap >= minimum, point, "Two island stops of the same kind are too close");
      }
    }
  }

  private static void locate(ServerLevel level, JsonObject report) {
    var spawn = level.getSharedSpawnPos();
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var generator = level.getChunkSource().getGenerator();
    var result = new JsonObject();
    for (String kind : KINDS) {
      var id = id(kind);
      var holder = registry.getHolder(id).orElseThrow();
      var found =
          generator.findNearestMapStructure(level, HolderSet.direct(holder), spawn, 40, false);
      require(found != null && found.getSecond().equals(holder), spawn, "Search failed for " + id);
      var position = new ChunkPos(found.getFirst());
      ChunkPos.rangeClosed(position, 1).forEach(chunk -> level.getChunk(chunk.x, chunk.z));
      var start = level.getChunk(found.getFirst()).getStartForStructure(holder.value());
      require(start != null && start.isValid(), found.getFirst(), "Missing saved start for " + id);
      var site = site(start, serialization);
      verifySite(level, kind, site);
      if (kind.equals("survey")) openSupplies(level, site);
      var witness = coordinates(found.getFirst());
      var anchor = coordinates(site.anchor());
      anchor.addProperty("facing", site.forward().getName());
      witness.add("site", anchor);
      result.add(id.toString(), witness);
    }
    report.add("located", result);
  }

  private static Site site(StructureStart start, StructurePieceSerializationContext context) {
    var piece = start.getPieces().getFirst().createTag(context);
    return new Site(
        BlockPos.of(piece.getLong("anchor")), Direction.from2DDataValue(piece.getInt("facing")));
  }

  private record Site(BlockPos anchor, Direction forward) {
    BlockPos pos(int x, int y, int z) {
      return anchor.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y);
    }

    BlockState state(ServerLevel level, int x, int y, int z) {
      return level.getBlockState(pos(x, y, z));
    }
  }

  private static void verifySite(ServerLevel level, String kind, Site site) {
    switch (kind) {
      case "survey" -> {
        require(
            site.state(level, 0, 1, 0).is(Blocks.CARTOGRAPHY_TABLE)
                && site.state(level, 0, 3, 0).is(Blocks.WHITE_WOOL),
            site.anchor(),
            "Missing camp");
        require(
            level.getBlockEntity(site.pos(-1, 1, 0)) instanceof RandomizableContainer container
                && container.getLootTable() != null
                && container
                    .getLootTable()
                    .location()
                    .toString()
                    .equals("aeroscapes_reimagine:chests/survey_camp"),
            site.anchor(),
            "Camp barrel has no supplies");
      }
      case "garden" -> garden(level, site);
      case "den" -> den(level, site);
      case "landing" -> landing(level, site);
      default -> throw new IllegalStateException("Unknown island stop " + kind);
    }
  }

  private static void openSupplies(ServerLevel level, Site site) {
    var barrel = (RandomizableContainer) level.getBlockEntity(site.pos(-1, 1, 0));
    barrel.unpackLootTable(null);
    boolean food = false;
    boolean supplies = false;
    for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
      var stack = barrel.getItem(slot);
      food |= stack.is(Items.BREAD) || stack.is(Items.APPLE);
      supplies |=
          stack.is(Items.PAPER)
              || stack.is(Items.TORCH)
              || stack.is(Items.STRING)
              || stack.is(Items.FEATHER);
    }
    require(
        food && supplies && barrel.getLootTable() == null,
        site.anchor(),
        "Barrel did not generate its supplies once");
  }

  private static void garden(ServerLevel level, Site site) {
    require(
        site.state(level, 0, 0, 0).is(Blocks.WATER)
            && site.state(level, 0, 1, -2).is(Blocks.COMPOSTER),
        site.anchor(),
        "Missing garden");
    for (int x : new int[] {-1, 1}) {
      for (int z = -1; z <= 1; z++) {
        require(
            site.state(level, x, 0, z).is(Blocks.FARMLAND)
                && site.state(level, x, 1, z).is(z == 0 ? Blocks.CARROTS : Blocks.WHEAT),
            site.pos(x, 1, z),
            "Missing garden bed");
      }
    }
    var forward = site.forward();
    for (int x : new int[] {-2, 2}) {
      for (int z = -2; z <= 2; z++) {
        var wall = site.state(level, x, 1, z);
        require(
            wall.is(Blocks.MOSSY_COBBLESTONE_WALL)
                && (z == -2 || joinedWall(wall, forward.getOpposite()))
                && (z == 2 || joinedWall(wall, forward)),
            site.pos(x, 1, z),
            "Disconnected garden wall");
      }
    }
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        var below = site.pos(x, -1, z);
        require(
            level.getBlockState(below).isFaceSturdy(level, below, Direction.UP),
            below,
            "Garden overhangs unsupported ground");
      }
    }
  }

  private static boolean joinedWall(BlockState wall, Direction direction) {
    var property =
        switch (direction) {
          case NORTH -> WallBlock.NORTH_WALL;
          case EAST -> WallBlock.EAST_WALL;
          case SOUTH -> WallBlock.SOUTH_WALL;
          case WEST -> WallBlock.WEST_WALL;
          default -> throw new IllegalArgumentException(direction.getName());
        };
    return wall.getValue(property) != WallSide.NONE;
  }

  private static void den(ServerLevel level, Site site) {
    require(
        site.state(level, 1, 0, 2).is(Blocks.COARSE_DIRT)
            && site.state(level, 1, 1, 2).isAir()
            && !site.state(level, 1, 2, 2).isAir(),
        site.anchor(),
        "Missing low burrow entrance");
    require(
        site.state(level, 0, 1, 0).isAir()
            && site.state(level, 0, 2, 0).isAir()
            && !site.state(level, 0, 3, 0).isAir(),
        site.anchor(),
        "Burrow chamber obstructed");
  }

  private static void landing(ServerLevel level, Site site) {
    require(
        site.state(level, 0, 0, 0).is(Blocks.YELLOW_TERRACOTTA), site.anchor(), "Missing landing");
    var forward = site.forward();
    for (int x : new int[] {-4, 4}) {
      require(
          site.state(level, x, 2, -5).is(Blocks.LANTERN),
          site.pos(x, 2, -5),
          "Missing deck lantern");
      for (int z = -4; z <= -2; z++) {
        var rail = site.state(level, x, 1, z);
        require(
            rail.is(Blocks.SPRUCE_FENCE)
                && rail.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(forward.getOpposite()))
                && (z == -2 || rail.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(forward))),
            site.pos(x, 1, z),
            "Disconnected deck rail");
      }
    }
    for (int x = -4; x <= 4; x++) {
      for (int z = -5; z <= 9; z++) {
        int bottom = z >= 5 ? -2 : 1;
        for (int y = bottom; y <= 8; y++) {
          if (Math.abs(x) == 4 && z >= -5 && z <= -2 && y <= 2) continue;
          require(
              site.state(level, x, y, z).isAir(), site.pos(x, y, z), "Deck approach obstructed");
        }
      }
    }
  }

  private static void require(boolean condition, BlockPos point, String message) {
    if (!condition) throw new IllegalStateException(message + " at " + point);
  }

  private static void village(ServerLevel level, JsonObject report) {
    var start =
        NaturalStructureWitness.find(level, ResourceLocation.parse("ribbits:ribbit_village"));
    var box = start.getBoundingBox();
    int villageBlocks = 0;
    for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
      for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
        int[] count = {0};
        level
            .getChunk(x, z)
            .findBlocks(
                state ->
                    BuiltInRegistries.BLOCK
                        .getKey(state.getBlock())
                        .getNamespace()
                        .equals("ribbits"),
                (pos, state) -> {
                  if (box.isInside(pos)) count[0]++;
                });
        villageBlocks += count[0];
      }
    }
    if (villageBlocks == 0) throw new IllegalStateException("Native village is missing its blocks");
    var village = coordinates(box.getCenter());
    village.addProperty("pieces", start.getPieces().size());
    village.addProperty("villageBlocks", villageBlocks);
    report.add("ribbitVillage", village);
  }

  private static JsonObject coordinates(BlockPos point) {
    var result = new JsonObject();
    result.addProperty("x", point.getX());
    result.addProperty("y", point.getY());
    result.addProperty("z", point.getZ());
    return result;
  }
}
