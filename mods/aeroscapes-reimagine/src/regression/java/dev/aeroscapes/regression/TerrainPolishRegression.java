package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owner-reported surface chambers and ocean-breaching city, naturally selected on seed one. */
public final class TerrainPolishRegression {
  private static ServerLevel pendingLevel;
  private static final List<ChunkPos> FORCED = new ArrayList<>();
  private static final List<StructureStart> CITIES = new ArrayList<>();
  private static int remainingTicks;
  private static boolean listening;
  private static BasinCavernRegression basinControl;
  private static boolean checkingLeak;

  private TerrainPolishRegression() {}

  public static void verify(ServerLevel level) {
    require(pendingLevel == null, "A terrain polish tick check is already running");
    require(level.getSeed() == 1, "Use host seed 1 for terrain polish");
    cavernBoundary();
    SurfaceExtensionRegression.verify(level);
    PillarGroundingRegression.verify(level);
    FeaturePlacementRegression.verifyControlled(level);
    buriedChambers(level, natural(level, "trial_chambers", 175, -126));
    var city =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace("ancient_city"));
    StructureStart formerWetCity = level.getChunk(163, -298).getStartForStructure(city);
    CITIES.clear();
    if (formerWetCity != null && formerWetCity.isValid()) CITIES.add(formerWetCity);
    else LogUtils.getLogger().info("Polish rejected the former ocean-breaching city host");
    retainedOcean(level);
    CITIES.add(natural(level, "ancient_city", -54, 48));
    for (StructureStart start : CITIES) sealedCity(level, start);
    FORCED.clear();
    try {
      force(level, new BoundingBox(2533, 0, -4856, 2700, 0, -4680));
      for (StructureStart start : CITIES) force(level, start.getBoundingBox());
      IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
      if (terrain == null) throw new IllegalStateException("Missing terrain");
      int scheduled = 0;
      for (int x = 2536; x <= 2700; x += 4)
        for (int z = -4856; z <= -4680; z += 4) {
          var column = terrain.column(x, z);
          if (column.basin() == null || column.radial() >= 1) continue;
          BlockPos point = new BlockPos(x, (int) Math.floor(column.floor()) + 1, z);
          if (level.getFluidState(point).is(Fluids.WATER)) {
            level.scheduleTick(point, Fluids.WATER, 1);
            scheduled++;
          }
        }
      require(scheduled > 20, "No real basin-floor water updates exercised");
      basinControl = new BasinCavernRegression(level);
      checkingLeak = false;
      pendingLevel = level;
      remainingTicks = basinControl.leakTicks();
      if (!listening) {
        NeoForge.EVENT_BUS.addListener(TerrainPolishRegression::tick);
        listening = true;
      }
      LogUtils.getLogger()
          .info(
              "Polish scheduled {} basin water updates and {} ticking checks",
              scheduled,
              remainingTicks);
    } catch (RuntimeException exception) {
      release(level);
      throw exception;
    }
  }

  private static void cavernBoundary() {
    var basin = new dev.aeroscapes.worldgen.IslandLayout.Basin(0, 0, 128, 224, 224, 80, 123);
    CityCavern flat = new CityCavern(List.of(new BoundingBox(-100, 90, -100, 100, 90, 100)));
    require(
        !flat.separatesBasin(basin, 100, 100),
        "A one-block template corner may puncture the basin outside the round cavern");
    CityCavern above =
        new CityCavern(
            List.of(
                new BoundingBox(-210, 0, -210, -200, 0, -200),
                new BoundingBox(-100, 200, -100, 100, 200, 100)));
    require(
        above.separatesBasin(basin, 100, 100),
        "Inactive cavern floor wrongly rejects a template entirely above the basin");
  }

  private static void force(ServerLevel level, BoundingBox bounds) {
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
        if (level.setChunkForced(x, z, true)) FORCED.add(new ChunkPos(x, z));
  }

  private static void tick(ServerTickEvent.Post event) {
    if (pendingLevel == null || --remainingTicks > 0) return;
    boolean keepRunning = false;
    try {
      if (!checkingLeak) {
        retainedOcean(pendingLevel);
        for (StructureStart start : CITIES) sealedCity(pendingLevel, start);
        basinControl.verifySealedAndOpenLeak();
        remainingTicks = basinControl.leakTicks();
        checkingLeak = true;
        keepRunning = true;
        return;
      }
      basinControl.verifyLeakDetected();
      LogUtils.getLogger().info("Terrain polish regression passed");
    } finally {
      if (!keepRunning) {
        release(pendingLevel);
      }
    }
  }

  private static void release(ServerLevel level) {
    for (ChunkPos chunk : FORCED) level.setChunkForced(chunk.x, chunk.z, false);
    FORCED.clear();
    CITIES.clear();
    pendingLevel = null;
    basinControl = null;
  }

  private static StructureStart natural(ServerLevel level, String id, int x, int z) {
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace(id));
    StructureStart start = level.getChunk(x, z).getStartForStructure(structure);
    if ((start == null || !start.isValid()) && id.equals("trial_chambers"))
      start = VerticalGenerationRegression.replacementStart(level, structure, x, z);
    require(
        start != null && start.isValid(),
        "Missing naturally selected " + id + " at " + x + "," + z);
    if (start == null) throw new IllegalStateException("Missing start");
    BoundingBox bounds = start.getBoundingBox();
    for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++)
      for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++)
        level.getChunk(chunkX, chunkZ);
    return start;
  }

  private static void retainedOcean(ServerLevel level) {
    int tested = 0;
    int drained = 0;
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing terrain");
    for (int x = 2536; x <= 2700; x += 4)
      for (int z = -4856; z <= -4680; z += 4) {
        var column = terrain.column(x, z);
        if (column.basin() == null || column.radial() >= 0.95) continue;
        int y = column.basin().waterline() - 4;
        if (y <= column.floor() + 4) continue;
        var raw =
            level
                .getChunkSource()
                .getGenerator()
                .getBaseColumn(x, z, level, level.getChunkSource().randomState());
        if (!raw.getBlock(y).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
        tested++;
        // Later surface/features may replace raw water with rock or a hull.
        // Missing water becomes a leak when it leaves open air, not solid land.
        if (level.getBlockState(new BlockPos(x, y, z)).isAir()) drained++;
      }
    LogUtils.getLogger().info("Polish former city ocean retained={}/{}", tested - drained, tested);
    require(tested > 100 && drained == 0, "Former city host still drains ocean");
  }

  private static void buriedChambers(ServerLevel level, StructureStart start) {
    BoundingBox bounds = start.getBoundingBox();
    int sampled = 0;
    int buried = 0;
    // Independently scan the union from above after FULL generation, including
    // the roofs of stacked rooms whose floor happens to lie inside a lower room.
    for (int x = bounds.minX(); x <= bounds.maxX(); x += 3)
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 3) {
        int roof = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces()) {
          BoundingBox part = piece.getBoundingBox();
          if (x >= part.minX() && x <= part.maxX() && z >= part.minZ() && z <= part.maxZ())
            roof = Math.max(roof, part.maxY());
        }
        if (roof == Integer.MIN_VALUE) continue;
        sampled++;
        var state = level.getBlockState(new BlockPos(x, roof + 2, z));
        if (state.getFluidState().isEmpty()
            && net.minecraft.world.level.block.Block.isFaceFull(
                state.getCollisionShape(level, new BlockPos(x, roof + 2, z)),
                net.minecraft.core.Direction.UP)) buried++;
      }
    LogUtils.getLogger()
        .info("Polish natural trial exterior roof {}/{} bounds={}", buried, sampled, bounds);
    require(
        sampled > 100 && buried * 10 >= sampled * 7,
        "Trial chambers remain largely exposed: " + buried + "/" + sampled);
  }

  private static void sealedCity(ServerLevel level, StructureStart start) {
    var parts = start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
    BoundingBox bounds = BoundingBox.encapsulatingBoxes(parts).orElseThrow().inflatedBy(12);
    CityCavern cavern = new CityCavern(parts);
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Missing island terrain");
    if (terrain == null) throw new IllegalStateException("Missing terrain");
    int fluids = 0;
    int basinFluids = 0;
    int basinColumns = 0;
    int tested = 0;
    int oceanSamples = 0;
    int drained = 0;
    for (int x = bounds.minX(); x <= bounds.maxX(); x += 3)
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 3) {
        var column = terrain.column(x, z);
        if (column.basin() != null && column.radial() < 1.22) basinColumns++;
        for (int y = bounds.minY(); y <= bounds.maxY(); y += 2) {
          BlockPos point = new BlockPos(x, y, z);
          if (cavern.contribution(x, y, z) >= 0
              && parts.stream().noneMatch(part -> part.isInside(point))) continue;
          tested++;
          if (!level.getFluidState(point).isEmpty()) {
            fluids++;
            // Local springs outside basins can tick into a city. The repaired
            // contract is separation from the ocean, not removal of all fluids.
            if (column.basin() != null && column.radial() < 1.22) basinFluids++;
            if (fluids <= 8)
              LogUtils.getLogger()
                  .info("Polish city fluid at {} state={}", point, level.getBlockState(point));
          }
        }
        if (column.basin() == null || column.radial() >= 0.95) continue;
        int y = column.basin().waterline() - 4;
        if (y <= column.floor() + 4) continue;
        var raw =
            level
                .getChunkSource()
                .getGenerator()
                .getBaseColumn(x, z, level, level.getChunkSource().randomState());
        if (!raw.getBlock(y).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
        oceanSamples++;
        if (level.getBlockState(new BlockPos(x, y, z)).isAir()) drained++;
      }
    LogUtils.getLogger()
        .info(
            "Polish natural city {} fluids={}/{} basinColumns={} basinFluids={} surroundingOcean={} drained={}",
            start.getChunkPos(),
            fluids,
            tested,
            basinColumns,
            basinFluids,
            oceanSamples,
            drained);
    require(tested > 100 && basinFluids == 0, "City cavity intersects basin fluid: " + basinFluids);
    require(drained == 0, "City drained its surrounding ocean: " + drained);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
