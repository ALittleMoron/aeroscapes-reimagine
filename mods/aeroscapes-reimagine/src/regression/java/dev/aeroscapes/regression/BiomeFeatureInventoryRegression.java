package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Captures effective feature wiring without requesting chunks or attempting feature placement. */
public final class BiomeFeatureInventoryRegression {
  private static final List<String> BLOCKS =
      List.of(
          "farmersdelight:wild_cabbages",
          "farmersdelight:wild_beetroots",
          "farmersdelight:wild_carrots",
          "farmersdelight:wild_onions",
          "farmersdelight:wild_potatoes",
          "farmersdelight:wild_tomatoes",
          "farmersdelight:wild_rice",
          "farmersdelight:sandy_shrub",
          "farmersdelight:brown_mushroom_colony",
          "farmersdelight:red_mushroom_colony",
          "farmersdelight:cabbages",
          "farmersdelight:onions",
          "farmersdelight:tomatoes",
          "farmersdelight:budding_tomatoes",
          "farmersdelight:rice",
          "farmersdelight:rice_panicles",
          "fungidelight:inky_cap_mushroom",
          "fungidelight:morel_mushroom",
          "fungidelight:inky_cap_mushroom_colony",
          "fungidelight:morel_mushroom_colony",
          "fungidelight:truffle_dirt",
          "fungidelight:inky_cap_mushroom_block",
          "fungidelight:inky_cap_mushroom_edge",
          "fungidelight:inky_cap_mushroom_stem",
          "fungidelight:morel_mushroom_block",
          "fungidelight:morel_mushroom_stem",
          "natures_delight:shiitake_mushroom_colony",
          "natures_spirit:coconut",
          "natures_spirit:olive_branch",
          "natures_spirit:shiitake_mushroom",
          "natures_spirit:shiitake_mushroom_block",
          "natures_spirit:desert_turnip_block",
          "natures_spirit:desert_turnip_root_block",
          "natures_spirit:desert_turnip_stem",
          "natures_spirit:green_bearberries",
          "natures_spirit:red_bearberries",
          "natures_spirit:purple_bearberries",
          "supplementaries:wild_flax",
          "supplementaries:flax",
          "supplementaries:flax_block",
          "quark:glow_shroom",
          "quark:glow_shroom_block",
          "quark:glow_shroom_stem",
          "quark:glow_shroom_ring",
          "quark:ancient_sapling",
          "quark:ancient_leaves",
          "galosphere:lichen_cordyceps",
          "galosphere:lichen_cordyceps_plant",
          "galosphere:pink_salt_cluster");

  private BiomeFeatureInventoryRegression() {}

