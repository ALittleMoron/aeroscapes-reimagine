package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Fresh native candidate eligibility and root support, without loading or placing world chunks. */
public final class OutpostTerrainRegression {
  private static final ResourceLocation ID =
      ResourceLocation.parse("nova_structures:badlands_miner_outpost");
  private static final int CANDIDATE_BUDGET = 1024;

  private OutpostTerrainRegression() {}

  public static void verify(ServerLevel level) {
    String requested = System.getProperty("aeroscapes.regression.outpostTerrainReport");
    if (requested == null || !Path.of(requested).isAbsolute())
      throw new IllegalArgumentException("Outpost terrain report needs an absolute output path");
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalArgumentException("Outpost terrain diagnostic needs the dedicated thread");
    if (!level.getServer().getWorldData().worldGenOptions().generateStructures())
      throw new IllegalArgumentException(
          "Outpost terrain diagnostic requires native structures enabled");
    rootSupportControls(level);
    Audit audit = new Audit(level, Path.of(requested));
    NeoForge.EVENT_BUS.addListener(audit::tick);
  }

  private static void rootSupportControls(ServerLevel level) {
    try {
      Method support =
          SupportedStructure.class.getDeclaredMethod(
              "supportedRootFootprint", BoundingBox.class, int.class, BiFunction.class);
      support.setAccessible(true);
      int floor = 80;
      BoundingBox bounds = new BoundingBox(0, floor - 1, 0, 36, floor + 24, 36);
      int noCavity = level.getMaxBuildHeight();
      NoiseColumn flat = column(level, floor - 1, noCavity);
      NoiseColumn shallow = column(level, floor - 7, noCavity);
      NoiseColumn cavity = column(level, floor - 1, floor - 4);
      NoiseColumn empty = column(level, level.getMinBuildHeight() - 1, noCavity);
      NoiseColumn distant = column(level, floor - 20, noCavity);
      checkFootprint(support, bounds, floor, (x, z) -> flat, true, "broad natural bed");
      checkFootprint(
          support,
          bounds,
          floor,
          (x, z) -> x == bounds.maxX() && z == bounds.maxZ() ? shallow : flat,
          true,
          "one shallow corner slope with 99 percent deep support");
      checkFootprint(
          support,
          bounds,
          floor,
          (x, z) -> x == 4 && z == 4 ? cavity : flat,
          true,
          "one small cave opening with a nearby natural bed");
      checkFootprint(
          support,
          bounds,
          floor,
          (x, z) -> x == bounds.maxX() ? empty : flat,
          false,
          "void edge strip despite 90 percent aggregate deep support");
      checkFootprint(
          support,
          bounds,
          floor,
          (x, z) -> distant,
          false,
          "distant lower island twenty blocks below the terrace");
      checkFootprint(
          support,
          bounds,
          floor,
          (x, z) -> x < 8 ? shallow : flat,
          false,
          "twenty percent deep-plane holes despite nearby beds");
      LogUtils.getLogger()
          .info(
              "Outpost root-footprint controls passed: natural bed, shallow slope, cave, void edge, distant bed and hollow plane");
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot exercise actual outpost root support", exception);
    }
  }

  private static void checkFootprint(
      Method support,
      BoundingBox bounds,
      int floor,
      BiFunction<Integer, Integer, NoiseColumn> columns,
      boolean expected,
      String caseName)
      throws ReflectiveOperationException {
    boolean actual = (boolean) support.invoke(null, bounds, floor, columns);
    if (actual != expected)
      throw new IllegalStateException("Outpost root support changed for " + caseName);
  }

