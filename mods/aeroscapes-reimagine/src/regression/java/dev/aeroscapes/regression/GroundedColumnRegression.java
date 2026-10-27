package dev.aeroscapes.regression;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.GroundedColumnPredicate;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Bounded first-stop behavior, including support just below a full native column. */
final class GroundedColumnRegression {
  private GroundedColumnRegression() {}

  static void verify() {
    for (int depth : new int[] {0, 1, 99, 100}) {
      check(depth, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), true);
    }
    check(101, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
    check(-1, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
    check(1, Blocks.CAVE_AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
    check(3, Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(), true);
    check(3, Blocks.STONE.defaultBlockState(), Blocks.LAVA.defaultBlockState(), true);
    // Grass is in the native pass-through tag: do not mistake it for an early stop.
    check(0, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
    check(100, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), true);
  }

  static void verifyWitchWiring(ServerLevel level) {
    var access = level.registryAccess();
    var operations = RegistryOps.create(JsonOps.INSTANCE, access);
    String nativePool = "minecraft:witch_hut/foundation";
    String scopedPool = "aeroscapes_reimagine:grounded/witch_villa/foundation";
    String nativeFeature = "minecraft:witch_hut_base";
    String scopedFeature = "aeroscapes_reimagine:grounded/nova/witch_hut_base";
    var witch =
        Objects.requireNonNull(
            access
                .registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse("nova_structures:witch_villa")));
    var definition =
        Structure.DIRECT_CODEC.encodeStart(operations, witch).getOrThrow().getAsJsonObject();
    var aliases = definition.getAsJsonObject("delegate").getAsJsonArray("pool_aliases");
    require(aliases != null && aliases.size() == 1, "Changed scoped Witch pool aliases");
    var alias = aliases.get(0).getAsJsonObject();
    require(
        alias.get("type").getAsString().equals("minecraft:direct")
            && alias.get("alias").getAsString().equals(nativePool)
            && alias.get("target").getAsString().equals(scopedPool),
        "Witch foundation does not resolve through its private direct alias");

    var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
    var shared = Objects.requireNonNull(pools.get(ResourceLocation.parse(nativePool)));
    var scoped = Objects.requireNonNull(pools.get(ResourceLocation.parse(scopedPool)));
    var sharedData =
        StructureTemplatePool.DIRECT_CODEC
            .encodeStart(operations, shared)
            .getOrThrow()
            .getAsJsonObject();
    var scopedData =
        StructureTemplatePool.DIRECT_CODEC
            .encodeStart(operations, scoped)
            .getOrThrow()
            .getAsJsonObject();
    require(
        sharedData.get("fallback").getAsString().equals("minecraft:empty")
            && sharedData.getAsJsonArray("elements").size() == 1,
        "Changed shared native Witch foundation/fallback");
    var sharedRow = sharedData.getAsJsonArray("elements").get(0).getAsJsonObject();
    require(
        sharedRow.get("weight").getAsInt() == 6
            && sharedRow
                .getAsJsonObject("element")
                .get("feature")
                .getAsString()
                .equals(nativeFeature)
            && sharedRow.getAsJsonObject("element").get("projection").getAsString().equals("rigid"),
        "Shared foundation no longer retains the native holder/weight/projection");
    var scopedElement =
        scopedData.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element");
    require(
        scopedElement.get("feature").getAsString().equals(scopedFeature),
        "Private Witch pool bypasses its grounded placed feature");
    scopedElement.addProperty("feature", nativeFeature);
    require(scopedData.equals(sharedData), "Private Witch pool changed other native fields");

    var features = access.registryOrThrow(Registries.PLACED_FEATURE);
    var nativePlaced = Objects.requireNonNull(features.get(ResourceLocation.parse(nativeFeature)));
    var scopedPlaced = Objects.requireNonNull(features.get(ResourceLocation.parse(scopedFeature)));
    var configured =
        Objects.requireNonNull(
            access
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.parse(nativeFeature)));
    require(
        nativePlaced.feature().value() == configured
            && scopedPlaced.feature().value() == configured,
        "Private Witch feature replaced the native configured-feature holder");
    var nativeData =
        PlacedFeature.DIRECT_CODEC
            .encodeStart(operations, nativePlaced)
            .getOrThrow()
            .getAsJsonObject();
    var scopedPlacedData =
        PlacedFeature.DIRECT_CODEC
            .encodeStart(operations, scopedPlaced)
            .getOrThrow()
            .getAsJsonObject();
    var modifiers = scopedPlacedData.getAsJsonArray("placement");
    require(
        nativeData.getAsJsonArray("placement").isEmpty() && modifiers.size() == 1,
        "Changed native/scoped Witch placement modifier count");
    var filter = modifiers.remove(0).getAsJsonObject();
    require(
        filter.get("type").getAsString().equals("minecraft:block_predicate_filter")
            && filter
                .getAsJsonObject("predicate")
                .get("type")
                .getAsString()
                .equals("aeroscapes_reimagine:grounded_column"),
        "Missing Witch grounded-column predicate");
    require(
        scopedPlacedData.equals(nativeData), "Private Witch feature changed other native fields");
  }

