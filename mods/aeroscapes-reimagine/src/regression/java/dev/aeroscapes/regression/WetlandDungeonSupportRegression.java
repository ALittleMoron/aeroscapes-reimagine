package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.RuntimeJsonPatches;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;
import uwu.juni.wetland_whimsy.content.blocks.entities.AncientPotBlockEntity;

/**
 * Same-origin native dungeon identity, real pot rewards and the existing anchored support guards.
 */
final class WetlandDungeonSupportRegression {
  private static final ResourceLocation ID = ResourceLocation.parse("wetland_whimsy:swamp_dungeon");
  private static final String RESOURCE = "wetland_whimsy:worldgen/structure/swamp_dungeon.json";

  private WetlandDungeonSupportRegression() {}

  static JsonObject verify(ServerLevel level, StructureStart saved) {
    try {
      var provider = ModList.get().getModFileById("wetland_whimsy");
      require(provider != null, "Wetland dungeon provider is absent");
      var recipe = RuntimeJsonPatches.readRecipes().getAsJsonObject(RESOURCE);
      byte[] original =
          Files.readAllBytes(provider.getFile().findResource(recipe.get("source").getAsString()));
      require(
          RuntimeJsonPatches.sha256(original).equals(recipe.get("sha256").getAsString()),
          "Wetland dungeon source changed");
      JsonObject nativeDefinition =
          JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
      Structure nativeDungeon = decode(level, nativeDefinition);
      Structure previous = decode(level, wrapper(nativeDefinition, "template_surface"));
      Structure current = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ID);
      require(current != null, "Current wetland dungeon is absent");
      JsonObject encoded =
          Structure.DIRECT_CODEC
              .encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), current)
              .getOrThrow()
              .getAsJsonObject();
      require(
          encoded.get("terrain_check").getAsString().equals("anchored_complex"),
          "Wetland dungeon did not use the established anchored-complex guard");
      ChunkPos origin = saved.getChunkPos();
      Attempt nativeAttempt = attempt(level, nativeDungeon, origin, false);
      Attempt oldAttempt = attempt(level, previous, origin, false);
      Attempt currentAttempt = attempt(level, current, origin, false);
      require(
          nativeAttempt.accepted() && currentAttempt.accepted(),
          "Natural dungeon lost its native/current start");
      require(
          nativeAttempt.pieces().equals(currentAttempt.pieces()),
          "Anchored guard changed native dungeon pieces");
      if (oldAttempt.accepted())
        require(
            nativeAttempt.pieces().equals(oldAttempt.pieces()),
            "Old guard changed native dungeon pieces");
      require(
          !oldAttempt.accepted(),
          "Selected roomed dungeon does not reproduce the former containment rejection");
      ListTag savedPieces = new ListTag();
      var serialization = StructurePieceSerializationContext.fromLevel(level);
      saved.getPieces().forEach(piece -> savedPieces.add(piece.createTag(serialization)));
      require(
          savedPieces.equals(currentAttempt.pieces()),
          "Saved dungeon differs from its fresh native assembly");
      JsonObject report = new JsonObject();
      report.addProperty("origin_chunk_x", origin.x);
      report.addProperty("origin_chunk_z", origin.z);
      report.addProperty("old_template_surface_accepted", oldAttempt.accepted());
      report.addProperty("current_anchored_complex_accepted", currentAttempt.accepted());
      report.addProperty("unwrapped_native_accepted", nativeAttempt.accepted());
      report.addProperty(
          "native_current_piece_identity", nativeAttempt.pieces().equals(currentAttempt.pieces()));
      report.addProperty(
          "saved_current_piece_identity", savedPieces.equals(currentAttempt.pieces()));
      report.addProperty("native_pieces", nativeAttempt.pieces().size());
      report.addProperty(
          "native_current_pieces_sha256", ResourceFingerprints.nbt(nativeAttempt.pieces()));
      JsonArray pots = pots(level, saved);
      require(!pots.isEmpty(), "Roomed natural dungeon has no ancient pot reward");
      report.add("natural_ancient_pots", pots);
      report.add("void_controls_biome_bypassed", voidControls(level, nativeDefinition, origin));
      JsonArray heights = new JsonArray();
      for (int offset :
          new int[] {
            level.getMaxBuildHeight() + 64, level.getMinBuildHeight() - level.getHeight() - 64
          }) {
        JsonObject displaced = nativeDefinition.deepCopy();
        JsonObject height = new JsonObject();
        height.addProperty("absolute", offset);
        displaced.add("start_height", height);
        Attempt shiftedNative = attempt(level, decode(level, displaced), origin, true);
        Attempt shiftedGuard =
            attempt(level, decode(level, wrapper(displaced, "anchored_complex")), origin, true);
        require(!shiftedGuard.accepted(), "Anchored dungeon accepted an out-of-height assembly");
        JsonObject negative = new JsonObject();
        negative.addProperty("start_height_offset", offset);
        negative.addProperty("native_accepted", shiftedNative.accepted());
        negative.addProperty("guard_accepted", shiftedGuard.accepted());
        heights.add(negative);
      }
      report.add("height_controls_biome_bypassed", heights);
      return report;
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot verify natural wetland dungeon support", failure);
    }
  }

  private record Attempt(boolean accepted, ListTag pieces, List<BoundingBox> bounds) {}

  private static Attempt attempt(
      ServerLevel level, Structure structure, ChunkPos origin, boolean bypassBiome) {
    var generator = level.getChunkSource().getGenerator();
    Optional<Structure.GenerationStub> stub =
        structure.findValidGenerationPoint(
            new Structure.GenerationContext(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                origin,
                level,
                biome -> bypassBiome || structure.biomes().contains(biome)));
    ListTag pieces = new ListTag();
    List<BoundingBox> bounds = new ArrayList<>();
    if (stub.isPresent()) {
      var serialization = StructurePieceSerializationContext.fromLevel(level);
      stub.orElseThrow()
          .getPiecesBuilder()
          .build()
          .pieces()
          .forEach(
              piece -> {
                pieces.add(piece.createTag(serialization));
                bounds.add(piece.getBoundingBox());
              });
    }
    return new Attempt(stub.isPresent(), pieces, List.copyOf(bounds));
  }

  private static JsonObject wrapper(JsonObject nativeDefinition, String terrainCheck) {
    JsonObject definition = new JsonObject();
    definition.addProperty("type", "aeroscapes_reimagine:supported");
    definition.addProperty("terrain_check", terrainCheck);
    definition.add("delegate", nativeDefinition.deepCopy());
    return definition;
  }

  private static Structure decode(ServerLevel level, JsonObject definition) {
    return Structure.DIRECT_CODEC
        .parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), definition)
        .getOrThrow();
  }

  private static JsonArray pots(ServerLevel level, StructureStart saved) {
    JsonArray pots = new JsonArray();
    var bounds = saved.getBoundingBox();
    for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
      for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++) {
        var chunk = level.getChunk(x, z);
        for (var position : chunk.getBlockEntitiesPos()) {
          if (!bounds.isInside(position)) continue;
          var entity = chunk.getBlockEntity(position);
          require(entity != null, "Saved wetland dungeon block entity is missing: " + position);
          var data = entity.saveWithFullMetadata(level.registryAccess());
          if (!data.getString("id").equals("wetland_whimsy:ancient_pot")) continue;
          require(
              entity instanceof AncientPotBlockEntity,
              "Ancient pot has an unexpected block entity type");
          AncientPotBlockEntity potEntity = (AncientPotBlockEntity) entity;
          JsonObject pot = new JsonObject();
          pot.addProperty("x", position.getX());
          pot.addProperty("y", position.getY());
          pot.addProperty("z", position.getZ());
          pot.addProperty("loot_quality", potEntity.lootQuality());
          pots.add(pot);
        }
      }
    return pots;
  }

  private static JsonArray voidControls(
      ServerLevel level, JsonObject nativeDefinition, ChunkPos positive) {
    JsonArray controls = new JsonArray();
    var generator = level.getChunkSource().getGenerator();
    JsonObject lifted = nativeDefinition.deepCopy();
    JsonObject height = new JsonObject();
    height.addProperty("absolute", 256);
    lifted.add("start_height", height);
    Structure nativeDungeon = decode(level, lifted);
    Structure current = decode(level, wrapper(lifted, "anchored_complex"));
    for (int radius = 1; radius <= 4 && controls.size() < 3; radius++)
      for (int dx = -radius; dx <= radius && controls.size() < 3; dx++)
        for (int dz = -radius; dz <= radius && controls.size() < 3; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
          ChunkPos origin = new ChunkPos(positive.x + dx * 8, positive.z + dz * 8);
          Attempt unwrapped = attempt(level, nativeDungeon, origin, true);
          if (!unwrapped.accepted() || unwrapped.bounds().isEmpty()) continue;
          boolean inHeight =
              unwrapped.bounds().stream()
                  .allMatch(
                      bounds ->
                          !level.isOutsideBuildHeight(bounds.minY())
                              && !level.isOutsideBuildHeight(bounds.maxY()));
          if (!inHeight) continue;
          var root = unwrapped.bounds().getFirst().getCenter();
          int top =
              generator.getBaseHeight(
                  root.getX(),
                  root.getZ(),
                  Heightmap.Types.WORLD_SURFACE_WG,
                  level,
                  level.getChunkSource().randomState());
          if (top != level.getMinBuildHeight()) continue;
          Attempt guarded = attempt(level, current, origin, true);
          require(!guarded.accepted(), "Anchored dungeon accepted an empty native root column");
          JsonObject control = new JsonObject();
          control.addProperty("chunk_x", origin.x);
          control.addProperty("chunk_z", origin.z);
          control.addProperty("rotated_root_center_x", root.getX());
          control.addProperty("rotated_root_center_y", root.getY());
          control.addProperty("rotated_root_center_z", root.getZ());
          control.addProperty("raw_surface_height", top);
          control.addProperty("diagnostic_start_height_offset", 256);
          control.addProperty("native_accepted", unwrapped.accepted());
          control.addProperty("native_assembly_in_height", inHeight);
          control.addProperty("guard_accepted", guarded.accepted());
          controls.add(control);
        }
    require(!controls.isEmpty(), "No bounded raw-void negative found near natural dungeon");
    return controls;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
