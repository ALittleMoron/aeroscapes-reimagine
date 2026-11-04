package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.GroundedColumnPredicate;
import dev.aeroscapes.worldgen.NativeTerrainVolume;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Native hundred-block reproduction and bounded manor-only foundation placement. */
final class ManorFoundationRegression {
  private ManorFoundationRegression() {}

  static void verify(ServerLevel level) {
    GroundedColumnRegression.verify();
    var access = level.registryAccess();
    var operations = RegistryOps.create(JsonOps.INSTANCE, access);
    var structure =
        Objects.requireNonNull(
            access
                .registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse("nova_structures:illager_manor")));
    JsonObject definition =
        Structure.DIRECT_CODEC.encodeStart(operations, structure).getOrThrow().getAsJsonObject();
    require(
        definition.get("terrain_check").getAsString().equals("foundation_surface"),
        "Manor must validate its complete native foundation grid");
    var aliases = definition.getAsJsonObject("delegate").getAsJsonArray("pool_aliases");
    require(aliases.size() == 2, "Changed manor foundation aliases");
    int index = 0;
    for (String suffix : new String[] {"", "_dark_oak"}) {
      String nativePool = "minecraft:illager_mansion/illager_mansion_base" + suffix;
      String privatePool = "aeroscapes_reimagine:grounded/illager_manor/base" + suffix;
      String nativeFeature = "minecraft:illager_mansion_base" + suffix;
      String privateFeature = "aeroscapes_reimagine:grounded/nova/illager_mansion_base" + suffix;
      var alias = aliases.get(index++).getAsJsonObject();
      require(
          alias.get("type").getAsString().equals("minecraft:direct")
              && alias.get("alias").getAsString().equals(nativePool)
              && alias.get("target").getAsString().equals(privatePool),
          "Changed manor alias");
      var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
      var nativeData =
          StructureTemplatePool.DIRECT_CODEC
              .encodeStart(
                  operations, Objects.requireNonNull(pools.get(ResourceLocation.parse(nativePool))))
              .getOrThrow()
              .getAsJsonObject();
      var privateData =
          StructureTemplatePool.DIRECT_CODEC
              .encodeStart(
                  operations,
                  Objects.requireNonNull(pools.get(ResourceLocation.parse(privatePool))))
              .getOrThrow()
              .getAsJsonObject();
      privateData
          .getAsJsonArray("elements")
          .get(0)
          .getAsJsonObject()
          .getAsJsonObject("element")
          .addProperty("feature", nativeFeature);
      if (suffix.isEmpty()) {
        require(
            privateData.get("fallback").getAsString().equals(privatePool),
            "Manor fallback escapes into unguarded native pool");
        privateData.addProperty("fallback", nativePool);
      }
      require(privateData.equals(nativeData), "Changed native manor pool content/weight/fallback");
      var features = access.registryOrThrow(Registries.PLACED_FEATURE);
      var original = Objects.requireNonNull(features.get(ResourceLocation.parse(nativeFeature)));
      var scoped = Objects.requireNonNull(features.get(ResourceLocation.parse(privateFeature)));
      require(
          scoped.feature().value() == original.feature().value(),
          "Manor must retain the native configured feature/materials");
      var placed =
          PlacedFeature.DIRECT_CODEC.encodeStart(operations, scoped).getOrThrow().getAsJsonObject();
      require(
          placed.getAsJsonArray("placement").size() == 1
              && placed
                      .getAsJsonArray("placement")
                      .get(0)
                      .getAsJsonObject()
                      .getAsJsonObject("predicate")
                      .get("max_depth")
                      .getAsInt()
                  == 12,
          "Manor live-column budget changed");
      place(level, original, -1, Blocks.AIR.defaultBlockState(), 100);
      place(level, scoped, 0, Blocks.STONE.defaultBlockState(), 0);
      place(level, scoped, 1, Blocks.STONE.defaultBlockState(), 1);
      place(level, scoped, 12, Blocks.STONE.defaultBlockState(), 12);
      place(level, scoped, 13, Blocks.STONE.defaultBlockState(), 0);
      place(level, scoped, -1, Blocks.AIR.defaultBlockState(), 0);
      place(level, scoped, 12, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
      place(level, scoped, 12, Blocks.SAND.defaultBlockState(), 0);
    }
    if (level.getSeed() == 1) {
      var generator = level.getChunkSource().getGenerator();
      var context =
          new Structure.GenerationContext(
              access,
              generator,
              generator.getBiomeSource(),
              level.getChunkSource().randomState(),
              level.getStructureManager(),
              level.getSeed(),
              new ChunkPos(-47, -49),
              level,
              structure.biomes()::contains);
      Structure nativeDelegate =
          Structure.DIRECT_CODEC.parse(operations, definition.get("delegate")).getOrThrow();
      var original =
          nativeDelegate.findValidGenerationPoint(context).orElseThrow().getPiecesBuilder().build();
      for (int[] point : new int[][] {{-742, 223, -737}, {-748, 223, -738}, {-736, 223, -740}}) {
        var column =
            generator.getBaseColumn(
                point[0], point[2], level, level.getChunkSource().randomState());
        for (int depth = 0; depth <= 100; depth++)
          require(column.getBlock(point[1] - depth).isAir(), "Original manor void control changed");
      }
      context = context(level, structure, new ChunkPos(-47, -49));
      var repaired = structure.findValidGenerationPoint(context);
      if (repaired.isPresent()) {
        var moved = repaired.orElseThrow().getPiecesBuilder().build();
        var before = original.pieces().getFirst().getBoundingBox();
        var after = moved.pieces().getFirst().getBoundingBox();
        require(
            before.minX() != after.minX()
                || before.minY() != after.minY()
                || before.minZ() != after.minZ(),
            "Manor still accepts its original unsupported footprint");
        support(level, new StructureStart(structure, new ChunkPos(-47, -49), 0, moved));
      }
    }
    System.out.println(
        "Manor foundation regression passed: native reproduction, bounded columns and void rejection");
  }