  private static NoiseColumn column(ServerLevel level, int surface, int cavity) {
    BlockState[] blocks = new BlockState[level.getHeight()];
    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
      blocks[y - level.getMinBuildHeight()] =
          y <= surface && y != cavity
              ? Blocks.STONE.defaultBlockState()
              : Blocks.AIR.defaultBlockState();
    return new NoiseColumn(level.getMinBuildHeight(), blocks);
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Path output;
    private final Structure guarded;
    private final Structure nativeStructure;
    private final RandomSpreadStructurePlacement placement;
    private final JsonObject report = new JsonObject();
    private final JsonArray candidates = new JsonArray();
    private final Map<String, Integer> counts = new TreeMap<>();
    private final long started = System.nanoTime();
    private int enumerated;
    private int radius;
    private int perimeter;
    private boolean done;

    private Audit(ServerLevel level, Path output) {
      this.level = level;
      this.output = output;
      guarded = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ID);
      if (guarded == null) throw new IllegalStateException("Missing badlands miner outpost");
      var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
      JsonObject definition =
          Structure.DIRECT_CODEC.encodeStart(operations, guarded).getOrThrow().getAsJsonObject();
      if (!definition.get("type").getAsString().equals("aeroscapes_reimagine:supported"))
        throw new IllegalStateException("Expected the current supported outpost wrapper");
      nativeStructure =
          Structure.DIRECT_CODEC.parse(operations, definition.get("delegate")).getOrThrow();
      if (!(nativeStructure instanceof JigsawStructure))
        throw new IllegalStateException("Expected the unchanged native jigsaw outpost delegate");
      var matches =
          level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
              .filter(
                  set ->
                      set.value().structures().stream()
                          .anyMatch(member -> member.structure().value() == guarded))
              .toList();
      if (matches.size() != 1)
        throw new IllegalStateException("Expected one native outpost structure set");
      var set = matches.getFirst();
      if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)
          || set.value().structures().size() != 1)
        throw new IllegalStateException("Expected the pinned single-member random outpost set");
      placement = spread;
      report.addProperty("format", 1);
      report.addProperty("seed", level.getSeed());
      report.addProperty(
          "structure_generation_seed", level.getChunkSource().getGeneratorState().getLevelSeed());
      report.addProperty("target", ID.toString());
      report.add("guarded_definition", definition);
      report.add(
          "native_set",
          StructureSet.DIRECT_CODEC.encodeStart(operations, set.value()).getOrThrow());
      report.addProperty("candidate_budget", CANDIDATE_BUDGET);
      report.addProperty("region_center_x", Math.floorDiv(143, spread.spacing()));
      report.addProperty("region_center_z", Math.floorDiv(127, spread.spacing()));
      report.addProperty(
          "scope",
          "Native potential positions and isStructureChunk including exclusion; original biome"
              + " predicates in independent native/guarded contexts. Template assemblies and raw"
              + " columns only, without chunk requests, block writes or forced placement."
              + " Native-positive root templates have authored terrace layers at local Y0..5;"
              + " support profiles use the actual root minY plus its native groundLevelDelta."
              + " Accepted stubs remain candidates requiring actual natural STARTS/FULL proof.");
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      long slice = System.nanoTime();
      while (!done && System.nanoTime() - slice < 20_000_000L) {
        if (enumerated == CANDIDATE_BUDGET || System.nanoTime() - started > 600_000_000_000L) {
          finish(enumerated == CANDIDATE_BUDGET ? "candidate_budget" : "deadline");
          return;
        }
        inspect(next());
        if (enumerated % 128 == 0)
          LogUtils.getLogger()
              .info("Outpost terrain progress: candidates={} counts={}", enumerated, counts);
      }
    }

    private ChunkPos next() {
      int dx = 0;
      int dz = 0;
      if (radius > 0) {
        int width = radius * 2;
        int side = perimeter / width;
        int offset = perimeter % width;
        dx =
            switch (side) {
              case 0 -> -radius + offset;
              case 1 -> radius;
              case 2 -> radius - offset;
              default -> -radius;
            };
        dz =
            switch (side) {
              case 0 -> -radius;
              case 1 -> -radius + offset;
              case 2 -> radius;
              default -> radius - offset;
            };
      }
      ChunkPos candidate =
          placement.getPotentialStructureChunk(
              level.getChunkSource().getGeneratorState().getLevelSeed(),
              (Math.floorDiv(143, placement.spacing()) + dx) * placement.spacing(),
              (Math.floorDiv(127, placement.spacing()) + dz) * placement.spacing());
      enumerated++;
      if (++perimeter == (radius == 0 ? 1 : radius * 8)) {
        perimeter = 0;
        radius++;
      }
      return candidate;
    }

    private void inspect(ChunkPos origin) {
      JsonObject row = new JsonObject();
      row.addProperty("chunk_x", origin.x);
      row.addProperty("chunk_z", origin.z);
      candidates.add(row);
      if (!placement.isStructureChunk(
          level.getChunkSource().getGeneratorState(), origin.x, origin.z)) {
        row.addProperty("status", "native_placement_rejected");
        count("native_placement_rejected");
        return;
      }
      JsonArray biomeCalls = new JsonArray();
      var nativeStub =
          nativeStructure.findValidGenerationPoint(context(origin, nativeStructure, biomeCalls));
      row.add("native_biome_predicate_calls", biomeCalls);
      if (nativeStub.isEmpty()) {
        row.addProperty("status", "native_stub_absent");
        count(biomeCalls.isEmpty() ? "native_no_stub_before_biome" : "native_biome_rejected");
        return;
      }
      var builder = nativeStub.orElseThrow().getPiecesBuilder();
      var pieces = builder.build().pieces();
      if (pieces.isEmpty() || !(pieces.getFirst() instanceof PoolElementStructurePiece root))
        throw new IllegalStateException("Native outpost did not retain its jigsaw root");
      row.addProperty("piece_count", pieces.size());
      row.add("assembly_bounds", bounds(builder.getBoundingBox()));
      row.addProperty(
          "assembly_within_build_height",
          !level.isOutsideBuildHeight(builder.getBoundingBox().minY())
              && !level.isOutsideBuildHeight(builder.getBoundingBox().maxY()));
      row.add("root_bounds", bounds(root.getBoundingBox()));
      row.addProperty("ground_level_delta", root.getGroundLevelDelta());
      grid(root, row);
      JsonArray guardedBiomeCalls = new JsonArray();
      var guardedStub =
          guarded.findValidGenerationPoint(context(origin, guarded, guardedBiomeCalls));
      row.add("guarded_biome_predicate_calls", guardedBiomeCalls);
      boolean accepted = guardedStub.isPresent();
      row.addProperty("status", accepted ? "guarded_candidate_accepted" : "native_only_candidate");
      count("native_stub_accepted");
      count(accepted ? "guarded_candidate_accepted" : "guarded_candidate_rejected");
      if (accepted)
        LogUtils.getLogger()
            .info(
                "Outpost guarded candidate: seed={} chunk={} support={}",
                level.getSeed(),
                origin,
                row.get("support_both"));
    }

    private Structure.GenerationContext context(
        ChunkPos origin, Structure structure, JsonArray calls) {
      var generator = level.getChunkSource().getGenerator();
      return new Structure.GenerationContext(
          level.registryAccess(),
          generator,
          generator.getBiomeSource(),
          level.getChunkSource().randomState(),
          level.getStructureManager(),
          level.getChunkSource().getGeneratorState().getLevelSeed(),
          origin,
          level,
          biome -> {
            boolean accepted = structure.biomes().contains(biome);
            JsonObject call = new JsonObject();
            call.addProperty("biome", biome.unwrapKey().orElseThrow().location().toString());
            call.addProperty("accepted", accepted);
            calls.add(call);
            return accepted;
          });
    }

    private void grid(PoolElementStructurePiece root, JsonObject row) {
      BoundingBox bounds = root.getBoundingBox();
      int floor = bounds.minY() + root.getGroundLevelDelta();
      int upper = 0;
      int lower = 0;
      int both = 0;
      int minimum = level.getMaxBuildHeight();
      int maximum = level.getMinBuildHeight();
      int[] profile = new int[17];
      Map<String, Integer> failures = new TreeMap<>();
      JsonArray samples = new JsonArray();
      for (int x = bounds.minX(); ; x = Math.min(x + 4, bounds.maxX())) {
        for (int z = bounds.minZ(); ; z = Math.min(z + 4, bounds.maxZ())) {
          NoiseColumn column =
              level
                  .getChunkSource()
                  .getGenerator()
                  .getBaseColumn(x, z, level, level.getChunkSource().randomState());
          boolean top = solid(column.getBlock(floor - 1));
          boolean deep = solid(column.getBlock(floor - 4));
          if (top) upper++;
          if (deep) lower++;
          if (top && deep) both++;
          for (int offset = -8; offset <= 8; offset++)
            if (solid(column.getBlock(floor + offset))) profile[offset + 8]++;
          int height = level.getMaxBuildHeight();
          while (height > level.getMinBuildHeight()
              && !Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(column.getBlock(height - 1)))
            height--;
          minimum = Math.min(minimum, height);
          maximum = Math.max(maximum, height);
          if (!(top && deep)) {
            String failure =
                height == level.getMinBuildHeight()
                    ? "empty_natural_column"
                    : height < floor
                        ? "natural_surface_below_floor"
                        : top
                            ? "cavity_or_thin_shell_below_support"
                            : "higher_natural_surface_with_floor_gap";
            failures.merge(failure, 1, Integer::sum);
          }
          JsonObject sample = new JsonObject();
          sample.addProperty("x", x);
          sample.addProperty("z", z);
          sample.addProperty("natural_first_free_height", height);
          sample.addProperty("floor_minus_1", block(column.getBlock(floor - 1)));
          sample.addProperty("floor_minus_4", block(column.getBlock(floor - 4)));
          sample.addProperty("strict_support", top && deep);
          samples.add(sample);
          if (z == bounds.maxZ()) break;
        }
        if (x == bounds.maxX()) break;
      }
      row.addProperty("root_floor_y", floor);
      row.addProperty("support_samples", samples.size());
      row.addProperty("support_floor_minus_1", upper);
      row.addProperty("support_floor_minus_4", lower);
      row.addProperty("support_both", both);
      row.addProperty("natural_first_free_min", minimum);
      row.addProperty("natural_first_free_max", maximum);
      JsonObject coverage = new JsonObject();
      for (int offset = -8; offset <= 8; offset++)
        coverage.addProperty(Integer.toString(offset), profile[offset + 8]);
      row.add("solid_samples_by_floor_offset", coverage);
      JsonObject failureCounts = new JsonObject();
      failures.forEach(failureCounts::addProperty);
      row.add("strict_failures_by_terrain", failureCounts);
      row.add("root_grid", samples);
    }

    private void count(String stage) {
      counts.merge(stage, 1, Integer::sum);
    }

    private void finish(String reason) {
      done = true;
      report.addProperty("termination", reason);
      report.addProperty("enumerated", enumerated);
      report.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000L);
      JsonObject totals = new JsonObject();
      counts.forEach(totals::addProperty);
      report.add("counts", totals);
      report.add("candidates", candidates);
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot write outpost terrain report " + output, exception);
      }
      LogUtils.getLogger()
          .info(
              "Outpost terrain diagnostic completed: candidates={} counts={} report={}",
              enumerated,
              counts,
              output);
    }
  }

  private static String block(BlockState state) {
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  private static JsonArray bounds(BoundingBox bounds) {
    JsonArray result = new JsonArray();
    for (int value :
        new int[] {
          bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        }) result.add(value);
    return result;
  }
}
