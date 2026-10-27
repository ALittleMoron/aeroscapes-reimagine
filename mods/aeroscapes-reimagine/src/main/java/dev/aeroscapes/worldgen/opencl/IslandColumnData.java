package dev.aeroscapes.worldgen.opencl;

import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;

/** Exact CPU geometry uploaded once for a GPU batch, rather than recomputed per voxel. */
public final class IslandColumnData {
  public static final int HEADER_BYTES = 16;
  public static final int COLUMN_BYTES = 240;

  private IslandColumnData() {}

  public static byte[] create(
      IslandTerrain terrain,
      ChunkPos start,
      int chunkSize,
      StaticCache2D<StructureManager> structures) {
    int width = Math.addExact(Math.multiplyExact(chunkSize, 16), 1);
    int columnCount = Math.multiplyExact(width, width);
    List<List<CityCavern>> caverns = caverns(start, chunkSize, structures);
    int cityColumnCount = 0;
    for (int z = 0; z < width; z++) {
      for (int x = 0; x < width; x++) {
        int index = Math.min(z / 16, chunkSize - 1) * chunkSize + Math.min(x / 16, chunkSize - 1);
        cityColumnCount = Math.addExact(cityColumnCount, caverns.get(index).size());
      }
    }
    int cityStart = Math.addExact(HEADER_BYTES, Math.multiplyExact(columnCount, COLUMN_BYTES));
    ByteBuffer data =
        ByteBuffer.allocate(Math.addExact(cityStart, Math.multiplyExact(cityColumnCount, 16)))
            .order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(start.getMinBlockX()).putInt(start.getMinBlockZ()).putInt(width).putInt(width);
    int cityOffset = cityStart;
    for (int z = 0; z < width; z++) {
      for (int x = 0; x < width; x++) {
        int blockX = start.getMinBlockX() + x;
        int blockZ = start.getMinBlockZ() + z;
        IslandTerrain.Column column = terrain.column(blockX, blockZ);
        putShape(data, column.small0());
        putShape(data, column.small1());
        putShape(data, column.small2());
        putShape(data, column.small3());
        putShape(data, column.large());
        putShape(data, column.basinShape());
        data.putDouble(column.radial())
            .putDouble(column.floor())
            .putDouble(column.basin() == null ? 0 : column.basin().waterline())
            .putDouble(column.basin() == null ? 0 : 1)
            .putDouble(column.hasDetail() ? 1 : 0);
        int chunkIndex =
            Math.min(z / 16, chunkSize - 1) * chunkSize + Math.min(x / 16, chunkSize - 1);
        List<CityCavern> columnCaverns = caverns.get(chunkIndex);
        data.putInt(cityOffset).putInt(columnCaverns.size());
        for (CityCavern cavern : columnCaverns) {
          data.putDouble(cityOffset, cavern.bottom(blockX, blockZ));
          data.putDouble(cityOffset + 8, cavern.ceiling(blockX, blockZ));
          cityOffset += 16;
        }
      }
    }
    return data.array();
  }

  private static void putShape(ByteBuffer data, IslandLayout.VerticalShape shape) {
    data.putDouble(shape.cap())
        .putDouble(shape.top())
        .putDouble(shape.bottom())
        .putDouble(shape.scale());
  }

  private static List<List<CityCavern>> caverns(
      ChunkPos start, int size, StaticCache2D<StructureManager> structures) {
    List<List<CityCavern>> result = new ArrayList<>(Math.multiplyExact(size, size));
    for (int z = 0; z < size; z++) {
      for (int x = 0; x < size; x++) {
        if (structures == null) {
          result.add(List.of());
          continue;
        }
        ChunkPos position = new ChunkPos(start.x + x, start.z + z);
        result.add(
            structures
                .get(position.x, position.z)
                .startsForStructure(
                    position,
                    structure ->
                        structure instanceof SupportedStructure supported
                            && supported.needsCavern())
                .stream()
                .map(
                    structure ->
                        new CityCavern(
                            structure.getPieces().stream()
                                .map(piece -> piece.getBoundingBox())
                                .toList()))
                .toList());
      }
    }
    return result;
  }
}
