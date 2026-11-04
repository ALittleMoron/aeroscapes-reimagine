package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

/**
 * Natural source, raw water and saved FULL spawn-list/predicate witnesses, without placing mobs.
 */
public final class FaunaWaterHabitatRegression {
  private enum Depth {
    SHALLOW,
    DEEP,
    SEABED
  }

  private record Species(String id, Depth depth) {}

  private static final List<Species> SPECIES =
      List.of(
          new Species("naturalist:catfish", Depth.SHALLOW),
          new Species("naturalist:piranha", Depth.SHALLOW),
          new Species("crittersandcompanions:koi_fish", Depth.SHALLOW),
          new Species("naturalist:great_white_shark", Depth.SHALLOW),
          new Species("naturalist:anglerfish", Depth.DEEP),
          new Species("naturalist:blobfish", Depth.DEEP),
          new Species("naturalist:giant_isopod", Depth.SEABED));

  private FaunaWaterHabitatRegression() {}

  public static JsonArray run(ServerLevel level) {
    var randomState = level.getChunkSource().randomState();
    IslandTerrain terrain = IslandTerrain.from(randomState);
    require(terrain != null, "Fauna habitats require the owned generator");
    var layout = IslandTerrain.layout(randomState.sampler().continentalness());
    Map<String, JsonObject> found = new LinkedHashMap<>();
    Set<Long> fullChunks = new HashSet<>();
    long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
    for (int cellX = -8; cellX <= 8 && found.size() < SPECIES.size(); cellX++) {
      for (int cellZ = -8; cellZ <= 8 && found.size() < SPECIES.size(); cellZ++) {
        require(
            System.nanoTime() < deadline,
            "Fauna habitat search exceeded three minutes; found=" + found.keySet());
        var candidate = layout.basin(cellX * 1024, cellZ * 1024);
        if (candidate == null) continue;
        var basin = terrain.basin(candidate.centerX(), candidate.centerZ());
        if (basin != null) basin(level, basin, found, fullChunks, deadline);
      }
    }
    var missing = SPECIES.stream().map(Species::id).filter(id -> !found.containsKey(id)).toList();
    require(
        missing.isEmpty(),
        "No natural saved FULL fauna water habitat in bounded basin cohort: " + missing);
    level.getServer().saveAllChunks(true, true, true);
    JsonArray report = new JsonArray();
    for (Species species : SPECIES) {
      JsonObject row = found.get(species.id());
      row.addProperty("saved_full", true);
      report.add(row);
    }
    return report;
  }

  private static void basin(
      ServerLevel level,
      IslandLayout.Basin basin,
      Map<String, JsonObject> found,
      Set<Long> fullChunks,
      long deadline) {
    var generator = level.getChunkSource().getGenerator();
    var randomState = level.getChunkSource().randomState();
    var source = generator.getBiomeSource();
    for (int offsetX = -256; offsetX <= 256 && found.size() < SPECIES.size(); offsetX += 32) {
      require(
          System.nanoTime() < deadline,
          "Fauna basin search exceeded three minutes; found=" + found.keySet());
      for (int offsetZ = -256; offsetZ <= 256 && found.size() < SPECIES.size(); offsetZ += 32) {
        int x = Math.floorDiv(basin.centerX() + offsetX, 4) * 4;
        int z = Math.floorDiv(basin.centerZ() + offsetZ, 4) * 4;
        if (!basin.water(x, basin.waterline(), z)) continue;
        int floor = (int) Math.ceil(basin.floor(x, z));
        for (int y :
            new int[] {
              basin.waterline() - 4,
              basin.waterline() - 8,
              basin.waterline() - 40,
              basin.waterline() - 52,
              floor,
              floor + 1
            }) {
          BlockPos position = new BlockPos(x, y, z);
          var biome =
              source.getNoiseBiome(
                  QuartPos.fromBlock(x),
                  QuartPos.fromBlock(y),
                  QuartPos.fromBlock(z),
                  randomState.sampler());
          var matching =
              SPECIES.stream()
                  .filter(species -> !found.containsKey(species.id()))
                  .filter(species -> depth(species, basin, position))
                  .filter(species -> selected(biome, type(species.id())))
                  .toList();
          if (matching.isEmpty()) continue;
          var column = generator.getBaseColumn(x, z, level, randomState);
          if (!column.getBlock(y).getFluidState().is(FluidTags.WATER)) continue;
          long chunk = ChunkPos.asLong(x >> 4, z >> 4);
          require(
              fullChunks.contains(chunk) || fullChunks.size() < 48,
              "Fauna habitat search exceeded 48 candidate FULL chunks; found=" + found.keySet());
          fullChunks.add(chunk);
          level.getChunkAt(position);
          for (Species species : matching) {
            JsonObject witness = full(level, basin, species, position);
            if (witness == null) continue;
            found.put(species.id(), witness);
            LogUtils.getLogger().info("Fauna natural FULL water witness {}", witness);
          }
        }
      }
    }
  }

