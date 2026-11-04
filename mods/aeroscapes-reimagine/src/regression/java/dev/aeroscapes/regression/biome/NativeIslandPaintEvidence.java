package dev.aeroscapes.regression.biome;

import com.google.gson.JsonObject;
import com.ninni.spawn.server.level.structure.BaseIslandPiece;
import com.ninni.spawn.server.level.structure.BaseIslandStructure;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Invocation evidence, distinct from source-selected biomes and native climate ties. */
public final class NativeIslandPaintEvidence {
  private record Key(long seed, ResourceLocation dimension, int x, int z) {}

  private record Paint(BlockPos origin, int radius, String biome, int bottom, int top) {
    private boolean contains(BlockPos position) {
      int x = (position.getX() & ~3) + 2;
      int z = (position.getZ() & ~3) + 2;
      int y = position.getY() & ~3;
      double dx = x - origin.getX();
      double dz = z - origin.getZ();
      double footprint = radius + 17.0;
      return dx * dx + dz * dz <= footprint * footprint && y + 3 >= bottom && y <= top + 60;
    }
  }

  private static final ConcurrentHashMap<Key, ConcurrentLinkedQueue<Paint>> PAINT =
      new ConcurrentHashMap<>();

  private NativeIslandPaintEvidence() {}

  public static void record(
      WorldGenLevel level,
      int chunkX,
      int chunkZ,
      BaseIslandPiece piece,
      String biome,
      int bottom,
      int top) {
    if (System.getProperty("aeroscapes.regression.biomeCoverage") == null) return;
    var world = level.getLevel();
    var key = new Key(world.getSeed(), world.dimension().location(), chunkX, chunkZ);
    PAINT
        .computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>())
        .add(new Paint(piece.getOrigin(), piece.getRadius(), biome, bottom, top));
  }

  public static JsonObject classify(
      ServerLevel level, BlockPos position, String selected, String actual) {
    var records =
        PAINT.get(
            new Key(
                level.getSeed(),
                level.dimension().location(),
                position.getX() >> 4,
                position.getZ() >> 4));
    if (records == null) return null;
    for (var paint : records) {
      if (!paint.biome().equals(actual) || !paint.contains(position)) continue;
      var structure =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE)
              .get(ResourceLocation.parse(actual));
      if (!(structure instanceof BaseIslandStructure)) continue;
      var start =
          level
              .structureManager()
              .getStructureWithPieceAt(position.atY(paint.origin().getY()), structure);
      if (!start.isValid()) continue;
      var serialization = StructurePieceSerializationContext.fromLevel(level);
      boolean linked =
          start.getPieces().stream()
              .anyMatch(
                  piece ->
                      piece instanceof BaseIslandPiece island
                          && island.getOrigin().equals(paint.origin())
                          && island.getRadius() == paint.radius()
                          && position.getX() >= piece.getBoundingBox().minX()
                          && position.getX() <= piece.getBoundingBox().maxX()
                          && position.getZ() >= piece.getBoundingBox().minZ()
                          && position.getZ() <= piece.getBoundingBox().maxZ()
                          && piece.createTag(serialization).getString("Biome").equals(actual));
      if (!linked) continue;
      JsonObject result = new JsonObject();
      result.addProperty("classification", "observed_native_structure_paint");
      result.addProperty("x", position.getX());
      result.addProperty("y", position.getY());
      result.addProperty("z", position.getZ());
      result.addProperty("source", selected);
      result.addProperty("stored", actual);
      result.addProperty("native_min_placed_y", paint.bottom());
      result.addProperty("native_max_placed_y", paint.top());
      result.addProperty("native_radius", paint.radius());
      result.addProperty("start_chunk_x", start.getChunkPos().x);
      result.addProperty("start_chunk_z", start.getChunkPos().z);
      return result;
    }
    return null;
  }
}