  static void verifyToxic(ServerLevel level) {
    require(level.getSeed() == 1, "Use the verified Toxic seed-one fixture");
    Structure structure =
        Objects.requireNonNull(
            level
                .registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse("nova_structures:toxic_lair")));
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    var negative =
        new Structure.GenerationContext(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            random,
            level.getStructureManager(),
            level.getSeed(),
            new ChunkPos(45551, 64985),
            level,
            structure.biomes()::contains);
    require(structure.findValidGenerationPoint(negative).isEmpty(), "Toxic accepted old void root");
    var start = level.getChunk(916, 1059).getStartForStructure(structure);
    require(start != null && start.isValid(), "Missing natural projected Toxic start");
    var root = start.getPieces().getFirst().getBoundingBox();
    var center = root.getCenter();
    var column = generator.getBaseColumn(center.getX(), center.getZ(), level, random);
    int floor = root.minY() + 1;
    require(root.getYSpan() == 36, "Changed pinned Toxic root height");
    require(
        column.getBlock(floor - 1).is(Blocks.STONE) && column.getBlock(floor - 4).is(Blocks.STONE),
        "Toxic root lost its natural stone support");
    for (var piece : start.getPieces()) {
      var bounds = piece.getBoundingBox();
      require(
          !level.isOutsideBuildHeight(bounds.minY()) && !level.isOutsideBuildHeight(bounds.maxY()),
          "Toxic child outside actual build bounds");
    }

    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    Set<String> wired = new HashSet<>();
    for (String name :
        new String[] {"cobblestone_toxic_base", "oak_log_base", "stone_bricks_toxic_base"}) {
      String id = "aeroscapes_reimagine:grounded/nova/" + name;
      var placed =
          Objects.requireNonNull(
              level
                  .registryAccess()
                  .registryOrThrow(Registries.PLACED_FEATURE)
                  .get(ResourceLocation.parse(id)));
      var nativeFeature =
          Objects.requireNonNull(
              level
                  .registryAccess()
                  .registryOrThrow(Registries.CONFIGURED_FEATURE)
                  .get(ResourceLocation.parse("nova_structures:" + name)));
      require(placed.feature().value() == nativeFeature, "Changed native Toxic feature holder");
      var encoded =
          PlacedFeature.DIRECT_CODEC.encodeStart(ops, placed).getOrThrow().getAsJsonObject();
      var modifiers = encoded.getAsJsonArray("placement");
      require(modifiers.size() == 1, "Changed scoped Toxic placement modifiers");
      require(
          modifiers
              .get(0)
              .getAsJsonObject()
              .getAsJsonObject("predicate")
              .get("type")
              .getAsString()
              .equals("aeroscapes_reimagine:grounded_column"),
          "Missing scoped Toxic grounded predicate");
      for (var piece : start.getPieces()) {
        if (piece instanceof PoolElementStructurePiece pool
            && pool.getElement() instanceof FeaturePoolElement feature) {
          var element =
              FeaturePoolElement.CODEC
                  .codec()
                  .encodeStart(ops, feature)
                  .getOrThrow()
                  .getAsJsonObject();
          if (id.equals(element.get("feature").getAsString())) {
            wired.add(id);
            break;
          }
        }
      }
    }
    require(wired.size() == 3, "Native Toxic pools bypass the scoped grounded clones");