  @Nullable
  private static JsonObject full(
      ServerLevel level, IslandLayout.Basin basin, Species species, BlockPos anchor) {
    EntityType<?> type = type(species.id());
    var generator = level.getChunkSource().getGenerator();
    var randomState = level.getChunkSource().randomState();
    for (int offsetY = -2; offsetY <= 2; offsetY++) {
      for (int offsetX = 0; offsetX < 4; offsetX++) {
        for (int offsetZ = 0; offsetZ < 4; offsetZ++) {
          BlockPos position = anchor.offset(offsetX, offsetY, offsetZ);
          if (!depth(species, basin, position)
              || !level.getFluidState(position).is(FluidTags.WATER)) continue;
          var source =
              generator
                  .getBiomeSource()
                  .getNoiseBiome(
                      QuartPos.fromBlock(position.getX()),
                      QuartPos.fromBlock(position.getY()),
                      QuartPos.fromBlock(position.getZ()),
                      randomState.sampler());
          var stored =
              level
                  .getChunkAt(position)
                  .getNoiseBiome(
                      QuartPos.fromBlock(position.getX()),
                      QuartPos.fromBlock(position.getY()),
                      QuartPos.fromBlock(position.getZ()));
          var displayed = level.getBiome(position);
          if (!source.equals(stored) || !stored.equals(displayed) || !selected(displayed, type))
            continue;
          if (!generator
              .getBaseColumn(position.getX(), position.getZ(), level, randomState)
              .getBlock(position.getY())
              .getFluidState()
              .is(FluidTags.WATER)) continue;
          if (!SpawnPlacements.checkSpawnRules(
              type, level, MobSpawnType.NATURAL, position, RandomSource.create(position.asLong())))
            continue;
          double halfWidth = type.getWidth() / 2.0;
          var bounds =
              new AABB(
                  position.getX() + 0.5 - halfWidth,
                  position.getY(),
                  position.getZ() + 0.5 - halfWidth,
                  position.getX() + 0.5 + halfWidth,
                  position.getY() + type.getHeight(),
                  position.getZ() + 0.5 + halfWidth);
          if (!level.noCollision(bounds)) continue;
          JsonObject result = new JsonObject();
          result.addProperty("entity", species.id());
          result.addProperty("depth_role", species.depth().name());
          result.addProperty("biome", displayed.unwrapKey().orElseThrow().location().toString());
          result.addProperty("x", position.getX());
          result.addProperty("y", position.getY());
          result.addProperty("z", position.getZ());
          result.addProperty("waterline", basin.waterline());
          result.addProperty("source_raw_full_and_displayed_agree", true);
          result.addProperty("effective_biome_spawn_entry", true);
          result.addProperty("native_spawn_predicate_and_clear_body", true);
          return result;
        }
      }
    }
    return null;
  }

  private static boolean depth(Species species, IslandLayout.Basin basin, BlockPos position) {
    int depth = basin.waterline() - position.getY();
    return switch (species.depth()) {
      case SHALLOW -> depth >= 2 && depth <= 12;
      case DEEP -> depth >= 33;
      case SEABED -> Math.abs(position.getY() - basin.floor(position.getX(), position.getZ())) <= 3;
    };
  }

  private static EntityType<?> type(String id) {
    return BuiltInRegistries.ENTITY_TYPE
        .getOptional(ResourceLocation.parse(id))
        .orElseThrow(() -> new IllegalStateException("Missing fauna entity " + id));
  }

  private static boolean selected(Holder<Biome> biome, EntityType<?> type) {
    return biome.value().getMobSettings().getMobs(type.getCategory()).unwrap().stream()
        .anyMatch(entry -> entry.type == type && entry.getWeight().asInt() > 0);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
