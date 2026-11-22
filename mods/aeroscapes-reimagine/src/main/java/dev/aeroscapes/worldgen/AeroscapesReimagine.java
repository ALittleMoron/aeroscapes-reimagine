package dev.aeroscapes.worldgen;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.atmosphere.CrystalResonance;
import dev.aeroscapes.atmosphere.PresentationConfig;
import dev.aeroscapes.ecology.cliff.CliffHabitats;
import dev.aeroscapes.fauna.WildlifeReactions;
import dev.aeroscapes.worldgen.c2me.C2meIslandCompiler;
import dev.aeroscapes.worldgen.feature.IslandFeatures;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

/** World-generation fixes for the pinned Aeroscapes Reimagine pack. */
@Mod("aeroscapes_reimagine")
public final class AeroscapesReimagine {
  public static final GameRules.Key<GameRules.BooleanValue> LARGE_STARTING_ISLAND =
      GameRules.register(
          "aeroscapesLargeStartingIsland",
          GameRules.Category.MISC,
          GameRules.BooleanValue.create(false));
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int MINIMUM_SPAWN_Y = 50;
  private static final int SEARCH_RADIUS = 4096;
  private static final int SEARCH_STEP = 128;
  private static final int MAXIMUM_GENERATED_CANDIDATES = 12;
  private static final int MAXIMUM_MOUNTAIN_PROBES = 48;
  private static final int MAXIMUM_PROBES_PER_MOUNTAIN = 12;
  // Covers every possible column chosen by vanilla's default first-spawn fuzz.
  private static final int VANILLA_SPAWN_RADIUS = 10;

