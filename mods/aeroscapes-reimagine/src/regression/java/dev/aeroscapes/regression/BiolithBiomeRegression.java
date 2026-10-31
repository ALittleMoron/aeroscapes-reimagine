package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandBiomeSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.mixin.MultiNoiseBiomeSourceAccess;
import terrablender.worldgen.IExtendedParameterList;

/** Observes live Biolith/TerraBlender state and calibrates fresh, uninstalled source objects. */
public final class BiolithBiomeRegression {
  private static final String INTERFACE =
      "com.terraformersmc.biolith.impl.biome.InterfaceBiomeSource";
  private static final String INJECTOR =
      "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource";
  private static final String BLUEPRINT =
      "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSource";
  private static final String WEALD = "quark:glimmering_weald";

  private BiolithBiomeRegression() {}

  public static void verify(ServerLevel level, Path output) {
    require(level.getServer().isDedicatedServer(), "Disposable dedicated server only");
    require(level.getServer().isSameThread(), "Run diagnostic on the server thread");
    require(
        output.isAbsolute() && output.normalize().toString().contains("/.runtime/"),
        "Diagnostic output must be absolute in ignored .runtime");
    try {
      Class<?> api = Class.forName(INTERFACE);
      Method dimension = api.getMethod("biolith$getDimensionType");
      Method setDimension = api.getMethod("biolith$setDimensionType", ResourceKey.class);
      Method entries = api.getMethod("biolith$getBiomeEntries");
      BiomeSource outer = level.getChunkSource().getGenerator().getBiomeSource();
      JsonArray unwrapSteps = new JsonArray();
      IslandBiomeSource owned = ownedSource(outer, unwrapSteps);
      MultiNoiseBiomeSource delegate = delegate(owned);
      Climate.ParameterList<?> raw = rawParameters(delegate);
      Object effective = entries.invoke(delegate);
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("seed", level.getSeed());
      report.addProperty("outer_source", outer.getClass().getName());
      report.add("source_unwrap_steps", unwrapSteps);
      report.add("live_delegate", summary(delegate, dimension, entries));
      report.addProperty("outer_possible_contains_weald", containsWeald(outer));
      report.addProperty("owned_possible_contains_weald", containsWeald(owned));

      IslandBiomeSource generation = owned.forGeneration();
      MultiNoiseBiomeSource generationDelegate = delegate(generation);
      Object generationEffective = entries.invoke(generationDelegate);
      Climate.ParameterList<?> generationRaw = rawParameters(generationDelegate);
      report.add("generation_delegate", summary(generationDelegate, dimension, entries));
      report.addProperty("generation_wrapper_is_original", generation == owned);
      report.addProperty("generation_delegate_is_original", generationDelegate == delegate);
      report.addProperty(
          "generation_raw_parameters_are_original", rawParameters(generationDelegate) == raw);
      report.addProperty(
          "generation_effective_cache_is_original", generationEffective == effective);
      boolean strict = Boolean.getBoolean("aeroscapes.regression.biolithVerify");
      JsonObject checks = new JsonObject();
      checks.addProperty(
          "live_overworld", BuiltinDimensionTypes.OVERWORLD.equals(dimension.invoke(delegate)));
      checks.addProperty("live_weald_possible", containsWeald(outer) && containsWeald(owned));
      checks.addProperty("live_effective_weald_entry", wealdEntry(effective));
      checks.addProperty("live_effective_TB_initialized", initialized(effective));
      checks.addProperty(
          "generation_overworld",
          BuiltinDimensionTypes.OVERWORLD.equals(dimension.invoke(generationDelegate)));
      checks.addProperty(
          "generation_source_isolated", generation != owned && generationDelegate != delegate);
      checks.addProperty("generation_effective_cache_isolated", generationEffective != effective);
      checks.addProperty(
          "generation_raw_and_effective_bound",
          generationRaw == generationEffective && generationRaw != raw);
      checks.addProperty("generation_effective_TB_initialized", initialized(generationEffective));
      checks.addProperty("generation_effective_weald_entry", wealdEntry(generationEffective));
      checks.addProperty(
          "generation_possible_biomes_preserved",
          generation.possibleBiomes().equals(owned.possibleBiomes()));
      boolean contractPassed =
          checks.entrySet().stream().allMatch(entry -> entry.getValue().getAsBoolean());
      report.add("integration_contract", checks);
      report.addProperty("strict_verification_requested", strict);
      report.addProperty("integration_contract_passed", contractPassed);
      report.addProperty("diagnostic_stage", "live_snapshot");
      write(output, report);

      MultiNoiseBiomeSource early =
          MultiNoiseBiomeSource.createFromList(
              new Climate.ParameterList<>(rawParameters(delegate).values()));
      setDimension.invoke(early, BuiltinDimensionTypes.OVERWORLD);
      boolean earlyWeald = containsWeald(early);
      report.add("fresh_early_dimension_assignment", summary(early, dimension, entries));
      report.addProperty("fresh_early_possible_contains_weald", earlyWeald);

      MultiNoiseBiomeSource late =
          MultiNoiseBiomeSource.createFromList(
              new Climate.ParameterList<>(rawParameters(delegate).values()));
      boolean beforeLate = containsWeald(late);
      Object cachedBeforeLate = entries.invoke(late);
      setDimension.invoke(late, BuiltinDimensionTypes.OVERWORLD);
      report.add("fresh_late_dimension_assignment", summary(late, dimension, entries));
      report.addProperty("fresh_late_possible_before", beforeLate);
      report.addProperty("fresh_late_possible_after", containsWeald(late));
      report.addProperty("fresh_late_cache_unchanged", entries.invoke(late) == cachedBeforeLate);
      report.addProperty(
          "limits",
          "Fresh calibration sources are not installed in a generator and their TerraBlender regions are not initialized. This diagnostic proves registration/cache boundaries, not natural weald selection or FULL generation.");
      report.addProperty("diagnostic_stage", "complete");
      write(output, report);
      require(
          earlyWeald,
          "Fresh early-assigned source did not include enabled Glimmering Weald; inspect "
              + output);
      require(
          !strict || contractPassed,
          "Biolith biome integration contract failed; inspect " + output);
      if (strict)
        LogUtils.getLogger().info("Biolith biome integration regression passed: output={}", output);
      LogUtils.getLogger()
          .info("Biolith biome diagnostic complete: seed={} output={}", level.getSeed(), output);
    } catch (ReflectiveOperationException | IOException failure) {
      throw new IllegalStateException("Cannot inspect pinned Biolith biome integration", failure);
    }
  }

