package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.biome.EcologySurfaceEvidence;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Observe real SURFACE soil before features, then FULL persistence; exercise loaded loot/recipes.
 */
final class GroupTwoEcologyRegression {
  private static final Map<String, List<String>> SOILS =
      Map.of(
          "spawn:deep_warm_ocean", List.of("minecraft:sand"),
          "spawn:rocky_shore",
              List.of(
                  "minecraft:sand",
                  "minecraft:sandstone",
                  "minecraft:sandstone",
                  "minecraft:sandstone"),
          "spawn:seagrass_meadow",
              List.of(
                  "spawn:algal_sand",
                  "minecraft:sand",
                  "minecraft:sand",
                  "minecraft:sand",
                  "minecraft:sandstone",
                  "minecraft:sandstone",
                  "minecraft:sandstone",
                  "minecraft:sandstone",
                  "minecraft:sandstone"));

  private GroupTwoEcologyRegression() {}

  static void run(ServerLevel level, Path plan, Path output) {
    dropsAndCrafting(level);
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    report.addProperty("loaded_drops_and_crafting_passed", true);
    report.add("bird_categories", birdCategories());
    report.add("starting_bird_habitat", birds(level, level.getSharedSpawnPos()));
    if (level.getSeed() == 1) report.add("controlled_lower_snow", lowerSnow(level));
    JsonArray profiles = new JsonArray();
    report.add("profiles", profiles);
    Set<String> sampled = new HashSet<>();
    Set<String> columns = new HashSet<>();
    Set<String> kinds = new HashSet<>();
    boolean matches = true;
    try {
      var data = JsonParser.parseString(Files.readString(plan));
      require(
          data.isJsonObject() && data.getAsJsonObject().get("coverage").isJsonArray(),
          "Invalid coverage plan");
      for (var element : data.getAsJsonObject().getAsJsonArray("coverage")) {
        require(element.isJsonObject(), "Invalid biome plan entry");
        var entry = element.getAsJsonObject();
        String id = entry.get("id").getAsString();
        if (!SOILS.containsKey(id)) continue;
        for (var candidate : entry.getAsJsonArray("raw_witnesses")) {
          require(candidate.isJsonObject(), "Invalid raw witness");
          var position = candidate.getAsJsonObject();
          int x = integer(position, "x");
          int z = integer(position, "z");
          for (int dx = -128; dx <= 128; dx += 32)
            for (int dz = -128; dz <= 128; dz += 32) {
              int sampleX = x + dx;
              int sampleZ = z + dz;
              if (!columns.add(id + ":" + sampleX + ":" + sampleZ)) continue;
              JsonObject profile = profile(level, id, sampleX, sampleZ, kinds);
              if (profile == null) continue;
              profiles.add(profile);
              if (profile.has("native_soil_matches")) {
                sampled.add(id);
                matches &= profile.get("native_soil_matches").getAsBoolean();
              }
            }
        }
      }
      report.addProperty("all_three_sampled", sampled.equals(SOILS.keySet()));
      report.addProperty("native_soil_matches", matches && sampled.equals(SOILS.keySet()));
      level.getServer().saveAllChunks(true, true, true);
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot inspect group two ecology", failure);
    }
    LogUtils.getLogger().info("Group two ecology inspection complete: output={}", output);
    require(matches && sampled.equals(SOILS.keySet()), "Native marine soil profile mismatch");
  }

