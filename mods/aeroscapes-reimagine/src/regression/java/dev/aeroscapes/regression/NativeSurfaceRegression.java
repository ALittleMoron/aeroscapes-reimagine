package dev.aeroscapes.regression;

import dev.aeroscapes.worldgen.NativeTerrainVolume;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Actual native surface structures at their late placement height, including buried content. */
final class NativeSurfaceRegression {
  private static final ChunkPos IGLOO = new ChunkPos(-713, 1025);
  private static final ChunkPos MANSION = new ChunkPos(7812, 37472);
  private static final int[][] LOST_MANSION_BEDS = {
    {124961, 599564},
    {124962, 599563},
    {124962, 599564},
    {124962, 599565},
    {124962, 599566},
    {124962, 599567},
    {124963, 599564},
    {124963, 599565},
    {124963, 599566},
    {124963, 599567},
    {124964, 599566},
    {124964, 599567}
  };

  private NativeSurfaceRegression() {}

  static void nativeNegatives(ServerLevel level) {
    negative(level, "desert_pyramid", 160, 131);
    negative(level, "igloo", -941, -209);
    negative(level, "swamp_hut", -980, -177);
    negative(level, "mansion", 13310, 18360);
  }

  static void generateMansion(ServerLevel level) {
    StructureStart start = start(level, "mansion", MANSION.x, MANSION.z);
    load(level, start);
    var bounds = start.getBoundingBox();
    require(
        bounds.minY() >= level.getMinBuildHeight() && bounds.maxY() < level.getMaxBuildHeight(),
        "Clipped native mansion rooms");
    for (int[] chest :
        new int[][] {
          {124995, 223, 599501},
          {125012, 198, 599505},
          {124974, 200, 599523},
          {124980, 203, 599557}
        }) {
      require(
          hasLoot(level, new BlockPos(chest[0], chest[1], chest[2]), "woodland_mansion"),
          "Missing native mansion treasure");
    }
    for (int[] column : LOST_MANSION_BEDS) {
      require(
          level
              .getBlockState(new BlockPos(column[0], bounds.minY(), column[1]))
              .is(column[1] < 599566 ? Blocks.BIRCH_PLANKS : Blocks.COBBLESTONE),
          "Unsupported foundation omission changed the native mansion floor");
      for (int y = level.getMinBuildHeight() + 1; y < bounds.minY(); y++) {
        require(
            !level.getBlockState(new BlockPos(column[0], y, column[1])).is(Blocks.COBBLESTONE),
            "Mansion still filled a column whose natural bed was removed");
      }
    }
    for (int y : new int[] {195, 196})
      require(
          level.getBlockState(new BlockPos(124959, y, 599494)).is(Blocks.COBBLESTONE),
          "Mansion lost its supported native foundation fill");
    require(
        solid(level.getBlockState(new BlockPos(124959, 194, 599494)))
            && solid(
                level
                    .getChunkSource()
                    .getGenerator()
                    .getBaseColumn(124959, 599494, level, level.getChunkSource().randomState())
                    .getBlock(194)),
        "Supported mansion foundation lost its natural bed");
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
        require(
            !level
                .getBlockState(new BlockPos(x, level.getMinBuildHeight() + 1, z))
                .is(Blocks.COBBLESTONE),
            "Mansion foundation reached world bottom");
    }
    for (var chunk : chunks(start))
      level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 2, chunk);
  }

  static boolean mansionWhenReady(ServerLevel level, boolean deadline) {
    StructureStart start = start(level, "mansion", MANSION.x, MANSION.z);
    Set<ChunkPos> chunks = chunks(start);
    boolean ready =
        chunks.stream()
            .allMatch(
                chunk ->
                    level.areEntitiesLoaded(chunk.toLong())
                        && level.isPositionEntityTicking(
                            new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ())));
    if (!ready && !deadline) return false;
    try {
      require(ready, "Mansion fixture never reached loaded/entity-ticking readiness");
      var area = AABB.of(start.getBoundingBox()).inflate(2);
      require(
          !level.getEntitiesOfClass(Evoker.class, area).isEmpty()
              && !level.getEntitiesOfClass(Vindicator.class, area).isEmpty(),
          "Missing native mansion illagers after entity-ticking promotion");
      return true;
    } finally {
      for (var chunk : chunks)
        level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 2, chunk);
    }
  }

  static void pyramid(ServerLevel level) {
    StructureStart start = start(level, "desert_pyramid", -3213, -5592);
    load(level, start);
    var root = start.getPieces().getFirst();
    require(
        root.getBoundingBox().minY() - 14 >= level.getMinBuildHeight(), "Clipped pyramid cellar");
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      var chest =
          root.getWorldPos(10 + direction.getStepX() * 2, -11, 10 + direction.getStepZ() * 2);
      require(hasLoot(level, chest, "desert_pyramid"), "Missing native pyramid treasure chest");
    }
    for (int x = 8; x <= 12; x++) {
      for (int z = 8; z <= 12; z++) {
        require(
            level.getBlockState(root.getWorldPos(x, -14, z)).is(Blocks.CUT_SANDSTONE),
            "Missing native pyramid chamber base");
      }
    }
    for (int x = 9; x <= 11; x++) {
      for (int z = 9; z <= 11; z++) {
        require(
            level.getBlockState(root.getWorldPos(x, -12, z)).is(Blocks.CUT_SANDSTONE),
            "Missing native pyramid standing floor");
        require(
            level.getBlockState(root.getWorldPos(x, -13, z)).is(Blocks.TNT),
            "Incomplete native pyramid trap");
        for (int y = -10; y <= -1; y++)
          require(
              level.getBlockState(root.getWorldPos(x, y, z)).isAir(),
              "Blocked native pyramid treasure shaft");
      }
    }
    require(
        level.getBlockState(root.getWorldPos(10, -11, 10)).is(Blocks.STONE_PRESSURE_PLATE),
        "Missing native pyramid pressure plate");
    var bounds = root.getBoundingBox();
    nativeColumns(level, bounds);
    clippedColumns(level, bounds);
    boolean archaeology = false;
    for (BlockPos position :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY() - 4,
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      archaeology |= level.getBlockState(position).is(Blocks.SUSPICIOUS_SAND);
    }
    require(archaeology, "Pyramid lost its archaeology cellar");
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        for (int y = level.getMinBuildHeight(); y <= level.getMinBuildHeight() + 2; y++) {
          var position = new BlockPos(x, y, z);
          if (level.getBlockState(position).is(Blocks.SANDSTONE))
            require(
                solid(
                    level
                        .getChunkSource()
                        .getGenerator()
                        .getBaseColumn(x, z, level, level.getChunkSource().randomState())
                        .getBlock(y)),
                "Pyramid filled a raw-void column to world bottom");
        }
      }
    }
  }

  static void swampHut(ServerLevel level) {
    StructureStart start = start(level, "swamp_hut", -642, -766);
    load(level, start);
    var root = start.getPieces().getFirst();
    for (int x : new int[] {1, 5}) {
      for (int z : new int[] {2, 7}) {
        var leg = root.getWorldPos(x, -1, z);
        int y = leg.getY();
        while (y > level.getMinBuildHeight() + 1
            && level.getBlockState(new BlockPos(leg.getX(), y, leg.getZ())).is(Blocks.OAK_LOG)) y--;
        var raw =
            level
                .getChunkSource()
                .getGenerator()
                .getBaseColumn(leg.getX(), leg.getZ(), level, level.getChunkSource().randomState());
        require(
            y > level.getMinBuildHeight() + 1
                && solid(raw.getBlock(y))
                && solid(level.getBlockState(new BlockPos(leg.getX(), y, leg.getZ()))),
            "Native swamp stilt reached the void bottom");
      }
    }
    require(
        level.getBlockState(root.getWorldPos(4, 2, 6)).is(Blocks.CAULDRON),
        "Missing native swamp hut cauldron");
  }

  static void generateIgloo(ServerLevel level) {
    StructureStart start = start(level, "igloo", IGLOO.x, IGLOO.z);
    require(start.getPieces().size() > 1, "Igloo fixture lost its native basement roll");
    load(level, start);
    // Complete the neighbourhood required for the native entity-ticking promotion.
    for (int x = IGLOO.x - 2; x <= IGLOO.x + 2; x++) {
      for (int z = IGLOO.z - 2; z <= IGLOO.z + 2; z++) level.getChunk(x, z);
    }
    level.getChunkSource().addRegionTicket(TicketType.FORCED, IGLOO, 2, IGLOO);
  }

  static boolean iglooWhenReady(ServerLevel level, boolean deadline) {
    boolean ready =
        level.areEntitiesLoaded(IGLOO.toLong())
            && level.isPositionEntityTicking(
                new BlockPos(IGLOO.getMinBlockX(), 0, IGLOO.getMinBlockZ()));
    if (!ready && !deadline) return false;
    try {
      require(ready, "Igloo fixture never reached loaded/entity-ticking readiness");
      iglooContents(level);
      return true;
    } finally {
      level.getChunkSource().removeRegionTicket(TicketType.FORCED, IGLOO, 2, IGLOO);
    }
  }

  private static void iglooContents(ServerLevel level) {
    StructureStart start = start(level, "igloo", IGLOO.x, IGLOO.z);
    var boxes = new java.util.ArrayList<BoundingBox>();
    int ladders = 0;
    int loot = 0;
    int weakness = 0;
    boolean entrance = false;
    Set<BlockPos> scanned = new HashSet<>();
    for (var piece : start.getPieces()) {
      require(piece instanceof TemplateStructurePiece, "Unexpected native igloo piece");
      var template = (TemplateStructurePiece) piece;
      BlockPos origin = iglooOrigin(level, template);
      BoundingBox bounds = template.template().getBoundingBox(template.placeSettings(), origin);
      require(
          bounds.minY() >= level.getMinBuildHeight() && bounds.maxY() < level.getMaxBuildHeight(),
          "Clipped native curing basement");
      boxes.add(bounds);
      for (var ladder :
          template
              .template()
              .filterBlocks(
                  origin, template.placeSettings().copy().setBoundingBox(null), Blocks.LADDER)) {
        require(level.getBlockState(ladder.pos()).is(Blocks.LADDER), "Disconnected igloo ladder");
        ladders++;
      }
      for (BlockPos position :
          BlockPos.betweenClosed(
              bounds.minX(),
              bounds.minY(),
              bounds.minZ(),
              bounds.maxX(),
              bounds.maxY(),
              bounds.maxZ())) {
        if (!scanned.add(position.immutable())) continue;
        if (hasLoot(level, position, "igloo_chest")) loot++;
        entrance |= level.getBlockState(position).is(Blocks.OAK_TRAPDOOR);
        if (level.getBlockEntity(position) instanceof BrewingStandBlockEntity stand) {
          for (int slot = 0; slot < 3; slot++) {
            var stack = stand.getItem(slot);
            var contents = stack.get(DataComponents.POTION_CONTENTS);
            if (stack.is(Items.SPLASH_POTION) && contents != null && contents.is(Potions.WEAKNESS))
              weakness++;
          }
        }
      }
    }
    var body = BoundingBox.encapsulatingBoxes(boxes).orElseThrow();
    require(
        ladders > 0 && loot == 1 && entrance && weakness > 0,
        "Incomplete native igloo entrance/loot/curing potion");
    require(
        level.getEntitiesOfClass(Villager.class, AABB.of(body).inflate(2)).size() == 1
            && level.getEntitiesOfClass(ZombieVillager.class, AABB.of(body).inflate(2)).size() == 1,
        "Missing native curing NPC after entity-ticking promotion");
  }

  private static BlockPos iglooOrigin(ServerLevel level, TemplateStructurePiece template) {
    BlockPos origin = template.templatePosition();
    int offsetX = origin.getX() - IGLOO.getMinBlockX();
    int offsetZ = origin.getZ() - IGLOO.getMinBlockZ();
    BlockPos query =
        origin.offset(
            StructureTemplate.calculateRelativePosition(
                template.placeSettings(), new BlockPos(3 - offsetX, 0, -offsetZ)));
    int height =
        level
            .getChunkSource()
            .getGenerator()
            .getBaseHeight(
                query.getX(),
                query.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                level,
                level.getChunkSource().randomState());
    return origin.offset(0, height - 91, 0);
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  private static void nativeColumns(ServerLevel level, BoundingBox footprint) {
    var generator = level.getChunkSource().getGenerator();
    require(generator instanceof NoiseBasedChunkGenerator, "Unexpected pyramid noise generator");
    var noise = (NoiseBasedChunkGenerator) generator;
    var random = level.getChunkSource().randomState();
    var volume = NativeTerrainVolume.nativeColumns(noise, random, level, footprint);
    for (int x : new int[] {footprint.minX(), footprint.getCenter().getX(), footprint.maxX()}) {
      for (int z : new int[] {footprint.minZ(), footprint.getCenter().getZ(), footprint.maxZ()}) {
        var column = noise.getBaseColumn(x, z, level, random);
        int height = level.getMaxBuildHeight();
        while (height > level.getMinBuildHeight()
            && !Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                .isOpaque()
                .test(volume.block(x, height - 1, z))) height--;
        require(
            height
                == noise.getBaseHeight(
                    x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, random),
            "Shared native column changed the pyramid height predicate");
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
          require(
              column.getBlock(y).equals(volume.block(x, y, z)),
              "Shared native column differs from the original full noise state");
      }
    }
  }

  private static void clippedColumns(ServerLevel level, BoundingBox footprint) {
    var noise = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    int bottom = footprint.minY() - 14;
    var bounds =
        new BoundingBox(
            footprint.minX() - 1,
            bottom,
            footprint.minZ() - 1,
            footprint.minX() + 3,
            bottom + 29,
            footprint.minZ() + 3);
    var volume = new NativeTerrainVolume(noise, random, bounds);
    var solidNoise = NativeTerrainVolume.solidSampler(noise);
    for (int x : new int[] {bounds.minX(), bounds.getCenter().getX(), bounds.maxX()}) {
      for (int z : new int[] {bounds.minZ(), bounds.getCenter().getZ(), bounds.maxZ()}) {
        var column = solidNoise.getBaseColumn(x, z, level, random);
        for (int y = bounds.minY(); y <= bounds.maxY(); y++)
          require(
              column.getBlock(y).equals(volume.block(x, y, z)),
              "Clipped conservative noise differs from its full native solid column");
      }
    }
  }

  private static boolean hasLoot(ServerLevel level, BlockPos position, String name) {
    return level.getBlockEntity(position) instanceof RandomizableContainerBlockEntity container
        && container.getLootTable() != null
        && container.getLootTable().location().toString().equals("minecraft:chests/" + name);
  }

  static void oceanRuin(ServerLevel level, String name, int x, int z, Block archaeology) {
    StructureStart start = start(level, name, x, z);
    load(level, start);
    int suspicious = 0;
    int loot = 0;
    Set<BlockPos> scanned = new HashSet<>();
    for (var piece : start.getPieces()) {
      var bounds = piece.getBoundingBox();
      require(
          bounds.minY() > level.getMinBuildHeight() + 2
              && bounds.maxY() < level.getMaxBuildHeight(),
          "Ocean ruin fell below its island");
      for (BlockPos position :
          BlockPos.betweenClosed(
              bounds.minX(),
              bounds.minY() - 1,
              bounds.minZ(),
              bounds.maxX(),
              bounds.maxY(),
              bounds.maxZ())) {
        if (!scanned.add(position.immutable())) continue;
        if (level.getBlockState(position).is(archaeology)) suspicious++;
        if (level.getBlockEntity(position) instanceof RandomizableContainerBlockEntity container
            && container.getLootTable() != null
            && container
                .getLootTable()
                .location()
                .toString()
                .equals("minecraft:chests/underwater_ruin_small")) loot++;
      }
    }
    require(loot > 0 && suspicious > 0, "Ocean ruin lost its native loot or archaeology");
  }

  static void oceanNegative(ServerLevel level) {
    negative(level, "ocean_ruin_warm", -1134, -636);
  }

  private static void negative(ServerLevel level, String name, int x, int z) {
    var holder =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(ResourceLocation.withDefaultNamespace(name))
            .orElseThrow();
    var chunk = new ChunkPos(x, z);
    var generator = level.getChunkSource().getGenerator();
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
            holder.value().biomes()::contains);
    require(
        holder.value().findValidGenerationPoint(context).isEmpty(),
        "Accepted original native late-height failure: " + name);
    StructureStart start = level.getChunk(x, z).getStartForStructure(holder.value());
    require(
        start == null || !start.isValid(), "Original broken target selected naturally: " + name);
  }

  private static StructureStart start(ServerLevel level, String name, int x, int z) {
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var holder = registry.getHolder(ResourceLocation.withDefaultNamespace(name)).orElseThrow();
    var state = level.getChunkSource().getGeneratorState();
    require(
        state.getPlacementsForStructure(holder).stream()
            .anyMatch(placement -> placement.isStructureChunk(state, x, z)),
        "Surface fixture is not a native structure-set candidate");
    StructureStart start = level.getChunk(x, z).getStartForStructure(holder.value());
    require(
        start != null && start.isValid(), "Missing natural native " + name + " at " + x + "," + z);
    return start;
  }

  private static void load(ServerLevel level, StructureStart start) {
    for (var chunk : chunks(start)) level.getChunk(chunk.x, chunk.z);
  }

  private static Set<ChunkPos> chunks(StructureStart start) {
    Set<ChunkPos> chunks = new HashSet<>();
    for (var piece : start.getPieces()) {
      var bounds = piece.getBoundingBox();
      for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
        for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
          chunks.add(new ChunkPos(x, z));
      }
    }
    return chunks;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