  static JsonObject verifyStart(ServerLevel level, StructureStart start) {
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    JsonObject wrapped =
        Structure.DIRECT_CODEC
            .encodeStart(operations, start.getStructure())
            .getOrThrow()
            .getAsJsonObject();
    JsonObject aliasedData = wrapped.getAsJsonObject("delegate").deepCopy();
    JsonObject nativeData = aliasedData.deepCopy();
    nativeData.remove("pool_aliases");
    Structure nativeDelegate = Structure.DIRECT_CODEC.parse(operations, nativeData).getOrThrow();
    Structure aliased = Structure.DIRECT_CODEC.parse(operations, aliasedData).getOrThrow();
    var nativeContext = context(level, nativeDelegate, start.getChunkPos());
    var aliasedContext = context(level, aliased, start.getChunkPos());
    var currentContext = context(level, start.getStructure(), start.getChunkPos());
    var nativePieces =
        nativeDelegate
            .findValidGenerationPoint(nativeContext)
            .orElseThrow()
            .getPiecesBuilder()
            .build();
    var aliasedPieces =
        aliased.findValidGenerationPoint(aliasedContext).orElseThrow().getPiecesBuilder().build();
    var currentPieces =
        start
            .getStructure()
            .findValidGenerationPoint(currentContext)
            .orElseThrow()
            .getPiecesBuilder()
            .build();
    var nativeRoot = nativePieces.pieces().getFirst().getBoundingBox();
    var currentRoot = currentPieces.pieces().getFirst().getBoundingBox();
    int dx = currentRoot.minX() - nativeRoot.minX();
    int dy = currentRoot.minY() - nativeRoot.minY();
    int dz = currentRoot.minZ() - nativeRoot.minZ();
    require(
        Math.abs(dx) + Math.abs(dz) <= 32 && Math.abs(dy) <= 12,
        "Manor relocation exceeded its bounded local search");
    translate(nativePieces, dx, dy, dz);
    translate(aliasedPieces, dx, dy, dz);
    var expected = pieces(level, nativePieces);
    require(
        expected.equals(pieces(level, aliasedPieces))
            && expected.equals(pieces(level, currentPieces))
            && expected.equals(pieces(level, new PiecesContainer(start.getPieces()))),
        "Manor adaptation changed native pieces, ordering, rooms or junctions");
    long tail = nativeContext.random().nextLong();
    require(
        tail == aliasedContext.random().nextLong() && tail == currentContext.random().nextLong(),
        "Manor adaptation changed native assembly RNG");
    JsonObject report = new JsonObject();
    report.addProperty("native_piece_count", nativePieces.pieces().size());
    report.addProperty("native_graph_and_rng_equal", true);
    JsonArray translation = new JsonArray();
    translation.add(dx);
    translation.add(dy);
    translation.add(dz);
    report.add("translation", translation);
    report.add("foundation_support", support(level, start));
    return report;
  }

