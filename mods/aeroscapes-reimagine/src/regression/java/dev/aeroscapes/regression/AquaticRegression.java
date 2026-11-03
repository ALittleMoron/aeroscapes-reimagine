package dev.aeroscapes.regression;

import com.li64.tide.compat.seasons.Season;
import com.li64.tide.data.fishing.FishingContext;
import com.li64.tide.data.fishing.conditions.types.AboveCondition;
import com.li64.tide.data.fishing.conditions.types.BelowCondition;
import com.li64.tide.data.fishing.conditions.types.DepthRangeCondition;
import com.li64.tide.data.rods.CustomRodManager;
import com.li64.tide.registries.TideEntityTypes;
import com.li64.tide.registries.TideItems;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.aquatic.AquaticHabitats;
import dev.aeroscapes.aquatic.AquaticTasks;
import dev.aeroscapes.aquatic.AquaticWater;
import dev.aeroscapes.mixin.aquatic.AquaticConchAccess;
import dev.aeroscapes.mixin.aquatic.TideHookAccess;
import dev.aeroscapes.worldgen.BiomeSourceDelegate;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandBiomeSources;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.hybridlabs.aquatic.entity.HAEntityTypes;
import dev.hybridlabs.aquatic.item.HAItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Finite natural habitat witnesses and actual transformed fishing/spawn entry points. */
public final class AquaticRegression {
  private AquaticRegression() {}

