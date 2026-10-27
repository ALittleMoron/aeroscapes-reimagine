package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/** Real transformed badlands extension on empty, supported and wet controlled columns. */
public final class SurfaceExtensionRegression {
  private SurfaceExtensionRegression() {}

  public static void verify(ServerLevel level) {
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    RandomState islandState = level.getChunkSource().randomState();
    require(IslandTerrain.from(islandState) != null, "Badlands regression needs island terrain");
    NoiseGeneratorSettings settings = generator.generatorSettings().value();
    RandomState nativeState = nativeState(level, settings);
    require(IslandTerrain.from(nativeState) == null, "Native badlands control still has islands");
    SurfaceSystem islandSurface = surface(islandState, settings);
    SurfaceSystem nativeSurface = surface(nativeState, settings);
    int minimumY = level.getMinBuildHeight();
    BlockState stone = settings.defaultBlock();
    try {
      Method extension =
          SurfaceSystem.class.getDeclaredMethod(
              "erodedBadlandsExtension",
              BlockColumn.class,
              int.class,
              int.class,
              int.class,
              LevelHeightAccessor.class);
      extension.setAccessible(true);
      List<BlockPos> positions = eligible(extension, nativeSurface, stone, level);
      for (BlockPos position : positions) {
        var nativeVoid = new Column(minimumY - 1, stone);
        extend(extension, nativeSurface, nativeVoid, position, level);
        require(
            !nativeVoid.writes.isEmpty() && nativeVoid.writes.containsKey(minimumY),
            "Non-island native empty-column control did not reach minimum Y");
        var islandVoid = new Column(minimumY - 1, stone);
        extend(extension, islandSurface, islandVoid, position, level);
        require(islandVoid.writes.isEmpty(), "Badlands extension creates unsupported void land");
        for (int groundY : new int[] {64, 0, minimumY}) {
          int referenceY = Math.max(63, groundY);
          var expected = new Column(referenceY, stone);
          var actual = new Column(groundY, stone);
          extend(extension, nativeSurface, expected, position, level);
          extend(extension, islandSurface, actual, position, level);
          require(!expected.writes.isEmpty(), "Supported badlands control did not place a spire");
          Map<Integer, BlockState> translated = new HashMap<>();
          expected.writes.forEach((y, state) -> translated.put(y + groundY - referenceY, state));
          require(
              actual.writes.equals(translated),
              "Badlands extension changed native spire relief above land at Y" + groundY);
          require(
              actual.writes.keySet().stream().allMatch(y -> y > groundY),
              "Badlands extension replaced its existing ground");
        }
        var nativeWater = new Column(63, Blocks.WATER.defaultBlockState());
        var islandWater = new Column(63, Blocks.WATER.defaultBlockState());
        extend(extension, nativeSurface, nativeWater, position, level);
        extend(extension, islandSurface, islandWater, position, level);
        require(
            nativeWater.writes.isEmpty() && islandWater.writes.isEmpty(),
            "Badlands extension fills water despite the native abort");
      }
      LogUtils.getLogger()
          .info(
              "Badlands surface regression passed: {} native eligible columns, unsupported void rejected, three supported heights and water preserved",
              positions.size());
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot execute pinned native badlands surface", exception);
    }
  }

  @SuppressWarnings("deprecation") // Copy the pinned settings record's legacy mob flag.
  private static RandomState nativeState(ServerLevel level, NoiseGeneratorSettings settings) {
    var nativeSettings =
        new NoiseGeneratorSettings(
            settings.noiseSettings(),
            settings.defaultBlock(),
            settings.defaultFluid(),
            settings
                .noiseRouter()
                .mapAll(
                    density ->
                        density instanceof IslandTerrain ? DensityFunctions.constant(-1) : density),
            settings.surfaceRule(),
            settings.spawnTarget(),
            settings.seaLevel(),
            settings.disableMobGeneration(),
            settings.isAquifersEnabled(),
            settings.oreVeinsEnabled(),
            settings.useLegacyRandomSource());
    return RandomState.create(
        nativeSettings, level.registryAccess().lookupOrThrow(Registries.NOISE), level.getSeed());
  }

  private static SurfaceSystem surface(RandomState state, NoiseGeneratorSettings settings) {
    return new SurfaceSystem(
        state,
        settings.defaultBlock(),
        settings.seaLevel(),
        new XoroshiroRandomSource(777).forkPositional());
  }

  private static List<BlockPos> eligible(
      Method extension, SurfaceSystem surface, BlockState stone, LevelHeightAccessor bounds)
      throws ReflectiveOperationException {
    var positions = new ArrayList<BlockPos>();
    for (int x = -256; x <= 256; x += 8) {
      for (int z = -256; z <= 256; z += 8) {
        var column = new Column(64, stone);
        var position = new BlockPos(x, 0, z);
        extend(extension, surface, column, position, bounds);
        if (!column.writes.isEmpty()) positions.add(position);
        if (positions.size() == 8) return positions;
      }
    }
    throw new IllegalStateException("No bounded set of native eligible badlands spires");
  }

  private static void extend(
      Method extension,
      SurfaceSystem surface,
      Column column,
      BlockPos position,
      LevelHeightAccessor bounds)
      throws ReflectiveOperationException {
    // Match buildSurface exactly: the argument is highest occupied block + 1, not its Y.
    extension.invoke(surface, column, position.getX(), position.getZ(), column.groundY + 1, bounds);
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }

  private static final class Column implements BlockColumn {
    private final int groundY;
    private final BlockState ground;
    private final Map<Integer, BlockState> writes = new HashMap<>();

    private Column(int groundY, BlockState ground) {
      this.groundY = groundY;
      this.ground = ground;
    }

    @Override
    public BlockState getBlock(int y) {
      BlockState written = writes.get(y);
      if (written != null) return written;
      return y <= groundY ? ground : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void setBlock(int y, BlockState state) {
      writes.put(y, state);
    }
  }
}
