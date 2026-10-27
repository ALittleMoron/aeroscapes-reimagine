package dev.aeroscapes.regression;

import com.ishland.c2me.opts.dfc.common.ast.binary.AddNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.c2me.C2meIslandCompiler;
import dev.aeroscapes.worldgen.opencl.IslandColumnData;
import dev.aeroscapes.worldgen.opencl.IslandOpenClGraph;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Lossless GPU upload contract checked against real seeded CPU columns. */
final class OpenClColumnRegression {
  private OpenClColumnRegression() {}

  static void verify(RandomState state) {
    IslandTerrain terrain = IslandTerrain.from(state);
    require(terrain != null, "GPU upload requires the retained seeded terrain");
    IslandOpenClGraph.validateFlatCache(new ConstantNode(1));
    boolean rejected = false;
    try {
      IslandOpenClGraph.validateFlatCache(
          new AddNode(new ConstantNode(1), C2meIslandCompiler.node(terrain)));
    } catch (IllegalStateException expected) {
      rejected = expected.getMessage().contains("null-context flat-cache");
    }
    require(rejected, "GPU flat-cache prefill accepted a nested island geometry root");
    for (ChunkPos start :
        List.of(new ChunkPos(0, 0), new ChunkPos(-1, -1), new ChunkPos(-93, 64))) {
      ByteBuffer data =
          ByteBuffer.wrap(IslandColumnData.create(terrain, start, 1, null))
              .order(ByteOrder.LITTLE_ENDIAN);
      require(data.getInt() == start.getMinBlockX(), "Wrong upload minimum X");
      require(data.getInt() == start.getMinBlockZ(), "Wrong upload minimum Z");
      require(data.getInt() == 17 && data.getInt() == 17, "Upload omitted interpolation boundary");
      for (int z = 0; z < 17; z++) {
        for (int x = 0; x < 17; x++) {
          IslandTerrain.Column column =
              terrain.column(start.getMinBlockX() + x, start.getMinBlockZ() + z);
          shape(data, column.small0());
          shape(data, column.small1());
          shape(data, column.small2());
          shape(data, column.small3());
          shape(data, column.large());
          shape(data, column.basinShape());
          equal(data.getDouble(), column.radial());
          equal(data.getDouble(), column.floor());
          equal(data.getDouble(), column.basin() == null ? 0 : column.basin().waterline());
          equal(data.getDouble(), column.basin() == null ? 0 : 1);
          equal(data.getDouble(), column.hasDetail() ? 1 : 0);
          require(data.getInt() == data.capacity(), "Unexpected city payload offset");
          require(data.getInt() == 0, "Flat-cache upload invented structure data");
        }
      }
      require(data.position() == data.capacity(), "GPU upload stride does not cover its payload");
    }
    CityCavern cavern = new CityCavern(List.of(new BoundingBox(-36, -64, -24, 31, -28, 39)));
    for (int z = -48; z <= 48; z += 7)
      for (int x = -48; x <= 48; x += 5)
        for (int y = -80; y <= -8; y++) {
          double opening = Math.min((y - cavern.bottom(x, z)) / 2, (cavern.ceiling(x, z) - y) / 4);
          equal(cavern.contribution(x, y, z), opening > 0 ? -Math.min(6, opening * 6) : 0);
        }
  }

  private static void shape(ByteBuffer data, IslandLayout.VerticalShape shape) {
    equal(data.getDouble(), shape.cap());
    equal(data.getDouble(), shape.top());
    equal(data.getDouble(), shape.bottom());
    equal(data.getDouble(), shape.scale());
  }

  private static void equal(double expected, double actual) {
    require(
        Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual),
        "GPU column upload changed an exact CPU value");
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }
}