  private static IslandBiomeSource ownedSource(BiomeSource source, JsonArray unwrapSteps)
      throws ReflectiveOperationException {
    for (int depth = 0; depth < 8; depth++) {
      unwrapSteps.add(source.getClass().getName());
      if (source instanceof IslandBiomeSource owned) return owned;
      Object inner;
      if (source.getClass().getName().equals(INJECTOR)) {
        inner = source.getClass().getMethod("directDelegate").invoke(source);
      } else if (source.getClass().getName().equals(BLUEPRINT)) {
        // Blueprint has no public getter; Lithostitched's public bridge unwraps its exact field.
        Class<?> injector = Class.forName(INJECTOR);
        Object bridge = injector.getConstructor(BiomeSource.class).newInstance(source);
        inner = injector.getMethod("rootDelegate").invoke(bridge);
      } else {
        throw new IllegalStateException("Unexpected biome wrapper " + source.getClass().getName());
      }
      require(inner instanceof BiomeSource, "Wrapper returned no biome source");
      source = (BiomeSource) inner;
    }
    throw new IllegalStateException("Too many nested biome source wrappers");
  }

  private static MultiNoiseBiomeSource delegate(IslandBiomeSource source)
      throws ReflectiveOperationException {
    // Inspect the owned private field instead of adding a production-only diagnostic API.
    Field field = IslandBiomeSource.class.getDeclaredField("delegate");
    field.setAccessible(true);
    Object value = field.get(source);
    require(value instanceof MultiNoiseBiomeSource, "Owned delegate is not MultiNoise");
    return (MultiNoiseBiomeSource) value;
  }

