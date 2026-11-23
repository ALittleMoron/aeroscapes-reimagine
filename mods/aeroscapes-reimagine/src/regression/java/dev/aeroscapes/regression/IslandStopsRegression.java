package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Saved terrain witnesses for island stops and Ribbits villages. */
final class IslandStopsRegression {
  private static final List<String> KINDS = List.of("survey", "garden", "den", "landing");
  private static final List<String> SCENES =
      List.of(
          "bivouac",
          "repair_bay",
          "freight_stage",
          "pilot_shelter",
          "water_stop",
          "field_kitchen",
          "rescue_cache",
          "signal_point",
          "cartography_shelter",
          "abandoned_depot");
  private static final Map<String, String> STRUCTURES =
      Map.of(
          "survey", "survey_camp",
          "garden", "surviving_garden",
          "den", "fox_den",
          "landing", "landing_deck");
  private static final Map<String, BlockPos> TREE_WITNESSES =
      Map.of(
          "water_stop", new BlockPos(-2233, 225, 3385),
          "field_kitchen", new BlockPos(5609, 232, 2251),
          "abandoned_depot", new BlockPos(4811, 218, 8825));

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
    var kinds = new ArrayList<>(KINDS);
    kinds.addAll(SCENES);
    var landingAnchors = new ArrayList<BlockPos>();
    for (String kind : kinds) {
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
        if (site.side() != 0) {
          position.addProperty("sceneSide", site.side());
          position.addProperty("sceneRise", site.rise());
        }
        position.addProperty(
            "biome",
            level.getBiome(site.anchor().above()).unwrapKey().orElseThrow().location().toString());
        sites.add(position);
        anchors.add(site.anchor());
      }
      if (kind.equals("survey")) verifySpacing(anchors, 96);
      if (kind.equals("landing") || SCENES.contains(kind)) landingAnchors.addAll(anchors);
      report.add(kind, sites);
    }
    verifySpacing(landingAnchors, 128);
    if (Boolean.getBoolean("aeroscapes.regression.ribbitVillage")) village(level, report);
    try {
      var located = new JsonObject();
      report.add("located", located);
      if (Boolean.getBoolean("aeroscapes.regression.islandStopsLocate")) {
        for (String kind : KINDS) locate(level, located, kind, level.getSharedSpawnPos(), 40);
      }
      var scenes = System.getProperty("aeroscapes.regression.landingScenes", "");
      if (!scenes.isEmpty()) {
        findLandingScenes(
            level, located, scenes.equals("all") ? SCENES : List.of(scenes.split(",")));
      }
    } finally {
      try {
        Files.writeString(output, report.toString());
      } catch (IOException failure) {
        throw new UncheckedIOException("Could not write island stop witnesses", failure);
      }
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
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", STRUCTURES.getOrDefault(kind, "landing_" + kind));
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

  private static void findLandingScenes(ServerLevel level, JsonObject located, List<String> kinds) {
    var center = new ChunkPos(level.getSharedSpawnPos());
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var needs = new LinkedHashMap<ResourceLocation, String>();
    for (String kind : kinds) {
      require(SCENES.contains(kind), level.getSharedSpawnPos(), "Unknown landing scene " + kind);
      needs.put(id(kind), kind);
    }
    var source = level.getChunkSource();
    var generatorState = source.getGeneratorState();
    var islandStops =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE_SET)
            .getHolder(ResourceLocation.parse("aeroscapes_reimagine:island_stops"))
            .orElseThrow();
    var representative = registry.getHolder(id("bivouac")).orElseThrow();
    var placement =
        generatorState.getPlacementsForStructure(representative).stream()
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .findFirst()
            .orElseThrow();
    if (level.getSeed() == 7) {
      var collision = new ChunkPos(-279, -200);
      require(
          placement
                  .getPotentialStructureChunk(level.getSeed(), collision.x, collision.z)
                  .equals(collision)
              && generatorState.hasStructureChunkInRange(islandStops, collision.x, collision.z, 1)
              && !placement.isStructureChunk(generatorState, collision.x, collision.z),
          collision.getMiddleBlockPosition(216),
          "Pilot shelter's known overlapping garden slot was not excluded");
    }
    int limit = Integer.getInteger("aeroscapes.regression.landingSceneRadius", 40);
    int eligible = 0;
    var failures = new ArrayList<String>();
    if (level.getSeed() == 42) {
      for (var witness : TREE_WITNESSES.entrySet()) {
        var kind = needs.remove(id(witness.getKey()));
        if (kind == null) continue;
        var anchor = witness.getValue();
        try {
          require(
              locate(level, located, kind, anchor, 0).equals(anchor),
              anchor,
              "Tree obstruction witness moved");
          if (kind.equals("abandoned_depot")) {
            var oak = new BlockPos(4796, 212, 8819);
            for (int y = 0; y <= 2; y++) {
              var trunk = level.getBlockState(oak.above(y));
              require(
                  trunk.is(Blocks.OAK_LOG)
                      && trunk.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y,
                  oak.above(y),
                  "Nearby oak outside the landing clearance was lost");
            }
          }
        } catch (IllegalStateException failure) {
          String message = id(kind) + ": " + failure.getMessage();
          failures.add(message);
          LogUtils.getLogger().warn("Landing scene witness failed: {}", message);
        }
      }
    }
    for (int ring = 0; ring <= limit && !needs.isEmpty(); ring++) {
      for (int dx = -ring; dx <= ring && !needs.isEmpty(); dx++) {
        for (int dz = -ring; dz <= ring && !needs.isEmpty(); dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
          var chunk =
              placement.getPotentialStructureChunk(
                  level.getSeed(),
                  center.x + dx * placement.spacing(),
                  center.z + dz * placement.spacing());
          if (!placement.isStructureChunk(generatorState, chunk.x, chunk.z)) continue;
          if (NaturalStructureWitness.prefit(level, representative.value(), chunk).isEmpty()) {
            continue;
          }
          eligible++;
          var starts = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
          for (var start : starts.getAllStarts().values()) {
            if (!start.isValid()) continue;
            var key = registry.getKey(start.getStructure());
            var needed = needs.remove(key);
            if (needed != null) {
              try {
                require(
                    !generatorState.hasStructureChunkInRange(islandStops, chunk.x, chunk.z, 1),
                    chunk.getMiddleBlockPosition(0),
                    "Landing scene shares the reserved island-stop area");
                locate(level, located, needed, chunk.getMiddleBlockPosition(0), 0);
              } catch (IllegalStateException failure) {
                String message = key + ": " + failure.getMessage();
                failures.add(message);
                LogUtils.getLogger().warn("Landing scene witness failed: {}", message);
              }
            }
          }
        }
      }
      if (ring % 8 == 0) {
        LogUtils.getLogger()
            .info(
                "Landing scene search ring {}: {} eligible terraces, need {}",
                ring,
                eligible,
                needs);
      }
    }
    require(
        needs.isEmpty() && failures.isEmpty(),
        level.getSharedSpawnPos(),
        "Landing scene search checked "
            + eligible
            + " terraces. Missing: "
            + needs
            + ". Invalid: "
            + failures);
  }

  private static BlockPos locate(
      ServerLevel level, JsonObject located, String kind, BlockPos origin, int radius) {
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var generator = level.getChunkSource().getGenerator();
    var id = id(kind);
    var holder = registry.getHolder(id).orElseThrow();
    var found =
        generator.findNearestMapStructure(level, HolderSet.direct(holder), origin, radius, false);
    require(found != null && found.getSecond().equals(holder), origin, "Search failed for " + id);
    var position = new ChunkPos(found.getFirst());
    ChunkPos.rangeClosed(position, 1).forEach(chunk -> level.getChunk(chunk.x, chunk.z));
    var start = level.getChunk(found.getFirst()).getStartForStructure(holder.value());
    require(start != null && start.isValid(), found.getFirst(), "Missing saved start for " + id);
    var site = site(start, serialization);
    LogUtils.getLogger().info("Checking saved {} at {}", id, site.anchor());
    var witness = coordinates(found.getFirst());
    var anchor = coordinates(site.anchor());
    anchor.addProperty("facing", site.forward().getName());
    if (site.side() != 0) {
      anchor.addProperty("sceneSide", site.side());
      anchor.addProperty("sceneRise", site.rise());
    }
    witness.add("site", anchor);
    witness.addProperty("verified", false);
    located.add(id.toString(), witness);
    verifySite(level, kind, site);
    if (kind.equals("survey")) openSupplies(level, site);
    witness.addProperty("verified", true);
    return site.anchor();
  }

  private static Site site(StructureStart start, StructurePieceSerializationContext context) {
    var piece = start.getPieces().getFirst().createTag(context);
    return new Site(
        BlockPos.of(piece.getLong("anchor")),
        Direction.from2DDataValue(piece.getInt("facing")),
        piece.getString("scene"),
        piece.getInt("scene_side"),
        piece.getInt("scene_rise"));
  }

  private record Site(BlockPos anchor, Direction forward, String scene, int side, int rise) {
    BlockPos pos(int x, int y, int z) {
      return anchor.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y);
    }

    BlockState state(ServerLevel level, int x, int y, int z) {
      return level.getBlockState(pos(x, y, z));
    }

    BlockPos terrace(int x, int y, int z) {
      return pos(side * (5 + x), rise + y, -6 + z);
    }

    BlockState terraceState(ServerLevel level, int x, int y, int z) {
      return level.getBlockState(terrace(x, y, z));
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
      default -> landingScene(level, kind, site);
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

  private static void landingScene(ServerLevel level, String scene, Site site) {
    landing(level, site);
    require(scene.equals(site.scene()), site.anchor(), "Wrong saved scene");
    require(
        Math.abs(site.side()) == 1 && Math.abs(site.rise()) <= 1,
        site.anchor(),
        "Invalid saved terrace transform");
    for (int distance = 0; distance < 5; distance++) {
      var floor = site.pos(site.side() * distance, distance < 3 ? 0 : site.rise(), -6);
      grounded(level, floor);
    }
    for (int x = 0; x < 4; x++) {
      for (int z = 0; z < 4; z++) {
        var floor = site.terrace(x, 0, z);
        var state = level.getBlockState(floor);
        require(state.getFluidState().isEmpty(), floor, "Landing scene floor is flooded");
        if (z > 0 || state.isFaceSturdy(level, floor, Direction.UP)) grounded(level, floor);
      }
    }
    verifyLandingWalk(level, site, scene);
    switch (scene) {
      case "bivouac" -> {
        for (int row = 2; row <= 3; row++) {
          var bed = site.terraceState(level, 2, 1, row);
          require(
              bed.is(Blocks.GREEN_BED)
                  && bed.getValue(BedBlock.FACING) == site.forward()
                  && bed.getValue(BedBlock.PART) == (row == 2 ? BedPart.FOOT : BedPart.HEAD),
              site.terrace(2, 1, row),
              "Bivouac bedroll is disconnected");
        }
        requireLoot(level, site.terrace(1, 1, 2), "landing_supplies");
      }
      case "repair_bay" -> {
        var rope =
            BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("supplementaries:rope"));
        require(
            site.terraceState(level, 2, 1, 3).is(Blocks.CRAFTING_TABLE)
                && site.terraceState(level, 1, 1, 2).is(Blocks.LOOM)
                && site.terraceState(level, 1, 2, 2).is(rope.orElse(Blocks.CHAIN)),
            site.anchor(),
            "Repair bay is missing its workbench or sails");
        headroom(level, site.terrace(2, 0, 2));
      }
      case "freight_stage" -> {
        var ramp = site.terraceState(level, 2, 1, 1);
        require(
            ramp.is(Blocks.SPRUCE_STAIRS)
                && ramp.getValue(StairBlock.FACING) == site.forward()
                && site.terraceState(level, 2, 2, 3).is(Blocks.BARREL),
            site.anchor(),
            "Freight ramp is disconnected from the raised cargo");
        headroom(level, site.terrace(2, 1, 2));
      }
      case "pilot_shelter" ->
          require(
              site.terraceState(level, 2, 1, 2).is(Blocks.SPRUCE_STAIRS)
                  && site.terraceState(level, 3, 2, 3).is(Blocks.COBBLESTONE),
              site.anchor(),
              "Pilot shelter is missing its bench or windbreak");
      case "water_stop" -> {
        var water = site.terraceState(level, 2, 1, 2);
        var gutter = site.terrace(2, 1, 2);
        require(
            water.is(Blocks.WATER_CAULDRON)
                && water.getValue(LayeredCauldronBlock.LEVEL) > 0
                && site.terraceState(level, 2, 2, 2).is(Blocks.CHAIN)
                && site.terraceState(level, 2, 3, 2).is(Blocks.CHAIN),
            site.anchor(),
            "Water stop is missing its cistern or downspout");
        require(
            level.getBiome(gutter).value().getPrecipitationAt(gutter) == Biome.Precipitation.RAIN,
            gutter,
            "Water stop is outside a rainy biome");
      }
      case "field_kitchen" ->
          require(
              site.terraceState(level, 2, 1, 2).is(Blocks.CAMPFIRE)
                  && site.terraceState(level, 2, 1, 3).is(Blocks.SMOKER)
                  && site.terraceState(level, 2, 3, 2).is(Blocks.YELLOW_WOOL),
              site.anchor(),
              "Field kitchen is missing its covered cooking area");
      case "rescue_cache" -> {
        require(
            site.terraceState(level, 3, 6, 3).is(Blocks.LANTERN)
                && site.terraceState(level, 2, 5, 3).is(Blocks.RED_WOOL),
            site.anchor(),
            "Rescue flag is missing");
        requireLoot(level, site.terrace(2, 1, 2), "landing_rescue_supplies");
      }
      case "signal_point" -> {
        for (int x : new int[] {1, 3}) {
          require(
              site.terraceState(level, x, 6, 2).is(Blocks.LANTERN)
                  && site.terraceState(level, x, 5, 2).is(Blocks.WAXED_WEATHERED_COPPER),
              site.anchor(),
              "Signal lantern is missing or disconnected");
        }
      }
      case "cartography_shelter" -> {
        require(
            site.terraceState(level, 1, 1, 2).is(Blocks.CARTOGRAPHY_TABLE)
                && site.terraceState(level, 2, 4, 2).is(Blocks.LIGHT_BLUE_STAINED_GLASS),
            site.anchor(),
            "Cartography shelter is missing its table or skylight");
        headroom(level, site.terrace(2, 0, 2));
        headroom(level, site.terrace(2, 0, 3));
      }
      case "abandoned_depot" -> {
        require(
            site.terraceState(level, 3, 1, 3).is(Blocks.BARREL)
                && site.terraceState(level, 3, 2, 3).is(Blocks.GRAY_WOOL),
            site.anchor(),
            "Abandoned depot is missing its collapsed awning");
        requireLoot(level, site.terrace(2, 1, 2), "landing_supplies");
      }
      default -> throw new IllegalStateException("Unknown landing scene " + scene);
    }
  }

  private static void verifyLandingWalk(ServerLevel level, Site site, String scene) {
    var origin = site.pos(0, 0, -5);
    var floors = new HashSet<BlockPos>();
    for (int x = -3; x <= 3; x++) floors.add(site.pos(x, 0, -5));
    for (int distance = 0; distance < 5; distance++) {
      floors.add(site.pos(site.side() * distance, distance < 3 ? 0 : site.rise(), -6));
    }
    for (int x = 0; x < 4; x++) {
      for (int z = 0; z < 4; z++) floors.add(site.terrace(x, 0, z));
    }
    for (int distance = 0; distance <= 8; distance++) {
      for (int dy = -1; dy <= 1; dy++) {
        floors.add(site.pos(site.side() * distance, site.rise() + dy, -7));
      }
    }
    floors.removeIf(
        floor -> {
          var state = level.getBlockState(floor);
          return !state.getFluidState().isEmpty()
              || !state.isFaceSturdy(level, floor, Direction.UP)
              || !headroom(level, floor, 2);
        });
    var targets =
        switch (scene) {
          case "cartography_shelter" -> List.of(site.terrace(2, 0, 3));
          case "freight_stage" -> List.of(site.terrace(2, 0, 0));
          case "rescue_cache" -> List.of(site.terrace(1, 0, 2));
          case "water_stop" ->
              List.of(site.terrace(2, 0, 1), site.terrace(1, 0, 2), site.terrace(2, 0, 3));
          default -> List.of(site.terrace(2, 0, 1));
        };
    var open = new ArrayDeque<BlockPos>();
    if (floors.remove(origin)) open.add(origin);
    while (!open.isEmpty()) {
      var floor = open.removeFirst();
      if (targets.contains(floor)) return;
      for (var direction : Direction.Plane.HORIZONTAL) {
        for (int dy = -1; dy <= 1; dy++) {
          var next = floor.relative(direction).above(dy);
          var lower = dy > 0 ? floor : next;
          if (floors.contains(next) && (dy == 0 || headroom(level, lower, 3))) {
            floors.remove(next);
            open.addLast(next);
          }
        }
      }
    }
    throw new IllegalStateException("Landing scene has no walk from the deck to " + targets);
  }

  private static void grounded(ServerLevel level, BlockPos floor) {
    for (var position : List.of(floor, floor.below())) {
      require(
          level.getBlockState(position).isFaceSturdy(level, position, Direction.UP),
          position,
          "Landing scene overhangs unsupported ground");
    }
    var footing = floor.below();
    var state = level.getBlockState(footing);
    if (state.is(Blocks.STRIPPED_SPRUCE_LOG)) {
      Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
      require(
          braceContact(level, footing, Direction.get(Direction.AxisDirection.POSITIVE, axis))
              || braceContact(
                  level, footing, Direction.get(Direction.AxisDirection.NEGATIVE, axis)),
          footing,
          "Landing scene brace has no ground contact");
    }
  }

  private static boolean braceContact(ServerLevel level, BlockPos footing, Direction direction) {
    for (int span = 1; span <= 2; span++) {
      var point = footing.relative(direction, span);
      var state = level.getBlockState(point);
      if (!state.isFaceSturdy(level, point, direction.getOpposite())
          || state.is(BlockTags.LEAVES)) {
        return false;
      }
      if (!state.is(BlockTags.LOGS)) return true;
      if (!state.is(Blocks.STRIPPED_SPRUCE_LOG)
          || state.getValue(RotatedPillarBlock.AXIS) != direction.getAxis()) return false;
    }
    return false;
  }

  private static void headroom(ServerLevel level, BlockPos floor) {
    require(headroom(level, floor, 2), floor, "Landing scene walkway is obstructed");
  }

  private static boolean headroom(ServerLevel level, BlockPos floor, int height) {
    for (int y = 1; y <= height; y++) {
      var position = floor.above(y);
      var state = level.getBlockState(position);
      if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, position).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static void requireLoot(ServerLevel level, BlockPos position, String name) {
    require(
        level.getBlockEntity(position) instanceof RandomizableContainer container
            && container.getLootTable() != null
            && container.getLootTable().location().getPath().equals("chests/" + name),
        position,
        "Landing scene has no " + name);
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
