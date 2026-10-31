package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.StructureBiomeCompatibility;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Compares native ring placement and prefit eligibility without generating or changing chunks. */
public final class StrongholdBiomeRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final ChunkPos OLD_SITE = new ChunkPos(160, -273);
  private static final int SAMPLED_RINGS_PER_MODE = 12;

  private StrongholdBiomeRegression() {}

  public static void verify(ServerLevel level) {
    if (!level.getServer().isDedicatedServer() || level.dimension() != Level.OVERWORLD)
      throw new IllegalStateException("Stronghold biome diagnostic needs a dedicated Overworld");
    Audit audit = new Audit(level);
    NeoForge.EVENT_BUS.addListener(audit::tick);
    LOGGER.info(
        "Stronghold biome diagnostic started: seed={} output={}", level.getSeed(), audit.output);
  }

  private static Climate.Sampler unmasked(Climate.Sampler caller) {
    IslandBiomeSampler original = (IslandBiomeSampler) (Object) caller;
    if (original.aeroscapesReimagine$terrainMask() == null)
      throw new IllegalStateException("Diagnostic requires a terrain-mask binding");
    Climate.Sampler copy =
        new Climate.Sampler(
            caller.temperature(),
            caller.humidity(),
            caller.continentalness(),
            caller.erosion(),
            caller.depth(),
            caller.weirdness(),
            caller.spawnTarget());
    IslandBiomeSampler context = (IslandBiomeSampler) (Object) copy;
    context.aeroscapesReimagine$terrainMask(original.aeroscapesReimagine$terrainMask());
    context.aeroscapesReimagine$deferTerrainMask(true);
    return copy;
  }

  private static final class UnmaskedRingSource extends BiomeSource {
    private final BiomeSource delegate;

    private UnmaskedRingSource(BiomeSource delegate) {
      this.delegate = delegate;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
      throw new UnsupportedOperationException(
          "Invocation-local diagnostic source is not serialized");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return delegate.possibleBiomes().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
      return delegate.getNoiseBiome(x, y, z, sampler);
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x,
        int y,
        int z,
        int radius,
        Predicate<Holder<Biome>> predicate,
        RandomSource random,
        Climate.Sampler sampler) {
      // Keep the actual outer source's native traversal and this ring's original RNG object.
      return delegate.findBiomeHorizontal(x, y, z, radius, predicate, random, unmasked(sampler));
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Structure structure;
    private final StrongholdStructure nativeStructure;
    private final BiomeSource source;
    private final Climate.Sampler caller;
    private final Climate.Sampler deferred;
    private final boolean strict =
        Boolean.getBoolean("aeroscapes.regression.strongholdBiomeStrict");
    private final Path output =
        Path.of(
                System.getProperty(
                    "aeroscapes.regression.strongholdBiomeReport",
                    ".runtime/stronghold-biome.json"))
            .toAbsolutePath()
            .normalize();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private final long deadline = System.nanoTime() + 120_000_000_000L;
    private final CompletableFuture<List<ChunkPos>> actualRings;
    private final CompletableFuture<List<ChunkPos>> unmaskedRings;
    private List<ChunkPos> positions;
    private int cursor;
    private boolean done;

    private Audit(ServerLevel level) {
      this.level = level;
      if (strict && level.getSeed() != 1L)
        throw new IllegalStateException("Strict stronghold biome regression requires seed 1");
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      Holder.Reference<Structure> stronghold =
          registry.getHolder(ResourceLocation.withDefaultNamespace("stronghold")).orElseThrow();
      structure = stronghold.value();
      var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
      JsonObject serialized =
          Structure.DIRECT_CODEC.encodeStart(operations, structure).getOrThrow().getAsJsonObject();
      if (!serialized.has("delegate"))
        throw new IllegalStateException("Expected the actual stronghold's supported delegate");
      Structure delegate =
          Structure.DIRECT_CODEC.parse(operations, serialized.get("delegate")).getOrThrow();
      if (!(delegate instanceof StrongholdStructure strongholdDelegate))
        throw new IllegalStateException("Expected the pinned native StrongholdStructure delegate");
      nativeStructure = strongholdDelegate;
      source = level.getChunkSource().getGenerator().getBiomeSource();
      caller = level.getChunkSource().randomState().sampler();
      if (((IslandBiomeSampler) (Object) caller).aeroscapesReimagine$deferTerrainMask())
        throw new IllegalStateException("Diagnostic caller sampler must retain its terrain mask");
      deferred = unmasked(caller);
      var state = level.getChunkSource().getGeneratorState();
      ConcentricRingsStructurePlacement placement =
          state.getPlacementsForStructure(stronghold).stream()
              .filter(ConcentricRingsStructurePlacement.class::isInstance)
              .map(ConcentricRingsStructurePlacement.class::cast)
              .findFirst()
              .orElseThrow();
      actualRings = state.ringPositions.get(placement);
      if (actualRings == null) throw new IllegalStateException("Actual ring future is missing");
      var comparison =
          ChunkGeneratorStructureState.createForNormal(
              level.getChunkSource().randomState(),
              level.getSeed(),
              new UnmaskedRingSource(source),
              level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup());
      comparison.getPlacementsForStructure(stronghold);
      unmaskedRings = comparison.ringPositions.get(placement);
      if (unmaskedRings == null)
        throw new IllegalStateException("Unmasked comparison ring future is missing");
      report.addProperty("format", 1);
      report.addProperty("seed", level.getSeed());
      report.addProperty("strict", strict);
      report.addProperty("old_site_reference_seed", 1);
      report.addProperty("source_class", source.getClass().getName());
      report.add("actual_structure", serialized);
      report.addProperty("sampled_first_rings_per_mode", SAMPLED_RINGS_PER_MODE);
      report.addProperty("deadline_seconds", 120);
      report.addProperty(
          "scope",
          "Actual native ring futures versus a separate native structure state with only the ring"
              + " search sampler deferred; same world seed, structure sets, source and native RNG."
              + " Prefit samples query the native stub with masked/unmasked samplers. Only the old"
              + " accepted site also tests the current supported wrapper with/without the native"
              + " biome predicate; its fit still uses actual masked world state. No chunks are"
              + " generated. Climate nearest-point ties may depend on native worker history; this"
              + " finite comparison does not establish global stronghold absence.");
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != level.getServer()) return;
      try {
        if (System.nanoTime() > deadline)
          throw new IllegalStateException("Stronghold biome diagnostic exceeded 120 seconds");
        verifyIsolation();
        if (positions == null) {
          if (!actualRings.isDone() || !unmaskedRings.isDone()) return;
          List<ChunkPos> actual = actualRings.join();
          List<ChunkPos> comparison = unmaskedRings.join();
          report.add("actual_rings", chunks(actual));
          report.add("unmasked_rings", chunks(comparison));
          report.addProperty("actual_contains_old_site", actual.contains(OLD_SITE));
          report.addProperty("unmasked_contains_old_site", comparison.contains(OLD_SITE));
          report.addProperty("identical_ordered_rings", actual.equals(comparison));
          if (strict) {
            require(actual.equals(comparison), "Actual and unmasked native rings differ");
            require(
                actual.contains(OLD_SITE), "Actual rings lost the old accepted stronghold site");
            require(comparison.contains(OLD_SITE), "Unmasked reference lost the accepted site");
          }
          Set<ChunkPos> planned = new LinkedHashSet<>();
          planned.add(OLD_SITE);
          planned.addAll(actual.subList(0, Math.min(SAMPLED_RINGS_PER_MODE, actual.size())));
          planned.addAll(
              comparison.subList(0, Math.min(SAMPLED_RINGS_PER_MODE, comparison.size())));
          positions = new ArrayList<>(planned);
          return;
        }
        if (cursor < positions.size()) {
          sample(positions.get(cursor++));
          return;
        }
        finish("complete", null);
        LOGGER.info("Stronghold biome regression passed: samples={} output={}", cursor, output);
      } catch (RuntimeException exception) {
        finish("error", exception.getMessage());
        throw exception;
      }
    }

    private void sample(ChunkPos position) {
      var context = context(position, structure.biomes()::contains);
      var stub = nativeStructure.findGenerationPoint(context).orElseThrow();
      BlockPos point = stub.position();
      Holder<Biome> masked = biome(point, caller);
      Holder<Biome> unmasked = biome(point, deferred);
      JsonObject sample = new JsonObject();
      samples.add(sample);
      sample.addProperty("chunk_x", position.x);
      sample.addProperty("chunk_z", position.z);
      sample.addProperty("native_stub", point.toShortString());
      sample.addProperty("masked_biome", id(masked));
      sample.addProperty("unmasked_biome", id(unmasked));
      sample.addProperty("masked_native_eligible", context.validBiome().test(masked));
      sample.addProperty("unmasked_native_eligible", context.validBiome().test(unmasked));
      sample.addProperty(
          "actual_native_findValidGenerationPoint",
          nativeStructure.findValidGenerationPoint(context).isPresent());
      if (position.equals(OLD_SITE)) {
        var actualContext = context(position, structure.biomes()::contains);
        var supported = structure.findValidGenerationPoint(actualContext);
        sample.addProperty("actual_supported_prefit_and_fit", supported.isPresent());
        var original = context(position, structure.biomes()::contains);
        var scoped = StructureBiomeCompatibility.prefitContext(original);
        boolean sameRandom = scoped.random() == original.random();
        sample.addProperty("production_prefit_keeps_rng_identity", sameRandom);
        boolean otherContextUnchanged =
            scoped.registryAccess() == original.registryAccess()
                && scoped.chunkGenerator() == original.chunkGenerator()
                && scoped.randomState() == original.randomState()
                && scoped.structureTemplateManager() == original.structureTemplateManager()
                && scoped.seed() == original.seed()
                && scoped.chunkPos() == original.chunkPos()
                && scoped.heightAccessor() == original.heightAccessor()
                && scoped.validBiome() == original.validBiome();
        sample.addProperty("production_prefit_other_context_preserved", otherContextUnchanged);
        boolean scopedNative = nativeStructure.findValidGenerationPoint(scoped).isPresent();
        sample.addProperty("scoped_native_eligible", scopedNative);
        var bypass =
            new Structure.GenerationContext(
                original.registryAccess(),
                original.chunkGenerator(),
                original.biomeSource(),
                original.randomState(),
                original.structureTemplateManager(),
                original.random(),
                original.seed(),
                original.chunkPos(),
                original.heightAccessor(),
                ignored -> true);
        sample.addProperty(
            "copied_context_keeps_rng_identity", bypass.random() == original.random());
        var fit = structure.findValidGenerationPoint(bypass);
        sample.addProperty("supported_fit_with_native_biome_predicate_bypassed", fit.isPresent());
        if (fit.isPresent()) {
          var fitted = fit.orElseThrow();
          var pieces = fitted.getPiecesBuilder();
          sample.addProperty("fitted_position", fitted.position().toShortString());
          sample.addProperty("fitted_bounds", pieces.getBoundingBox().toString());
          sample.addProperty("fitted_piece_count", pieces.build().pieces().size());
        }
        if (strict) {
          require(id(masked).equals("aeroscapes_reimagine:void"), "Actual world lookup lost void");
          require(
              !context.validBiome().test(masked), "Masked native eligibility unexpectedly passed");
          require(context.validBiome().test(unmasked), "Unmasked climate eligibility was lost");
          require(
              sameRandom && otherContextUnchanged, "Prefit context changed native RNG or inputs");
          require(scoped.biomeSource() != original.biomeSource(), "Missing scoped prefit marker");
          require(scopedNative, "Scoped native prefit still rejects the old accepted site");
          require(
              supported.isPresent(), "Supported native eligibility or fit rejects the old site");
          require(fit.isPresent(), "Native-predicate bypass no longer fits the old site");
          var serialization = StructurePieceSerializationContext.fromLevel(level);
          var actualStub = supported.orElseThrow();
          var referenceStub = fit.orElseThrow();
          var actualPieces =
              actualStub.getPiecesBuilder().build().pieces().stream()
                  .map(piece -> piece.createTag(serialization))
                  .toList();
          var referencePieces =
              referenceStub.getPiecesBuilder().build().pieces().stream()
                  .map(piece -> piece.createTag(serialization))
                  .toList();
          boolean sameAssembly =
              actualStub.position().equals(referenceStub.position())
                  && actualPieces.equals(referencePieces);
          boolean sameRandomTail = true;
          for (int index = 0; index < 8; index++)
            sameRandomTail &= actualContext.random().nextLong() == original.random().nextLong();
          sample.addProperty("supported_vs_bypass_same_assembly", sameAssembly);
          sample.addProperty("supported_vs_bypass_same_rng_tail", sameRandomTail);
          require(
              sameAssembly && sameRandomTail, "Scoped prefit changed assembly or native RNG draws");
          require(
              id(biome(point, caller)).equals(id(masked)), "Scoped checks changed actual biome");
          verifyIsolation();
        }
      }
      LOGGER.info("Stronghold biome diagnostic sample {}", sample);
    }

    private Structure.GenerationContext context(
        ChunkPos position, Predicate<Holder<Biome>> predicate) {
      return new Structure.GenerationContext(
          level.registryAccess(),
          level.getChunkSource().getGenerator(),
          source,
          level.getChunkSource().randomState(),
          level.getStructureManager(),
          level.getSeed(),
          position,
          level,
          predicate);
    }

    private Holder<Biome> biome(BlockPos point, Climate.Sampler sampler) {
      return source.getNoiseBiome(
          QuartPos.fromBlock(point.getX()),
          QuartPos.fromBlock(point.getY()),
          QuartPos.fromBlock(point.getZ()),
          sampler);
    }

    private void verifyIsolation() {
      if (!isolationPreserved())
        throw new IllegalStateException("Diagnostic changed caller sampler or lost its mask");
    }

    private boolean isolationPreserved() {
      var original = (IslandBiomeSampler) (Object) caller;
      var copy = (IslandBiomeSampler) (Object) deferred;
      return caller == level.getChunkSource().randomState().sampler()
          && !original.aeroscapesReimagine$deferTerrainMask()
          && copy.aeroscapesReimagine$deferTerrainMask()
          && original.aeroscapesReimagine$terrainMask() == copy.aeroscapesReimagine$terrainMask();
    }

    private void finish(String status, String failure) {
      done = true;
      report.addProperty("status", status);
      report.addProperty("caller_sampler_preserved", isolationPreserved());
      if (failure != null) report.addProperty("failure", failure);
      report.add("prefit_samples", samples);
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Cannot write stronghold biome diagnostic " + output, exception);
      }
    }
  }

  private static JsonArray chunks(List<ChunkPos> positions) {
    JsonArray result = new JsonArray();
    for (ChunkPos position : positions) {
      JsonArray coordinates = new JsonArray();
      coordinates.add(position.x);
      coordinates.add(position.z);
      result.add(coordinates);
    }
    return result;
  }

  private static String id(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }
}