  public static void verify(ServerLevel level, Path output) {
    if (!level.getServer().isDedicatedServer() || !level.getServer().isSameThread())
      throw new IllegalStateException(
          "Feature inventory needs the disposable dedicated server thread");
    var operations =
        RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
    JsonArray codecErrors = new JsonArray();
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("seed", level.getSeed());
    report.addProperty("dimension", level.dimension().location().toString());
    Set<ResourceLocation> possible =
        level
            .getServer()
            .overworld()
            .getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .possibleBiomes()
            .stream()
            .map(holder -> holder.unwrapKey().orElseThrow().location())
            .collect(Collectors.toSet());
    var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
    JsonObject biomes = new JsonObject();
    for (ResourceLocation id : biomeRegistry.keySet().stream().sorted().toList()) {
      Holder<Biome> holder = biomeRegistry.getHolder(id).orElseThrow();
      Biome biome = holder.value();
      JsonObject row = new JsonObject();
      row.addProperty("possible_overworld", possible.contains(id));
      JsonArray tags = new JsonArray();
      holder.tags().map(tag -> tag.location().toString()).sorted().forEach(tags::add);
      row.add("tags", tags);
      row.add(
          "effective_climate",
          encode(
              Biome.ClimateSettings.CODEC.codec(),
              biome.getModifiedClimateSettings(),
              operations,
              "climate:" + id,
              codecErrors));
      JsonElement declared =
          encode(Biome.DIRECT_CODEC, biome, operations, "biome:" + id, codecErrors);
      if (declared.isJsonObject() && declared.getAsJsonObject().has("features"))
        row.add("declared_features", declared.getAsJsonObject().get("features"));
      else row.addProperty("declared_features_unavailable", true);
      JsonArray stages = new JsonArray();
      var effective = biome.getGenerationSettings().features();
      for (int index = 0; index < effective.size(); index++) {
        JsonObject stage = new JsonObject();
        stage.addProperty("index", index);
        stage.addProperty(
            "name",
            index < GenerationStep.Decoration.values().length
                ? GenerationStep.Decoration.values()[index].name().toLowerCase(Locale.ROOT)
                : "unknown_stage_" + index);
        JsonArray features = new JsonArray();
        effective.get(index).forEach(feature -> features.add(describe(feature)));
        stage.add("features", features);
        stages.add(stage);
      }
      row.add("effective_features", stages);
      biomes.add(id.toString(), row);
    }
    report.add("biomes", biomes);
    JsonObject configured = new JsonObject();
    var configuredRegistry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
    for (ResourceLocation id :
        configuredRegistry.keySet().stream()
            .filter(BiomeFeatureInventoryRegression::relevant)
            .sorted()
            .toList())
      configured.add(
          id.toString(),
          encode(
              ConfiguredFeature.DIRECT_CODEC,
              configuredRegistry.get(id),
              operations,
              "configured:" + id,
              codecErrors));
    report.add("target_configured_features", configured);
    JsonObject placed = new JsonObject();
    var placedRegistry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
    for (ResourceLocation id :
        placedRegistry.keySet().stream()
            .filter(BiomeFeatureInventoryRegression::relevant)
            .sorted()
            .toList())
      placed.add(
          id.toString(),
          encode(
              PlacedFeature.DIRECT_CODEC,
              placedRegistry.get(id),
              operations,
              "placed:" + id,
              codecErrors));
    report.add("target_placed_features", placed);
    JsonObject blocks = new JsonObject();
    for (String id : BLOCKS) {
      JsonObject row = new JsonObject();
      var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
      row.addProperty("registered", block.isPresent());
      block.ifPresent(
          value -> {
            row.addProperty("class", value.getClass().getName());
            row.addProperty("default_state", value.defaultBlockState().toString());
          });
      blocks.add(id, row);
    }
    report.add("target_blocks", blocks);
    report.add("codec_errors", codecErrors);
    report.addProperty("status", codecErrors.isEmpty() ? "complete" : "complete_with_codec_errors");
    report.addProperty(
        "limits",
        "Registry and effective feature wiring only; no chunks, tickets or placement."
            + " Zeta deferred_feature_* inline entries are service wrappers, not distinct plants or spawn proof."
            + " Null codec definitions are errors recorded above, never evidence of absent worldgen.");
    try {
      Path destination = output.toAbsolutePath().normalize();
      Files.createDirectories(destination.getParent());
      Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write biome feature inventory " + output, exception);
    }
    LogUtils.getLogger()
        .info(
            "Biome feature inventory regression passed: biomes={} configured={} placed={} codecErrors={} output={}",
            biomes.size(),
            configured.size(),
            placed.size(),
            codecErrors.size(),
            output);
  }

  private static JsonObject describe(Holder<PlacedFeature> holder) {
    JsonObject row = new JsonObject();
    holder.unwrapKey().ifPresent(key -> row.addProperty("reference", key.location().toString()));
    Holder<ConfiguredFeature<?, ?>> configured = holder.value().feature();
    configured
        .unwrapKey()
        .ifPresent(key -> row.addProperty("configured_reference", key.location().toString()));
    ResourceLocation type = BuiltInRegistries.FEATURE.getKey(configured.value().feature());
    row.addProperty("feature_type", type == null ? "unregistered_type" : type.toString());
    row.addProperty(
        "service_wrapper",
        type != null
            && type.getNamespace().equals("zeta")
            && type.getPath().startsWith("deferred_feature_"));
    return row;
  }

  private static boolean relevant(ResourceLocation id) {
    String path = id.getPath();
    return switch (id.getNamespace()) {
      case "farmersdelight", "fungidelight" -> true;
      case "supplementaries" -> Set.of("wild_flax", "wild_flax_patch").contains(path);
      case "natures_spirit" ->
          path.contains("maple")
              || path.contains("shiitake")
              || path.contains("coconut")
              || path.contains("olive")
              || path.contains("desert_turnip")
              || Set.of("patch_tundra_red_vegetation", "red_moss_vegetation").contains(path);
      case "quark" -> Set.of("glow_shrooms", "glow_shrooms_extras", "ancient_tree").contains(path);
      case "galosphere" -> path.startsWith("lichen_") || path.startsWith("pink_salt_");
      default -> false;
    };
  }

  private static <T> JsonElement encode(
      Codec<T> codec,
      T value,
      RegistryOps<JsonElement> operations,
      String context,
      JsonArray errors) {
    var encoded = codec.encodeStart(operations, value);
    encoded
        .error()
        .ifPresent(
            error -> {
              JsonObject failure = new JsonObject();
              failure.addProperty("context", context);
              failure.addProperty("error", error.message());
              errors.add(failure);
            });
    return encoded.result().orElse(JsonNull.INSTANCE);
  }
}