  private static Climate.ParameterList<Holder<Biome>> rawParameters(MultiNoiseBiomeSource source) {
    return ((MultiNoiseBiomeSourceAccess) source)
        .getParameters()
        .map(value -> value, holder -> holder.value().parameters());
  }

  private static JsonObject summary(MultiNoiseBiomeSource source, Method dimension, Method entries)
      throws ReflectiveOperationException {
    Object assigned = dimension.invoke(source);
    JsonObject result = new JsonObject();
    if (assigned == null) {
      result.add("dimension_type", JsonNull.INSTANCE);
      result.addProperty("dimension_state", "null_unassigned");
    } else {
      require(assigned instanceof ResourceKey<?>, "Invalid Biolith dimension key");
      String id = ((ResourceKey<?>) assigned).location().toString();
      result.addProperty("dimension_type", id);
      result.addProperty(
          "dimension_state", id.equals("biolith:undefined") ? "undefined" : "assigned");
    }
    Climate.ParameterList<?> raw = rawParameters(source);
    result.add("raw_parameters", parameters(raw));
    Object effective = entries.invoke(source);
    if (effective == null) {
      result.addProperty("effective_cache_populated", false);
    } else {
      require(effective instanceof Climate.ParameterList<?>, "Invalid Biolith parameter cache");
      result.addProperty("effective_cache_populated", true);
      result.addProperty("raw_and_effective_same_object", raw == effective);
      result.add("effective_parameters", parameters((Climate.ParameterList<?>) effective));
    }
    return result;
  }

  private static JsonObject parameters(Climate.ParameterList<?> parameters) {
    JsonObject result = new JsonObject();
    result.addProperty("entry_count", parameters.values().size());
    JsonArray matches = new JsonArray();
    for (var pair : parameters.values()) {
      require(pair.getSecond() instanceof Holder<?>, "Climate entry is not a biome holder");
      Holder<?> holder = (Holder<?>) pair.getSecond();
      require(holder.value() instanceof Biome, "Climate entry holder is not a biome");
      String id = holder.unwrapKey().orElseThrow().location().toString();
      if (id.equals(WEALD)) {
        JsonObject entry = new JsonObject();
        entry.addProperty("biome", id);
        entry.addProperty("parameters", pair.getFirst().toString());
        matches.add(entry);
      }
    }
    result.add("weald_entries", matches);
    if (parameters instanceof IExtendedParameterList<?> extension) {
      result.addProperty("terrablender_initialized", extension.isInitialized());
      if (extension.isInitialized())
        result.addProperty("terrablender_tree_count", extension.getTreeCount());
    }
    return result;
  }

  private static boolean containsWeald(BiomeSource source) {
    return source.possibleBiomes().stream()
        .anyMatch(holder -> holder.unwrapKey().orElseThrow().location().toString().equals(WEALD));
  }

  private static boolean initialized(Object parameters) {
    return parameters instanceof IExtendedParameterList<?> extension
        && extension.isInitialized()
        && extension.getTreeCount() == Regions.getCount(RegionType.OVERWORLD);
  }

  private static boolean wealdEntry(Object value) {
    if (!(value instanceof Climate.ParameterList<?> parameters)) return false;
    return parameters.values().stream()
        .anyMatch(
            pair ->
                pair.getSecond() instanceof Holder<?> holder
                    && holder.unwrapKey().orElseThrow().location().toString().equals(WEALD));
  }

  private static void write(Path output, JsonObject report) throws IOException {
    Files.createDirectories(output.getParent());
    Files.writeString(
        output,
        new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report) + "\n");
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }
}