  @Nullable
  private static JsonObject profile(
      ServerLevel level, String biome, int x, int z, Set<String> kinds) {
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    var terrain = IslandTerrain.from(random);
    require(terrain != null, "Owned terrain required");
    var basin = terrain.basin(x, z);
    if (basin == null || basin.radius(x, z) >= 1.1) return null;
    boolean shore = biome.equals("spawn:rocky_shore");
    if (shore != (basin.floor(x, z) >= basin.waterline())) return null;
    var raw = generator.getBaseColumn(x, z, level, random);
    if (!shore && !raw.getBlock(basin.waterline()).getFluidState().is(FluidTags.WATER)) return null;
    int floor = basin.waterline();
    if (shore) floor = (int) Math.floor(basin.floor(x, z)) + 16;
    while (floor > level.getMinBuildHeight()
        && (raw.getBlock(floor).isAir() || !raw.getBlock(floor).getFluidState().isEmpty())) floor--;
    if (!generator
        .getBiomeSource()
        .getNoiseBiome(x >> 2, floor >> 2, z >> 2, random.sampler())
        .unwrapKey()
        .orElseThrow()
        .location()
        .toString()
        .equals(biome)) return null;
    boolean upper =
        generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random)
            > basin.waterline() + 17;
    String kind = biome + ":" + upper;
    if (kinds.contains(kind)) return null;
    EcologySurfaceEvidence.begin(random, x, z, floor - 8, floor);
    net.minecraft.world.level.chunk.ChunkAccess chunk;
    JsonArray conditions;
    try {
      chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.SURFACE, true);
    } finally {
      conditions = EcologySurfaceEvidence.finish();
    }
    require(chunk != null, "Missing SURFACE chunk");
    JsonObject result = new JsonObject();
    result.addProperty("biome", biome);
    result.add("native_surface_conditions", conditions);
    result.addProperty("x", x);
    result.addProperty("y", floor);
    result.addProperty("z", z);
    result.addProperty("waterline", basin.waterline());
    result.addProperty("under_upper_island", upper);
    var selected =
        chunk
            .getNoiseBiome(x >> 2, floor >> 2, z >> 2)
            .unwrapKey()
            .orElseThrow()
            .location()
            .toString();
    result.addProperty("surface_biome", selected);
    result.addProperty(
        "water_above",
        chunk.getBlockState(new BlockPos(x, floor + 1, z)).getFluidState().is(FluidTags.WATER));
    if (!selected.equals(biome)) {
      result.addProperty("skipped", "Physical top selected a different biome");
      return result;
    }
    kinds.add(kind);
    JsonArray surface = new JsonArray();
    List<String> expected = SOILS.get(biome);
    boolean matches = true;
    for (int offset = 0; offset < expected.size(); offset++) {
      String state =
          BuiltInRegistries.BLOCK
              .getKey(chunk.getBlockState(new BlockPos(x, floor - offset, z)).getBlock())
              .toString();
      surface.add(state);
      matches &= state.equals(expected.get(offset));
    }
    result.add("surface_soil", surface);
    result.addProperty("native_soil_matches", matches);
    var full = level.getChunk(x >> 4, z >> 4);
    result.addProperty(
        "full_biome",
        full.getNoiseBiome(x >> 2, floor >> 2, z >> 2)
            .unwrapKey()
            .orElseThrow()
            .location()
            .toString());
    result.addProperty(
        "full_floor",
        BuiltInRegistries.BLOCK
            .getKey(full.getBlockState(new BlockPos(x, floor, z)).getBlock())
            .toString());
    result.add("birds", birds(level, new BlockPos(x, floor + 1, z)));
    return result;
  }

  private static JsonObject birdCategories() {
    JsonObject categories = new JsonObject();
    for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
      String namespace = entry.getKey().location().getNamespace();
      var category = entry.getValue().getCategory();
      if ((namespace.equals("fowlplay") || namespace.equals("hybrid_birds"))
          && category != MobCategory.MISC)
        categories.addProperty(category.getName(), category.getMaxInstancesPerChunk());
    }
    return categories;
  }

  private static JsonObject birds(ServerLevel level, BlockPos position) {
    JsonObject result = new JsonObject();
    var biome = level.getBiome(position);
    result.addProperty("biome", biome.unwrapKey().orElseThrow().location().toString());
    JsonArray spawns = new JsonArray();
    for (var category : MobCategory.values())
      for (var spawn : biome.value().getMobSettings().getMobs(category).unwrap()) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type);
        if (!id.getNamespace().equals("fowlplay") && !id.getNamespace().equals("hybrid_birds"))
          continue;
        JsonObject entry = new JsonObject();
        entry.addProperty("entity", id.toString());
        entry.addProperty("category", category.getName());
        entry.addProperty("weight", spawn.getWeight().asInt());
        entry.addProperty("minimum", spawn.minCount);
        entry.addProperty("maximum", spawn.maxCount);
        spawns.add(entry);
      }
    result.add("spawns", spawns);
    return result;
  }

  private static JsonObject lowerSnow(ServerLevel level) {
    BlockPos lower = new BlockPos(123, 113, 638);
    level.getChunk(lower);
    int upperY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, lower.getX(), lower.getZ());
    require(upperY - lower.getY() > 16, "Distinct natural upper surface required");
    var initial = level.getBlockState(lower);
    var below = level.getBlockState(lower.below());
    var upper = level.getBlockState(new BlockPos(lower.getX(), upperY, lower.getZ()));
    require(
        below.isFaceSturdy(level, lower.below(), net.minecraft.core.Direction.UP),
        "Natural lower footing");
    long seed = 0;
    for (; seed < 10000; seed++) {
      var random = RandomSource.create(seed);
      int dx = random.nextInt(8) - random.nextInt(8);
      int dz = random.nextInt(8) - random.nextInt(8);
      if (dx == 0 && dz == 0 && random.nextInt(8) == 1) break;
    }
    require(seed < 10000, "Native snowy-feature seed");
    try {
      level.setBlock(lower, Blocks.SNOW.defaultBlockState(), 2);
      var feature =
          level
              .registryAccess()
              .registryOrThrow(Registries.CONFIGURED_FEATURE)
              .get(ResourceLocation.parse("rocks:snowy_geyser"));
      require(
          feature != null
              && feature.place(
                  level, level.getChunkSource().getGenerator(), RandomSource.create(seed), lower),
          "Native snow feature did not place on the selected lower layer");
      var actual = level.getBlockState(lower);
      require(
          BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals("rocks:geyser")
              && actual.getValues().entrySet().stream()
                  .anyMatch(
                      entry ->
                          entry.getKey().getName().equals("snowy")
                              && Boolean.TRUE.equals(entry.getValue())),
          "Expected snowy geyser at lower snow");
      require(
          level.getBlockState(new BlockPos(lower.getX(), upperY, lower.getZ())).equals(upper),
          "Upper layer changed");
      require(
          Block.getDrops(actual, level, lower, null, null, ItemStack.EMPTY).isEmpty(),
          "Native geyser should have no drops");
      JsonObject result = new JsonObject();
      result.addProperty("x", lower.getX());
      result.addProperty("y", lower.getY());
      result.addProperty("z", lower.getZ());
      result.addProperty("native_upper_y", upperY);
      result.addProperty("controlled_snow_setup", true);
      result.addProperty("native_feature_passed", true);
      return result;
    } finally {
      level.setBlock(lower, initial, 2);
      level.setBlock(lower.below(), below, 2);
    }
  }

  private static void dropsAndCrafting(ServerLevel level) {
    for (var entry :
        Map.of(
                "rock",
                "rocks:cobblestone_splitter",
                "red_sand_rock",
                "rocks:red_sandstone_splitter",
                "gravel_rock",
                "minecraft:flint",
                "bamboo_stick",
                "minecraft:bamboo",
                "pinecone",
                "minecraft:spruce_sapling")
            .entrySet()) {
      var state =
          BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("rocks:" + entry.getKey()))
              .defaultBlockState();
      var drops =
          Block.getDrops(state, level, level.getSharedSpawnPos(), null, null, ItemStack.EMPTY);
      require(
          drops.size() == 1
              && drops.getFirst().getCount() == 1
              && BuiltInRegistries.ITEM
                  .getKey(drops.getFirst().getItem())
                  .toString()
                  .equals(entry.getValue()),
          "Wrong native debris loot: " + entry.getKey());
    }
    var splitter =
        BuiltInRegistries.ITEM.get(ResourceLocation.parse("rocks:red_sandstone_splitter"));
    var input =
        CraftingInput.of(
            2,
            2,
            java.util.stream.IntStream.range(0, 4)
                .mapToObj(ignored -> new ItemStack(splitter))
                .toList());
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    var result = recipe.value().assemble(input, level.registryAccess());
    require(
        result.is(net.minecraft.world.item.Items.RED_SANDSTONE) && result.getCount() == 1,
        "Native four-splitter recipe");
    require(
        recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
        "Unexpected splitter remainder");
  }

  private static int integer(JsonObject object, String name) {
    var value = object.get(name);
    require(
        value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
        "Expected integer " + name);
    return value.getAsBigDecimal().intValueExact();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