    loot(level, new BlockPos(14620, 216, 16933), "nova_structures:chests/toxic_lair/generic");
    loot(level, new BlockPos(14620, 218, 16933), "nova_structures:pots/pot_toxic");
    loot(level, new BlockPos(14621, 218, 16933), "nova_structures:chests/toxic_lair/kitchen");
    for (int[] origin :
        new int[][] {
          {14709, 220, 16943}, {14706, 220, 16942}, {14691, 220, 16926},
          {14707, 220, 16940}, {14689, 220, 16945}, {14701, 220, 16941},
          {14709, 220, 16931}
        }) {
      level.getChunk(origin[0] >> 4, origin[2] >> 4);
      var raw = generator.getBaseColumn(origin[0], origin[2], level, random);
      for (int depth = 0; depth <= 100; depth++) {
        var point = new BlockPos(origin[0], origin[1] - depth, origin[2]);
        require(raw.getBlock(point.getY()).isAir(), "Toxic fixture no longer crosses raw void");
        // Authored rooms intersect the upper column. Its bottom 61 blocks must stay clear.
        if (depth >= 40) require(level.getBlockState(point).isAir(), "Unsupported Toxic base tail");
      }
    }
  }

  private static void loot(ServerLevel level, BlockPos point, String expected) {
    level.getChunk(point.getX() >> 4, point.getZ() >> 4);
    var entity = Objects.requireNonNull(level.getBlockEntity(point));
    require(
        entity.saveWithFullMetadata(level.registryAccess()).getString("LootTable").equals(expected),
        "Missing native Toxic loot at " + point);
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }

  private static void check(int depth, BlockState stop, BlockState traversed, boolean expected) {
    BlockPos origin = new BlockPos(0, 150, 0);
    AtomicInteger reads = new AtomicInteger();
    WorldGenLevel level =
        (WorldGenLevel)
            Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[] {WorldGenLevel.class},
                (proxy, method, arguments) -> {
                  if (arguments == null || arguments.length != 1) {
                    throw new IllegalStateException("Unexpected world call " + method.getName());
                  }
                  if (method.getName().equals("getBlockState")) {
                    BlockPos position = (BlockPos) arguments[0];
                    if (reads.incrementAndGet() > 101
                        || position.getX() != origin.getX()
                        || position.getZ() != origin.getZ()) {
                      throw new IllegalStateException(
                          "Column predicate exceeded its bounded column");
                    }
                    return origin.getY() - position.getY() == depth ? stop : traversed;
                  }
                  if (method.getName().equals("isOutsideBuildHeight")) {
                    int y =
                        arguments[0] instanceof BlockPos position
                            ? position.getY()
                            : (Integer) arguments[0];
                    return y < -64 || y >= 384;
                  }
                  throw new IllegalStateException("Unexpected world call " + method.getName());
                });
    var predicate =
        GroundedColumnPredicate.CODEC
            .codec()
            .parse(JsonOps.INSTANCE, new JsonObject())
            .getOrThrow();
    if (predicate.test(level, origin) != expected || reads.get() == 0) {
      throw new IllegalStateException("Incorrect column support at depth " + depth + ": " + stop);
    }
  }
}