  public AeroscapesReimagine(IEventBus modBus, ModContainer container) {
    dev.aeroscapes.magic.RacialAbilities.register(modBus);
    if (ModList.get().isLoaded("neoorigins") && ModList.get().isLoaded("pehkui")) {
      dev.aeroscapes.races.RaceIdentity.register();
      dev.aeroscapes.races.ClassTraits.register(modBus);
      if (dev.aeroscapes.races.CharacterClass.creationAvailable()) {
        dev.aeroscapes.races.RaceNetwork.register(modBus);
        dev.aeroscapes.races.ProfessionEffects.register();
      }
    }
    dev.aeroscapes.races.ProfessionTools.register(modBus);
    dev.aeroscapes.races.ChangeItems.register(modBus);
    dev.aeroscapes.engineering.EngineeringMaterials.register(modBus);
    if (ModList.get().isLoaded("animalhusbandry"))
      dev.aeroscapes.husbandry.TroughAutomation.register(modBus);
    dev.aeroscapes.engineering.OilFields.register(modBus);
    dev.aeroscapes.sky.SkyWhaleFalls.register(modBus);
    dev.aeroscapes.sky.fauna.AeroFauna.register(modBus);
    CrystalResonance.register(modBus);
    CliffHabitats.register(modBus);
    WildlifeReactions.register();
    dev.aeroscapes.weathering.WiltedSaplingBlock.register(modBus);
    if (ModList.get().isLoaded("create") && ModList.get().isLoaded("immersive_weathering"))
      dev.aeroscapes.weathering.SnowyScoriaBlock.register(modBus);
    container.registerConfig(ModConfig.Type.CLIENT, PresentationConfig.SPEC);
    if (ModList.get().isLoaded("crittersandcompanions"))
      dev.aeroscapes.movement.RemovedCompanionHook.register(modBus);
    if (ModList.get().isLoaded("spawn")) dev.aeroscapes.ecology.EcologyNetwork.register(modBus);
    if (ModList.get().isLoaded("irons_spellbooks")) {
      dev.aeroscapes.magic.SpellCastRules.register();
      if (ModList.get().isLoaded("puffish_skills")) {
        dev.aeroscapes.magic.PreparedAbilities.register();
        dev.aeroscapes.magic.MagicBonuses.register();
        dev.aeroscapes.magic.GuardianAid.register();
        dev.aeroscapes.magic.BindingPact.register();
        dev.aeroscapes.magic.travel.TravelMagic.register();
        modBus.addListener(dev.aeroscapes.magic.AbilityNetwork::register);
      }
    }
    if (ModList.get().isLoaded("pmmo") && ModList.get().isLoaded("puffish_skills")) {
      dev.aeroscapes.progression.ProgressionBridge.register();
      if (ModList.get().isLoaded("irons_spellbooks"))
        dev.aeroscapes.progression.SpellPractice.register();
    }
    if (ModList.get().isLoaded("tieredneo")) {
      dev.aeroscapes.equipment.tools.ToolReforges.register(modBus);
      if (ModList.get().isLoaded("irons_spellbooks") && ModList.get().isLoaded("curios"))
        dev.aeroscapes.equipment.magic.MagicReforges.register();
    }
    if (ModList.get().isLoaded("waystones")) {
      dev.aeroscapes.travel.TravelIntegration.register(modBus);
    }
    if (ModList.get().isLoaded("c2me_opts_dfc")) {
      C2meIslandCompiler.register();
    }
    modBus.addListener(RuntimeDataPack::register);
    modBus.addListener(dev.aeroscapes.testship.TestShipNetwork::register);
    IslandFeatures.register(modBus);
    dev.aeroscapes.aquatic.AquaticWhaleFall.register(modBus);
    BlackstoneOres.register(modBus);
    IslandTerrain.register(modBus);
    IslandBiomeSource.register(modBus);
    SupportedStructure.register(modBus);
    IslandMonumentStructure.register(modBus);
    IslandOceanRuinStructure.register(modBus);
    IslandShipwreckStructure.register(modBus);
    IslandPyramidStructure.register(modBus);
    IslandIglooStructure.register(modBus);
    IslandSwampHutStructure.register(modBus);
    IslandMansionStructure.register(modBus);
    RootSupportedStructure.register(modBus);
    AerialStructure.register(modBus);
    GroundedColumnPredicate.register(modBus);
    HangingEntityAnchorProcessor.register(modBus);
    NonVoidProjectionProcessor.register(modBus);
    InteractiveStructureLocator.register();
    NeoForge.EVENT_BUS.addListener(AeroscapesReimagine::selectSpawn);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FeaturePreparation::prepare);
  }

  private static void selectSpawn(LevelEvent.CreateSpawnPosition event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || level.dimension() != Level.OVERWORLD
        || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
      return;
    }
    BlockPos climateSpawn = level.getChunkSource().randomState().sampler().findSpawnPosition();
    BlockPos origin = new ChunkPos(climateSpawn).getMiddleBlockPosition(0);
    Map<Long, Integer> naturalHeights = new HashMap<>();
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain != null && level.getGameRules().getBoolean(LARGE_STARTING_ISLAND)) {
      selectMountainSpawn(event, level, generator, origin, terrain.layout(), naturalHeights);
      return;
    }
    int generatedCandidates = 0;
    int gridRadius = SEARCH_RADIUS / SEARCH_STEP;
    for (int ring = 0; ring <= gridRadius; ring++) {
      for (int gridX = -ring; gridX <= ring; gridX++) {
        for (int gridZ = -ring; gridZ <= ring; gridZ++) {
          if (Math.max(Math.abs(gridX), Math.abs(gridZ)) != ring
              || gridX * gridX + gridZ * gridZ > gridRadius * gridRadius) {
            continue;
          }
          int x = origin.getX() + gridX * SEARCH_STEP;
          int z = origin.getZ() + gridZ * SEARCH_STEP;
          if (naturalHeight(level, generator, naturalHeights, x, z) == IslandFootprint.NO_LAND
              || !naturalFootprint(level, generator, naturalHeights, x, z)) {
            continue;
          }
          if (generatedCandidates == MAXIMUM_GENERATED_CANDIDATES) {
            throw noSpawn(level, generatedCandidates);
          }
          generatedCandidates++;
          BlockPos spawn = validateSpawn(level, x, z);
          if (spawn == null) continue;
          setSpawn(event, level, generator, spawn);
          LOGGER.info(
              "Selected natural island spawn {} for seed {} after {} full candidate probes and {} raw columns",
              spawn,
              level.getSeed(),
              generatedCandidates,
              naturalHeights.size());
          return;
        }
      }
    }
    throw noSpawn(level, generatedCandidates);
  }

  private static void selectMountainSpawn(
      LevelEvent.CreateSpawnPosition event,
      ServerLevel level,
      NoiseBasedChunkGenerator generator,
      BlockPos origin,
      IslandLayout layout,
      Map<Long, Integer> naturalHeights) {
    var sampler = level.getChunkSource().randomState().sampler();
    Map<Long, Integer> islandProbes = new HashMap<>();
    int probed = 0;
    int generated = 0;
    for (var candidate : MountainIslandSpawn.candidates(layout, origin)) {
      if (probed == MAXIMUM_MOUNTAIN_PROBES || generated == MAXIMUM_GENERATED_CANDIDATES) break;
      long islandKey = candidate.island().key();
      if (islandProbes.getOrDefault(islandKey, 0) >= MAXIMUM_PROBES_PER_MOUNTAIN) continue;
      int x = candidate.x();
      int z = candidate.z();
      if (naturalHeight(level, generator, naturalHeights, x, z) == IslandFootprint.NO_LAND)
        continue;
      islandProbes.merge(islandKey, 1, Integer::sum);
      probed++;
      if (!candidate.accepts(
              (sampleX, sampleZ) ->
                  naturalHeight(level, generator, naturalHeights, sampleX, sampleZ),
              generator.getBiomeSource(),
              sampler)
          || !naturalFootprint(level, generator, naturalHeights, x, z)) continue;
      generated++;
      BlockPos spawn = validateSpawn(level, x, z);
      if (spawn == null) continue;
      setSpawn(event, level, generator, spawn);
      LOGGER.info(
          "Selected large mountain island spawn {} for seed {} after {} full candidate probes and {} raw columns",
          spawn,
          level.getSeed(),
          generated,
          naturalHeights.size());
      return;
    }
    throw new IllegalStateException(
        "Aeroscapes could not find a safe starting island with a mountain, multiple biomes and nearby woodland"
            + " within its bounded search for seed "
            + level.getSeed()
            + " (radius "
            + MountainIslandSpawn.SEARCH_RADIUS
            + ", "
            + probed
            + " terrain probes and "
            + generated
            + " full candidate probes). Turn off Large starting island or try another seed.");
  }

  private static BlockPos validateSpawn(ServerLevel level, int x, int z) {
    BlockPos spawn = PlayerRespawnLogic.getOverworldRespawnPos(level, x, z);
    return spawn != null
            && spawn.getY() >= MINIMUM_SPAWN_Y
            && IslandFootprint.accepts(
                (offsetX, offsetZ) ->
                    generatedHeight(level, spawn.getX() + offsetX, spawn.getZ() + offsetZ))
            && safeSpawnArea(level, spawn)
        ? spawn
        : null;
  }

  private static void setSpawn(
      LevelEvent.CreateSpawnPosition event,
      ServerLevel level,
      NoiseBasedChunkGenerator generator,
      BlockPos spawn) {
    event.getSettings().setSpawn(spawn, 0.0f);
    if (level.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
      placeBonusChest(level, generator, spawn);
    }
    event.setCanceled(true);
  }

  private static boolean naturalFootprint(
      ServerLevel level,
      NoiseBasedChunkGenerator generator,
      Map<Long, Integer> naturalHeights,
      int x,
      int z) {
    return IslandFootprint.accepts(
        (offsetX, offsetZ) ->
            naturalHeight(level, generator, naturalHeights, x + offsetX, z + offsetZ));
  }

  private static int naturalHeight(
      ServerLevel level,
      NoiseBasedChunkGenerator generator,
      Map<Long, Integer> naturalHeights,
      int x,
      int z) {
    return naturalHeights.computeIfAbsent(
        ChunkPos.asLong(x, z),
        ignored -> {
          int height =
              generator.getBaseHeight(
                  x,
                  z,
                  Heightmap.Types.WORLD_SURFACE_WG,
                  level,
                  level.getChunkSource().randomState());
          if (height < MINIMUM_SPAWN_Y) {
            return IslandFootprint.NO_LAND;
          }
          // These generator queries exclude structure beards, surface rules and placed features.
          NoiseColumn column =
              generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
          BlockState surface = column.getBlock(height - 1);
          return surface.getFluidState().isEmpty()
                  && Block.isFaceFull(
                      surface.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                      Direction.UP)
              ? height
              : IslandFootprint.NO_LAND;
        });
  }

  private static boolean safeSpawnArea(ServerLevel level, BlockPos spawn) {
    AABB standingBounds = EntityType.PLAYER.getDimensions().makeBoundingBox(Vec3.ZERO);
    if (!safeSpawnPosition(level, spawn)
        || !level.noCollision(standingBounds.move(spawn.getBottomCenter()))) {
      return false;
    }
    for (int offsetX = -VANILLA_SPAWN_RADIUS; offsetX <= VANILLA_SPAWN_RADIUS; offsetX++) {
      for (int offsetZ = -VANILLA_SPAWN_RADIUS; offsetZ <= VANILLA_SPAWN_RADIUS; offsetZ++) {
        int x = spawn.getX() + offsetX;
        int z = spawn.getZ() + offsetZ;
        BlockPos feet = PlayerRespawnLogic.getOverworldRespawnPos(level, x, z);
        // Vanilla retries null or collision-blocked columns in its random permutation.
        if (feet == null || !level.noCollision(standingBounds.move(feet.getBottomCenter()))) {
          continue;
        }
        if (!safeSpawnPosition(level, feet)) {
          return false;
        }
      }
    }
    // The validated center is itself one accepted column, so vanilla always has a choice.
    return true;
  }

  private static int generatedHeight(ServerLevel level, int x, int z) {
    // Level.getHeight returns min Y for an unloaded chunk; explicitly generate this
    // bounded candidate footprint before judging its surface.
    int surfaceY =
        level
                .getChunk(x >> 4, z >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)
            + 1;
    if (surfaceY < MINIMUM_SPAWN_Y) {
      return IslandFootprint.NO_LAND;
    }
    BlockPos support = new BlockPos(x, surfaceY - 1, z);
    BlockState floor = level.getBlockState(support);
    return floor.getFluidState().isEmpty()
            && Block.isFaceFull(floor.getCollisionShape(level, support), Direction.UP)
        ? surfaceY
        : IslandFootprint.NO_LAND;
  }

  private static boolean safeSpawnPosition(ServerLevel level, BlockPos feet) {
    if (feet.getY() < MINIMUM_SPAWN_Y
        || level.getBlockState(feet).is(Blocks.POWDER_SNOW)
        || level.getBlockState(feet.above()).is(Blocks.POWDER_SNOW)) {
      return false;
    }
    BlockPos support = feet.below();
    BlockState floor = level.getBlockState(support);
    return floor.getFluidState().isEmpty()
        && Block.isFaceFull(floor.getCollisionShape(level, support), Direction.UP);
  }

  private static void placeBonusChest(
      ServerLevel level, NoiseBasedChunkGenerator generator, BlockPos spawn) {
    level
        .registryAccess()
        .registryOrThrow(Registries.CONFIGURED_FEATURE)
        .getHolderOrThrow(MiscOverworldFeatures.BONUS_CHEST)
        .value()
        .place(level, generator, level.getRandom(), spawn);
  }

  private static IllegalStateException noSpawn(ServerLevel level, int generatedCandidates) {
    return new IllegalStateException(
        "Aeroscapes could not find a natural starting island at Y >= "
            + MINIMUM_SPAWN_Y
            + " with a connected ~64-block land footprint and clear default spawn area"
            + " within "
            + SEARCH_RADIUS
            + " blocks of the vanilla climate spawn for seed "
            + level.getSeed()
            + " ("
            + generatedCandidates
            + " full candidate probes). Create a new world with another seed"
            + " or inspect this seed's generation; no artificial platform or unsafe fallback was used.");
  }
}
