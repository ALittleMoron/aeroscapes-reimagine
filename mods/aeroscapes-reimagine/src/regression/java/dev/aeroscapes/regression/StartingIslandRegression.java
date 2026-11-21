package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.AeroscapesReimagine;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Exercises the initial-spawn event and the reported fresh-chunk placement sites. */
final class StartingIslandRegression {
  private StartingIslandRegression() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StartingIslandRegression::initialize);
  }

  private static void initialize(LevelEvent.CreateSpawnPosition event) {
    if (!"large".equals(System.getProperty("aeroscapes.regression.startingIsland"))) return;
    var rule =
        ((ServerLevel) event.getLevel())
            .getGameRules()
            .getRule(AeroscapesReimagine.LARGE_STARTING_ISLAND);
    require(!rule.get(), "Large starting island must default to off");
    rule.set(true, null);
  }

  static void verify(ServerLevel level, String mode) {
    BlockPos spawn = level.getSharedSpawnPos();
    require(
        spawn.getY() >= 50
            && spawn.equals(
                PlayerRespawnLogic.getOverworldRespawnPos(level, spawn.getX(), spawn.getZ())),
        "Stored world spawn must be a real native respawn column: " + spawn);
    if ("marine".equals(mode)) MarineBasinRegression.verifyGalleon(level);
    if ("large".equals(mode)) {
      require(
          level.getGameRules().getBoolean(AeroscapesReimagine.LARGE_STARTING_ISLAND),
          "Large island setting was lost during world creation");
      var terrain = IslandTerrain.from(level.getChunkSource().randomState());
      if (terrain == null) throw new IllegalStateException("Expected owned island terrain");
      var island = terrain.layout().largeIsland(spawn.getX(), spawn.getZ());
      require(
          island != null
              && island.peakHeight() >= 48
              && island.radius(spawn.getX(), spawn.getZ()) < 1,
          "Spawn must be on a natural large island with a mountain: " + spawn);
    }
    if ("shore".equals(mode) || "shoreBaseline".equals(mode)) {
      require(level.getSeed() == 66065L, "Use the reported coastal structure seed");
      boolean baseline = "shoreBaseline".equals(mode);
      var galleon = start(level, -28, -21, "supplementaries:galleon");
      var ruins = start(level, -26, -22, "galosphere:forgotten_ruins");
      boolean clippedShip = galleon != null && galleon.isValid();
      var ruinRoot =
          ruins != null && ruins.isValid() ? ruins.getPieces().getFirst().getBoundingBox() : null;
      boolean hangingRuins =
          ruinRoot != null
              && ruinRoot.minX() == -426
              && ruinRoot.minY() == 172
              && ruinRoot.minZ() == -362;
      require(clippedShip == baseline, "Unexpected galleon start at the reported dry hillside");
      require(hangingRuins == baseline, "Unexpected ruin start at the reported cliff height");
      if (!baseline && ruinRoot != null) {
        require(
            hasStandingSpace(level, ruins), "Relocated ruins have no open ground at their root");
      }
    }
    LogUtils.getLogger()
        .info(
            "Starting island regression passed: mode={} seed={} spawn={}",
            mode,
            level.getSeed(),
            spawn);
  }

  private static boolean hasStandingSpace(ServerLevel level, StructureStart start) {
    var bounds = start.getPieces().getFirst().getBoundingBox();
    for (BlockPos feet :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY() + 1,
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY() - 1,
            bounds.maxZ())) {
      if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir())
        continue;
      BlockPos floor = feet.below();
      if (Block.isFaceFull(
          level.getBlockState(floor).getCollisionShape(level, floor), Direction.UP)) return true;
    }
    return false;
  }

  private static StructureStart start(ServerLevel level, int x, int z, String id) {
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse(id));
    if (structure == null) throw new IllegalStateException("Missing structure " + id);
    ChunkAccess chunk = level.getChunk(x, z);
    return chunk.getStartForStructure(structure);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