  public static void run(MinecraftServer server) {
    ServerLevel level = server.overworld();
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Aquatic fixture needs the owned island generator");
    require(
        AquaticWater.ownedSource(level.getChunkSource().getGenerator().getBiomeSource()),
        "Native injector chain lost the owned biome source");
    initializedSource(level);
    habitatTopology(level);
    Map<ResourceLocation, BlockPos> sites = habitats(level, terrain);
    require(!sites.isEmpty(), "No selected aquatic basin habitat in bounded source cohort");
    BlockPos deep = null;
    BlockPos shallow = null;
    int decorations = 0;
    for (var witness : sites.entrySet()) {
      BlockPos habitat = witness.getValue();
      var basin = terrain.basin(habitat.getX(), habitat.getZ());
      BlockPos site = habitat.atY(basin.waterline());
      var chunk = level.getChunkAt(site);
      fullBiomePalette(level, chunk, habitat.getY());
      var stored =
          chunk
              .getNoiseBiome(
                  QuartPos.fromBlock(habitat.getX()),
                  QuartPos.fromBlock(habitat.getY()),
                  QuartPos.fromBlock(habitat.getZ()))
              .unwrapKey()
              .orElseThrow()
              .location();
      var displayed = level.getBiome(habitat).unwrapKey().orElseThrow().location();
      require(
          stored.equals(witness.getKey()),
          "Selected habitat lost its exact-quart natural saved FULL biome: "
              + witness
              + " stored="
              + stored
              + " displayed="
              + displayed);
      BlockPos visible = visibleHabitat(level, habitat, witness.getKey());
      require(
          visible != null,
          "Saved aquatic habitat has no physical player-visible witness inside its quart cell: "
              + witness);
      LogUtils.getLogger()
          .info(
              "Aquatic FULL habitat witness {} exact={} displayedAtExact={} playerVisible={}",
              witness.getKey(),
              habitat,
              displayed,
              visible);
      int floor = AquaticWater.floor(level, site.getX(), site.getZ(), site.getY());
      for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
        for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
          if (shallow == null) {
            var water = new BlockPos(x, site.getY(), z);
            if (level.getFluidState(water).is(FluidTags.WATER)
                && AquaticWater.catchCoordinate(level, water) > 40) shallow = water;
          }
          for (int y = floor - 8; y <= site.getY(); y++) {
            var state = chunk.getBlockState(new BlockPos(x, y, z));
            if ("hybrid_aquatic"
                .equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()))
              decorations++;
          }
        }
      }
      if (level.getFluidState(site).is(FluidTags.WATER)
          && AquaticWater.catchCoordinate(level, site) < 15) deep = site;
    }
    require(deep != null, "No natural contiguous basin water deep enough for Tide deep catches");
    require(
        shallow != null, "No natural shallow fishing column in the saved aquatic witness chunks");
    require(decorations > 0, "Selected aquatic habitats have no natural saved provider decoration");
    fishingDepth(level, deep);
    AquaticFishingRegression.run(level, shallow, deep);
    encounters(level, deep);
    conch(level, deep);
    whaleFall(level);
    LogUtils.getLogger()
        .info(
            "Aquatic regression passed: natural habitats={}, decorationBlocks={}, deepFishing={}",
            sites.size(),
            decorations,
            deep);
  }

  private static BlockPos visibleHabitat(
      ServerLevel level, BlockPos quartOrigin, ResourceLocation expected) {
    for (int x = 0; x < 4; x++) {
      for (int y = 0; y < 4; y++) {
        for (int z = 0; z < 4; z++) {
          BlockPos point = quartOrigin.offset(x, y, z);
          if (("tide_pools".equals(expected.getPath())
                  || level.getFluidState(point).is(FluidTags.WATER))
              && level.getBiome(point).unwrapKey().orElseThrow().location().equals(expected))
            return point;
        }
      }
    }
    return null;
  }

  private static void habitatTopology(ServerLevel level) {
    var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
    List<Holder<Biome>> roster = new ArrayList<>();
    for (var habitat : AquaticHabitats.Habitat.values()) {
      roster.add(
          registry.getHolderOrThrow(
              ResourceKey.create(
                  Registries.BIOME,
                  ResourceLocation.fromNamespaceAndPath(
                      "hybrid_aquatic", habitat.name().toLowerCase(Locale.ROOT)))));
    }
    var measurements =
        AquaticHabitatTopologyRegression.run(
            new AquaticHabitats(roster), registry.getHolderOrThrow(Biomes.OCEAN));
    LogUtils.getLogger().info("Aquatic habitat topology regression passed: {}", measurements);
  }

  private static void fullBiomePalette(ServerLevel level, ChunkAccess chunk, int y) {
    var source = level.getChunkSource().getGenerator().getBiomeSource();
    var sampler = level.getChunkSource().randomState().sampler();
    int quartY = QuartPos.fromBlock(y);
    int minX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
    int minZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
    for (int x = minX; x < minX + 4; x++) {
      for (int z = minZ; z < minZ + 4; z++) {
        var expected = source.getNoiseBiome(x, quartY, z, sampler);
        var stored = chunk.getNoiseBiome(x, quartY, z);
        require(
            expected.equals(stored),
            "Aquatic FULL biome palette differs from its loaded source at quart "
                + new BlockPos(x, quartY, z)
                + " expected="
                + expected.unwrapKey()
                + " stored="
                + stored.unwrapKey());
      }
    }
  }

  private static void initializedSource(ServerLevel level) {
    var source = level.getChunkSource().getGenerator().getBiomeSource();
    var leaf = IslandBiomeSources.find(source);
    require(leaf != null, "Loaded wrapper chain has no owned source");
    var original = level.getChunkSource().randomState().sampler();
    var originalContext = (IslandBiomeSampler) (Object) original;
    require(
        originalContext.aeroscapesReimagine$terrainMask() != null,
        "Loaded RandomState lost its island mask; native terrain transforms must stay suppressed");
    require(
        originalContext.aeroscapesReimagine$generationSource() == null,
        "A generation clone leaked into the shared world sampler");
    var generation =
        IslandBiomeSources.forGeneration(
            source, original, originalContext.aeroscapesReimagine$terrainMask());
    var generationContext = (IslandBiomeSampler) (Object) generation;
    require(
        generation != original && generationContext.aeroscapesReimagine$generationSource() != leaf,
        "Wrapped generation skipped initialized TerraBlender parameter cloning");
    require(
        generationContext
            .aeroscapesReimagine$generationSource()
            .possibleBiomes()
            .equals(leaf.possibleBiomes()),
        "Generation clone lost biome membership");
    java.util.List<String> chain = new java.util.ArrayList<>();
    var cursor = source;
    while (cursor instanceof BiomeSourceDelegate wrapper) {
      chain.add(cursor.getClass().getName());
      cursor = wrapper.aeroscapes$delegate();
    }
    chain.add(cursor.getClass().getName());
    require(
        chain.stream().anyMatch(name -> name.endsWith("InjectorBiomeSource"))
            && chain.stream().anyMatch(name -> name.endsWith("ModdedBiomeSource")),
        "Fixture did not exercise the actual Lithostitched/Blueprint wrapper boundary");
    source.getNoiseBiome(0, 0, 0, original);
    source.getNoiseBiome(0, 0, 0, generation);
    require(
        IslandBiomeSources.find(source) == leaf
            && originalContext.aeroscapesReimagine$generationSource() == null,
        "Per-generation sampling mutated the retained world source");
    LogUtils.getLogger()
        .info(
            "Aquatic initialized source chain={} generationClone={}",
            chain,
            generationContext.aeroscapesReimagine$generationSource().getClass().getName());
  }

  private static Map<ResourceLocation, BlockPos> habitats(
      ServerLevel level, IslandTerrain terrain) {
    Map<ResourceLocation, BlockPos> sites = new LinkedHashMap<>();
    var generator = level.getChunkSource().getGenerator();
    var source = generator.getBiomeSource();
    var sampler = level.getChunkSource().randomState().sampler();
    var layout = IslandTerrain.layout(sampler.continentalness());
    for (int cellX = -8; cellX <= 8 && sites.size() < 14; cellX++) {
      for (int cellZ = -8; cellZ <= 8 && sites.size() < 14; cellZ++) {
        var candidate = layout.basin(cellX * 1024, cellZ * 1024);
        if (candidate == null) continue;
        var basin = terrain.basin(candidate.centerX(), candidate.centerZ());
        if (basin == null) continue;
        for (int offsetX = -256; offsetX <= 256 && sites.size() < 14; offsetX += 32) {
          for (int offsetZ = -256; offsetZ <= 256 && sites.size() < 14; offsetZ += 32) {
            int x = Math.floorDiv(basin.centerX() + offsetX, 4) * 4;
            int z = Math.floorDiv(basin.centerZ() + offsetZ, 4) * 4;
            int floor = (int) Math.ceil(basin.floor(x, z));
            for (int height :
                new int[] {
                  basin.waterline(),
                  basin.waterline() - 4,
                  basin.waterline() - 28,
                  basin.waterline() - 52,
                  floor + 4
                }) {
              int y = Math.floorDiv(height, 4) * 4;
              var selected =
                  source.getNoiseBiome(
                      QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
              var key = selected.unwrapKey().orElseThrow().location();
              if (!"hybrid_aquatic".equals(key.getNamespace()) || sites.containsKey(key)) continue;
              var column =
                  generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
              if (!"tide_pools".equals(key.getPath())
                  && !column.getBlock(y).getFluidState().is(FluidTags.WATER)) continue;
              BlockPos point = new BlockPos(x, y, z);
              sites.put(key, point);
              LogUtils.getLogger().info("Aquatic source/raw witness {} at {}", key, point);
              var outside =
                  source.getNoiseBiome(
                      QuartPos.fromBlock(x),
                      QuartPos.fromBlock(basin.waterline() + 96),
                      QuartPos.fromBlock(z),
                      sampler);
              require(
                  !outside
                      .unwrapKey()
                      .orElseThrow()
                      .location()
                      .getNamespace()
                      .equals("hybrid_aquatic"),
                  "Aquatic biome escaped bounded basin habitat");
            }
          }
        }
      }
    }
    for (var habitat : dev.aeroscapes.aquatic.AquaticHabitats.Habitat.values()) {
      var key =
          ResourceLocation.fromNamespaceAndPath(
              "hybrid_aquatic", habitat.name().toLowerCase(Locale.ROOT));
      require(
          sites.containsKey(key),
          "No source/raw habitat witness in bounded 17x17 basin-cell cohort: " + key);
    }
    return sites;
  }

  private static void fishingDepth(ServerLevel level, BlockPos surface) {
    int coordinate = AquaticWater.catchCoordinate(level, surface);
    var context =
        new FishingContext(
            level,
            new TideFishingHook(TideEntityTypes.FISHING_BOBBER, level),
            null,
            RandomSource.create(9),
            Vec3.atCenterOf(surface),
            surface,
            0,
            "water",
            level.getBiome(surface),
            level.getBiome(surface),
            level.dimension(),
            0.5f,
            0,
            Season.SUMMER);
    require(
        new BelowCondition(15).test(context),
        "Native Tide deep catch gate ignores real basin depth");
    require(
        !new AboveCondition(40).test(context),
        "Deep basin incorrectly qualifies as shallow fishing");
    require(
        new DepthRangeCondition(coordinate, coordinate).test(context),
        "Native Tide inclusive depth range changed");
    var type = HAEntityTypes.INSTANCE.getTRIPOD_FISH().get();
    var tideFish = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("tide:anglerfish"));
    require(
        SpawnPlacements.checkSpawnRules(
            tideFish, level, MobSpawnType.NATURAL, surface.below(25), RandomSource.create(4)),
        "Tide natural fish lost its local animal-position depth gate");
    require(
        !SpawnPlacements.checkSpawnRules(
            tideFish, level, MobSpawnType.NATURAL, surface.above(2), RandomSource.create(4)),
        "Tide natural fish accepted air above a basin");
    BlockPos submerged = surface.below(73);
    if (level.getFluidState(submerged).is(FluidTags.WATER))
      require(
          SpawnPlacements.checkSpawnRules(
              type, level, MobSpawnType.NATURAL, submerged, RandomSource.create(3)),
          "Custom deep fish predicate ignores local basin sea level");
    require(
        !SpawnPlacements.checkSpawnRules(
            type, level, MobSpawnType.NATURAL, surface.above(2), RandomSource.create(3)),
        "Deep fish accepted air above the basin");
  }

  private static void encounters(ServerLevel level, BlockPos position) {
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack saved = player.getMainHandItem();
    Vec3 savedPosition = player.position();
    var area = new AABB(position).inflate(6);
    try {
      player.setPos(position.getX() + 2, position.getY() + 1, position.getZ());
      for (ItemStack rod :
          List.of(new ItemStack(Items.FISHING_ROD), new ItemStack(TideItems.STONE_FISHING_ROD))) {
        for (var encounter :
            Map.of(
                    HAItems.INSTANCE.getOMINOUS_HOOK().get(),
                    "hybrid_aquatic:karkinos",
                    HAItems.INSTANCE.getCREEPERMAGNET_HOOK().get(),
                    "minecraft:creeper")
                .entrySet()) {
          CustomRodManager.setHook(rod, new ItemStack(encounter.getKey()));
          player.setItemInHand(InteractionHand.MAIN_HAND, rod);
          var empty =
              new TideFishingHook(TideEntityTypes.FISHING_BOBBER, player, level, 0, 0, 1, rod);
          empty.setPos(Vec3.atCenterOf(position));
          int before = level.getEntitiesOfClass(Entity.class, area).size();
          require(
              empty.retrieve(rod, level, player) == 0, "Empty encounter retrieval wore the rod");
          require(
              CustomRodManager.hasHook(rod), "Empty retrieval consumed a one-use encounter hook");
          require(
              level.getEntitiesOfClass(Entity.class, area).size() == before,
              "Empty retrieval summoned an encounter");
          empty.discard();
          var hook =
              new TideFishingHook(TideEntityTypes.FISHING_BOBBER, player, level, 0, 0, 1, rod);
          hook.setPos(Vec3.atCenterOf(position));
          ((TideHookAccess) hook).aeroscapes$nibble(20);
          var previous =
              level.getEntitiesOfClass(Entity.class, area).stream()
                  .map(Entity::getUUID)
                  .collect(java.util.stream.Collectors.toSet());
          try {
            require(
                hook.retrieve(rod, level, player) == 1, "Encounter retrieval lost native rod wear");
            var spawned =
                level.getEntitiesOfClass(Entity.class, area).stream()
                    .filter(entity -> !previous.contains(entity.getUUID()))
                    .toList();
            require(
                spawned.size() == 1,
                "Encounter retrieval duplicated encounter or ordinary rewards");
            var entity = spawned.getFirst();
            require(
                encounter
                    .getValue()
                    .equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()),
                "Encounter hook summoned the wrong entity");
            var distance = player.position().subtract(hook.position());
            var velocity =
                distance.scale(0.15).add(0, Math.sqrt(Math.sqrt(distance.lengthSqr())) * 0.08, 0);
            require(
                entity.getDeltaMovement().distanceToSqr(velocity) < 1.0e-12,
                "Encounter lost its native launch velocity");
            require(!CustomRodManager.hasHook(rod), "One-use encounter hook was not consumed");
            require(
                hook.retrieve(rod, level, player) == 0,
                "Discarded line summoned another encounter");
            require(
                level.getEntitiesOfClass(Entity.class, area).size() == previous.size() + 1,
                "Repeated retrieval duplicated an encounter or rewards");
          } finally {
            level.getEntitiesOfClass(Entity.class, area).stream()
                .filter(entity -> !previous.contains(entity.getUUID()))
                .forEach(Entity::discard);
            hook.discard();
          }
        }
      }
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, saved);
      player.setPos(savedPosition);
    }
  }

  private static void whaleFall(ServerLevel level) {
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    Structure structure = registry.get(ResourceLocation.parse("hybrid_aquatic:whale_fall"));
    require(structure != null, "Whale fall registry entry missing");
    var generator = level.getChunkSource().getGenerator();
    long deadline = System.nanoTime() + 60_000_000_000L;
    var holder = registry.wrapAsHolder(structure);
    for (var placement :
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder)) {
      if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
      for (int ring = 0; ring <= 12; ring++) {
        for (int regionX = -ring; regionX <= ring; regionX++) {
          for (int regionZ = -ring; regionZ <= ring; regionZ++) {
            if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != ring) continue;
            require(System.nanoTime() < deadline, "Whale fall natural search exceeded 60 seconds");
            ChunkPos chunk =
                spread.getPotentialStructureChunk(
                    level.getSeed(), regionX * spread.spacing(), regionZ * spread.spacing());
            if (!placement.isStructureChunk(
                level.getChunkSource().getGeneratorState(), chunk.x, chunk.z)) continue;
            var context =
                new Structure.GenerationContext(
                    level.registryAccess(),
                    generator,
                    generator.getBiomeSource(),
                    level.getChunkSource().randomState(),
                    level.getStructureManager(),
                    level.getSeed(),
                    chunk,
                    level,
                    structure.biomes()::contains);
            var stub = structure.findValidGenerationPoint(context);
            if (stub.isEmpty()) continue;
            var full = level.getChunk(chunk.x, chunk.z);
            var start = full.getStartForStructure(structure);
            require(
                start != null && start.isValid(),
                "Natural whale fall candidate lost its saved FULL start");
            LogUtils.getLogger()
                .info(
                    "Aquatic natural whale fall FULL chunk={} position={} pieces={}",
                    chunk,
                    stub.orElseThrow().position(),
                    start.getPieces().size());
            return;
          }
        }
      }
    }
    throw new IllegalStateException("No natural whale fall in bounded native-placement cohort");
  }

  private static void conch(ServerLevel level, BlockPos surface) {
    var registries = level.registryAccess();
    CompoundTag saved = AquaticTasks.get(level).save(new CompoundTag(), registries);
    var origin = surface.below(8);
    var area = new AABB(origin).inflate(12);
    var before =
        level.getEntitiesOfClass(Entity.class, area).stream()
            .map(Entity::getUUID)
            .collect(java.util.stream.Collectors.toSet());
    var shellBeast = HAEntityTypes.INSTANCE.getSHELL_BEAST().get();
    var beakling = HAEntityTypes.INSTANCE.getBEAKLING().get();
    List<Entity> summoned = new ArrayList<>();
    Consumer<EntityJoinLevelEvent> observer =
        event -> {
          Entity entity = event.getEntity();
          if (event.getLevel() == level
              && !event.loadedFromDisk()
              && (entity.getType() == shellBeast || entity.getType() == beakling)
              && area.contains(entity.position())) summoned.add(entity);
        };
    NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, observer);
    try {
      var conch = (AquaticConchAccess) HAItems.INSTANCE.getOMINOUS_CONCH().get();
      conch.aeroscapes$spawnAfterCooldown(level, origin, level.getServer().getTickCount());
      CompoundTag pending = AquaticTasks.get(level).save(new CompoundTag(), registries);
      var rows = pending.getList("Pending", Tag.TAG_COMPOUND);
      require(
          rows.size() == saved.getList("Pending", Tag.TAG_COMPOUND).size() + 1,
          "Native conch did not schedule one delayed summon");
      require(
          rows.getCompound(rows.size() - 1).getLong("Due") == level.getGameTime() + 140,
          "Conch delay is not140 real game ticks");
      require(
          AquaticTasks.load(pending, registries)
              .save(new CompoundTag(), registries)
              .equals(pending),
          "Pending conch position/deadline failed saved-data round trip");
      require(
          summoned.isEmpty()
              && level.getEntitiesOfClass(Entity.class, area).stream()
                  .allMatch(entity -> before.contains(entity.getUUID())),
          "Conch spawned before its deadline");
      conch.aeroscapes$spawnAfterCooldown(level, origin, level.getServer().getTickCount() - 140);
      var visible =
          level.getEntitiesOfClass(Entity.class, area).stream()
              .filter(entity -> !before.contains(entity.getUUID()))
              .toList();
      LogUtils.getLogger()
          .info(
              "Aquatic conch native summon origin={} observed={} visible={} entities={}",
              origin,
              summoned.size(),
              visible.size(),
              summoned.stream()
                  .map(
                      entity ->
                          BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                              + "@"
                              + entity.position()
                              + " added="
                              + entity.isAddedToLevel())
                  .toList());
      require(
          summoned.size() == 7 && summoned.stream().map(Entity::getUUID).distinct().count() == 7,
          "Native delayed conch branch must spawn one Shell Beast and six Beaklings");
      require(
          summoned.stream().allMatch(Entity::isAddedToLevel)
              && summoned.stream().noneMatch(Entity::isRemoved),
          "Conch native entities were attempted without successful level insertion");
      require(
          summoned.stream().filter(entity -> entity.getType() == shellBeast).count() == 1
              && summoned.stream().filter(entity -> entity.getType() == beakling).count() == 6,
          "Conch native branch lost its exact boss and minion composition");
      require(
          visible.stream().allMatch(summoned::contains),
          "Conch native branch inserted an unexpected visible entity");
    } finally {
      NeoForge.EVENT_BUS.unregister(observer);
      level
          .getDataStorage()
          .set("aeroscapes_reimagine_shell_summons", AquaticTasks.load(saved, registries));
      summoned.forEach(Entity::discard);
      level.getEntitiesOfClass(Entity.class, area).stream()
          .filter(entity -> !before.contains(entity.getUUID()))
          .forEach(Entity::discard);
    }
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }
}