  private static void translate(PiecesContainer pieces, int dx, int dy, int dz) {
    for (var piece : pieces.pieces()) {
      piece.move(dx, dy, dz);
      if (piece instanceof PoolElementStructurePiece pool)
        pool.getJunctions()
            .replaceAll(
                junction ->
                    new net.minecraft.world.level.levelgen.structure.pools.JigsawJunction(
                        junction.getSourceX() + dx,
                        junction.getSourceGroundY() + dy,
                        junction.getSourceZ() + dz,
                        junction.getDeltaY(),
                        junction.getDestProjection()));
    }
  }

  private static JsonObject support(ServerLevel level, StructureStart start) {
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    Map<BlockPos, NativeTerrainVolume> volumes = new HashMap<>();
    WorldGenLevel raw =
        (WorldGenLevel)
            Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[] {WorldGenLevel.class},
                (proxy, method, arguments) -> {
                  var point = (BlockPos) Objects.requireNonNull(arguments)[0];
                  if (method.getName().equals("isOutsideBuildHeight"))
                    return level.isOutsideBuildHeight(point);
                  if (!method.getName().equals("getBlockState"))
                    throw new IllegalStateException(
                        "Unexpected raw support call " + method.getName());
                  var origin =
                      new BlockPos(
                          Math.floorDiv(point.getX(), 16) * 16,
                          Math.floorDiv(point.getY(), 24) * 24,
                          Math.floorDiv(point.getZ(), 16) * 16);
                  var volume =
                      volumes.computeIfAbsent(
                          origin,
                          position ->
                              new NativeTerrainVolume(
                                  generator,
                                  level.getChunkSource().randomState(),
                                  new BoundingBox(
                                      position.getX(),
                                      position.getY(),
                                      position.getZ(),
                                      position.getX() + 15,
                                      position.getY() + 23,
                                      position.getZ() + 15)));
                  return volume.block(point.getX(), point.getY(), point.getZ());
                });
    JsonObject definition = new JsonObject();
    definition.addProperty("max_depth", 12);
    var predicate =
        GroundedColumnPredicate.CODEC.codec().parse(JsonOps.INSTANCE, definition).getOrThrow();
    int count = 0;
    Map<BlockPos, Boolean> supported = new HashMap<>();
    JsonArray unsupported = new JsonArray();
    for (var piece : start.getPieces()) {
      if (!(piece instanceof PoolElementStructurePiece pool)
          || !(pool.getElement() instanceof FeaturePoolElement)) continue;
      count++;
      BlockPos origin = pool.getPosition();
      boolean hasBed = predicate.test(raw, origin);
      supported.put(origin, hasBed);
      if (!hasBed) {
        JsonArray point = new JsonArray();
        point.add(origin.getX());
        point.add(origin.getY());
        point.add(origin.getZ());
        unsupported.add(point);
      }
    }
    require(
        unsupported.size() * 20 <= count, "Natural manor lost near-complete foundation support");
    var root = start.getPieces().getFirst();
    int rootFloor =
        root.getBoundingBox().minY()
            + (root instanceof PoolElementStructurePiece pool ? pool.getGroundLevelDelta() : 0);
    double minimumSupport = 1;
    for (var piece : start.getPieces()) {
      var bounds = piece.getBoundingBox();
      if (!(piece instanceof PoolElementStructurePiece pool)
          || pool.getElement() instanceof FeaturePoolElement
          || bounds.minY() > rootFloor
          || bounds.getXSpan() < 8
          || bounds.getZSpan() < 8) continue;
      int tested = 0;
      int found = 0;
      for (var entry : supported.entrySet()) {
        var point = entry.getKey();
        if (point.getX() < bounds.minX()
            || point.getX() > bounds.maxX()
            || point.getZ() < bounds.minZ()
            || point.getZ() > bounds.maxZ()) continue;
        tested++;
        if (entry.getValue()) found++;
      }
      if (tested == 0) continue;
      require(found * 10 >= tested * 9, "Natural manor has a concentrated unsupported main wing");
      minimumSupport = Math.min(minimumSupport, (double) found / tested);
    }
    JsonObject report = new JsonObject();
    report.addProperty("native_columns", count);
    report.addProperty("supported_columns", count - unsupported.size());
    report.addProperty("minimum_main_piece_support", minimumSupport);
    report.add("small_gaps", unsupported);
    return report;
  }

  private static Structure.GenerationContext context(
      ServerLevel level, Structure structure, ChunkPos chunk) {
    var generator = level.getChunkSource().getGenerator();
    return new Structure.GenerationContext(
        level.registryAccess(),
        generator,
        generator.getBiomeSource(),
        level.getChunkSource().randomState(),
        level.getStructureManager(),
        level.getSeed(),
        chunk,
        level,
        structure.biomes()::contains);
  }

  private static ListTag pieces(ServerLevel level, PiecesContainer pieces) {
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    ListTag result = new ListTag();
    for (var piece : pieces.pieces()) {
      var saved = piece.createTag(serialization);
      var element = saved.getCompound("pool_element");
      String feature = element.getString("feature");
      String prefix = "aeroscapes_reimagine:grounded/nova/illager_mansion_base";
      if (feature.equals(prefix) || feature.equals(prefix + "_dark_oak"))
        element.putString(
            "feature", "minecraft:illager_mansion_base" + feature.substring(prefix.length()));
      result.add(saved);
    }
    return result;
  }

  private static void place(
      ServerLevel level, PlacedFeature feature, int bedDepth, BlockState bed, int expectedWrites) {
    BlockPos origin = new BlockPos(0, 150, 0);
    Map<BlockPos, BlockState> written = new HashMap<>();
    WorldGenLevel fixture =
        (WorldGenLevel)
            Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[] {WorldGenLevel.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("getMinBuildHeight")) return -128;
                  if (method.getName().equals("getHeight") && arguments == null) return 512;
                  if (method.getName().equals("getLevel")) return level;
                  if (method.getName().equals("ensureCanWrite")) return true;
                  if (method.getName().equals("getBlockState")) {
                    var point = (BlockPos) Objects.requireNonNull(arguments)[0];
                    return written.getOrDefault(
                        point,
                        origin.getY() - point.getY() == bedDepth
                            ? bed
                            : Blocks.AIR.defaultBlockState());
                  }
                  if (method.getName().equals("setBlock")) {
                    var point = (BlockPos) Objects.requireNonNull(arguments)[0];
                    require(
                        point.getX() == 0
                            && point.getZ() == 0
                            && point.getY() <= origin.getY()
                            && point.getY() > origin.getY() - 100,
                        "Native column escaped its bounded fixture");
                    written.put(point.immutable(), (BlockState) arguments[1]);
                    return true;
                  }
                  if (method.getName().equals("isOutsideBuildHeight")) {
                    Object point = Objects.requireNonNull(arguments)[0];
                    int y = point instanceof BlockPos position ? position.getY() : (Integer) point;
                    return y < -128 || y >= 384;
                  }
                  throw new IllegalStateException(
                      "Unexpected column world call " + method.getName());
                });
    feature.place(fixture, level.getChunkSource().getGenerator(), RandomSource.create(1), origin);
    require(
        written.size() == expectedWrites,
        "Incorrect native column writes at bed depth " + bedDepth + ": " + written.size());
    if (bedDepth >= 0)
      require(
          !written.containsKey(origin.below(bedDepth)),
          "Native manor foundation replaced its supporting bed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
