package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Checks survival glue and native ship assembly after the real engine processing fixture. */
public final class ShipAssemblyRegression {
  private final ServerLevel level;
  private final ChunkPos chunk;
  private final BlockPos enginePos;
  private final BlockPos gluedPos;
  private final BlockPos ungluedPos;
  private final BlockPos assemblerPos;
  private final BlockPos shaftPos;
  private final Block engineBlock;
  private final boolean addedTicket;
  private final long startTick;
  private boolean assembled;
  private boolean fuelled;
  private long assembledAt;
  private int burnBeforeAssembly;
  private boolean finished;
  private ServerSubLevel ship;

  private ShipAssemblyRegression(ServerLevel level, ItemStack engine) {
    this.level = level;
    ItemStack portable = craftPortableEngine(level, engine);
    engineBlock = Block.byItem(portable.getItem());
    if (engineBlock == Blocks.AIR) throw new IllegalStateException("Portable engine has no block");
    chunk = new ChunkPos(level.getSharedSpawnPos());
    int y = Math.min(level.getSharedSpawnPos().getY() + 40, level.getMaxBuildHeight() - 8);
    enginePos = new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
    gluedPos = enginePos.east();
    ungluedPos = enginePos.west();
    assemblerPos = gluedPos.above();
    shaftPos =
        enginePos.relative(
            engineBlock.defaultBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
    addedTicket = level.setChunkForced(chunk.x, chunk.z, true);
    startTick = level.getServer().getTickCount();
  }

  public static void start(ServerLevel level, ItemStack engine) {
    ShipAssemblyRegression fixture = new ShipAssemblyRegression(level, engine);
    fixture.place();
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private static Item item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Missing item " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static Block block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Missing block " + id);
    return BuiltInRegistries.BLOCK.get(key);
  }

  private static ItemStack craftPortableEngine(ServerLevel level, ItemStack engine) {
    if (!engine.is(item("simulated:engine_assembly")) || engine.getCount() != 1)
      throw new IllegalStateException("Expected engine from real sequenced assembly: " + engine);
    CraftingInput input =
        CraftingInput.of(
            1,
            3,
            List.of(
                new ItemStack(item("create:iron_sheet")),
                engine.copy(),
                new ItemStack(Items.BLAST_FURNACE)));
    var recipe =
        level
            .getServer()
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, level)
            .orElseThrow(() -> new IllegalStateException("Missing portable engine recipe"));
    ItemStack result = recipe.value().assemble(input, level.registryAccess());
    if (!result.is(item("simulated:red_portable_engine"))
        || result.getCount() != 1
        || recipe.value().getRemainingItems(input).stream().anyMatch(stack -> !stack.isEmpty()))
      throw new IllegalStateException("Unexpected portable engine crafting result: " + result);
    return result;
  }

  private void place() {
    for (int x = -2; x <= 2; x++)
      for (int y = -1; y <= 2; y++)
        for (int z = -2; z <= 2; z++)
          level.setBlockAndUpdate(enginePos.offset(x, y, z), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(enginePos, engineBlock.defaultBlockState());
    level.setBlockAndUpdate(
        shaftPos,
        block("create:shaft")
            .defaultBlockState()
            .setValue(
                BlockStateProperties.AXIS,
                level
                    .getBlockState(enginePos)
                    .getValue(BlockStateProperties.HORIZONTAL_FACING)
                    .getAxis()));
    level.setBlockAndUpdate(gluedPos, Blocks.GOLD_BLOCK.defaultBlockState());
    level.setBlockAndUpdate(ungluedPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
    BlockState assembler =
        block("simulated:physics_assembler")
            .defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    if (!level.getBlockState(gluedPos).isFaceSturdy(level, gluedPos, Direction.UP)
        || !assembler.canSurvive(level, assemblerPos))
      throw new IllegalStateException("Gold block does not support physics assembler");
    level.setBlockAndUpdate(assemblerPos, assembler);
    if (!level.getBlockState(assemblerPos).is(assembler.getBlock()))
      throw new IllegalStateException("Could not place physics assembler above hull");
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    long ticks = event.getServer().getTickCount() - startTick;
    if (ticks > 300) {
      release();
      throw new IllegalStateException("Ship assembly fixture timed out");
    }
    if (!fuelled && ticks >= 20) {
      feedEngine();
      fuelled = true;
    }
    if (fuelled && !assembled && ticks >= 60) {
      verifyRunningEngine();
      level.setBlockAndUpdate(shaftPos, Blocks.AIR.defaultBlockState());
      assemble();
      assembled = true;
      assembledAt = ticks;
    }
    if (assembled && ticks >= assembledAt + 20) {
      verifyShip();
      release();
      LogUtils.getLogger().info("Ship progression recipe and loot regression passed");
    }
  }

  private float speedAt(BlockPos position) {
    var entity = level.getBlockEntity(position);
    if (entity == null)
      throw new IllegalStateException("Missing kinetic block entity at " + position);
    try {
      return ((Number) entity.getClass().getMethod("getSpeed").invoke(entity)).floatValue();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot inspect kinetic speed at " + position, exception);
    }
  }

  private void feedEngine() {
    if (speedAt(enginePos) != 0 || speedAt(shaftPos) != 0)
      throw new IllegalStateException("Unfuelled engine or shaft is rotating");
    FakePlayer player =
        new FakePlayer(
            level,
            new GameProfile(UUID.fromString("d736dd15-05c2-4373-916a-b7fea7d96b7e"), "AeroFuel"));
    player.setPos(enginePos.getX() + 0.5, enginePos.getY() + 1, enginePos.getZ() + 2.5);
    ItemStack coal = new ItemStack(Items.COAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, coal);
    level
        .getBlockState(enginePos)
        .useItemOn(
            coal,
            level,
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(enginePos), Direction.UP, enginePos, false));
    if (!player.getMainHandItem().isEmpty())
      throw new IllegalStateException("Portable engine did not accept coal");
  }

  private void verifyRunningEngine() {
    var engine = level.getBlockEntity(enginePos);
    if (engine == null) throw new IllegalStateException("Portable engine disappeared");
    try {
      float generated =
          ((Number) engine.getClass().getMethod("getGeneratedSpeed").invoke(engine)).floatValue();
      int burnTime =
          ((Number) engine.getClass().getMethod("getCurrentBurnTime").invoke(engine)).intValue();
      float engineSpeed = speedAt(enginePos);
      float shaftSpeed = speedAt(shaftPos);
      if (burnTime <= 0 || generated == 0 || engineSpeed == 0 || shaftSpeed == 0)
        throw new IllegalStateException(
            "Portable engine failed to burn coal or rotate shaft: burn="
                + burnTime
                + ", generated="
                + generated
                + ", engine="
                + engineSpeed
                + ", shaft="
                + shaftSpeed);
      burnBeforeAssembly = burnTime;
      LogUtils.getLogger()
          .info(
              "Ship progression portable engine burned coal and rotated shaft: burn={}, speed={}",
              burnTime,
              shaftSpeed);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not inspect portable engine", exception);
    }
  }

  private void assemble() {
    FakePlayer player =
        new FakePlayer(
            level,
            new GameProfile(UUID.fromString("a2b67f8b-ea5a-4cc9-91e3-4a24ebd4e9f1"), "AeroGlue"));
    player.setPos(enginePos.getX() + 0.5, enginePos.getY() + 1, enginePos.getZ() + 2.5);
    player.getInventory().clearContent();
    if (player.getAbilities().instabuild)
      throw new IllegalStateException("Glue fixture must use survival");
    try {
      Class<?> glueClass =
          Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
      Class<?> packetClass =
          Class.forName("com.simibubi.create.content.contraptions.glue.SuperGlueSelectionPacket");
      Object packet =
          packetClass
              .getConstructor(BlockPos.class, BlockPos.class)
              .newInstance(enginePos, gluedPos);
      int before = glueEntities(glueClass).size();
      packetClass
          .getMethod("handle", net.minecraft.server.level.ServerPlayer.class)
          .invoke(packet, player);
      if (glueEntities(glueClass).size() != before)
        throw new IllegalStateException("Glue was created without glue in survival inventory");
      ItemStack glue = new ItemStack(item("create:super_glue"));
      player.setItemInHand(InteractionHand.MAIN_HAND, glue);
      int damage = glue.getDamageValue();
      packetClass
          .getMethod("handle", net.minecraft.server.level.ServerPlayer.class)
          .invoke(packet, player);
      List<Entity> entities = glueEntities(glueClass);
      if (entities.size() != before + 1 || glue.getDamageValue() <= damage)
        throw new IllegalStateException(
            "Survival glue selection did not create glue and consume durability");
      Entity created = entities.getLast();
      if (!Boolean.TRUE.equals(
              glueClass.getMethod("contains", BlockPos.class).invoke(created, enginePos))
          || !Boolean.TRUE.equals(
              glueClass.getMethod("contains", BlockPos.class).invoke(created, gluedPos))
          || Boolean.TRUE.equals(
              glueClass.getMethod("contains", BlockPos.class).invoke(created, ungluedPos)))
        throw new IllegalStateException("Glue covers unexpected blocks");
      ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container == null) throw new IllegalStateException("Missing Sable server container");
      List<ServerSubLevel> beforeShips = List.copyOf(container.getAllSubLevels());
      var assembler = level.getBlockEntity(assemblerPos);
      if (assembler == null)
        throw new IllegalStateException("Missing physics assembler block entity");
      assembler.getClass().getMethod("assembleOrDisassemble").invoke(assembler);
      Object error = assembler.getClass().getMethod("getLastAssemblyException").invoke(assembler);
      if (error != null) throw new IllegalStateException("Physics assembler failed: " + error);
      List<ServerSubLevel> added =
          container.getAllSubLevels().stream()
              .filter(candidate -> !beforeShips.contains(candidate))
              .toList();
      if (added.size() != 1)
        throw new IllegalStateException("Expected one new server sublevel, got " + added.size());
      ship = added.getFirst();
    } catch (ReflectiveOperationException error) {
      Throwable cause =
          error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
      throw new IllegalStateException(
          "Could not execute pinned Create/Simulated ship assembly", cause);
    }
  }

  private List<Entity> glueEntities(Class<?> glueClass) {
    return level.getEntitiesOfClass(
        Entity.class, new AABB(enginePos).inflate(5), glueClass::isInstance);
  }

  private void verifyShip() {
    if (ship == null || ship.getPlot().getBoundingBox() == null)
      throw new IllegalStateException("Assembled ship plot is missing");
    ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
    if (container == null || ship.isRemoved() || !container.getAllSubLevels().contains(ship))
      throw new IllegalStateException("Assembled ship is no longer in the server container");
    var box = ship.getPlot().getBoundingBox();
    long width = (long) box.maxX() - box.minX() + 1;
    long height = (long) box.maxY() - box.minY() + 1;
    long depth = (long) box.maxZ() - box.minZ() + 1;
    if (width < 1
        || height < 1
        || depth < 1
        || width > 64
        || height > 64
        || depth > 64
        || width * height * depth > 32768)
      throw new IllegalStateException(
          "Unexpected assembly plot bounds: "
              + box
              + " ("
              + width
              + " x "
              + height
              + " x "
              + depth
              + ")");
    int engines = 0;
    int glued = 0;
    BlockPos movedEngine = null;
    for (int x = 0; x < width; x++)
      for (int y = 0; y < height; y++)
        for (int z = 0; z < depth; z++) {
          BlockPos position = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
          BlockState state = level.getBlockState(position);
          if (state.is(engineBlock)) {
            engines++;
            movedEngine = position;
          }
          if (state.is(Blocks.GOLD_BLOCK)) glued++;
        }
    if (engines != 1 || glued != 1)
      throw new IllegalStateException(
          "Ship plot lacks engine or glued block: engine=" + engines + ", glued=" + glued);
    if (movedEngine == null
        || movedEngine.getX() == Integer.MAX_VALUE
        || !level.getBlockState(movedEngine.east()).is(Blocks.GOLD_BLOCK))
      throw new IllegalStateException("Glued block is not east of the engine in the ship plot");
    var movedEngineEntity = level.getBlockEntity(movedEngine);
    if (movedEngineEntity == null)
      throw new IllegalStateException("Moved engine has no block entity");
    try {
      int remaining =
          ((Number)
                  movedEngineEntity
                      .getClass()
                      .getMethod("getCurrentBurnTime")
                      .invoke(movedEngineEntity))
              .intValue();
      if (remaining <= 0 || remaining > burnBeforeAssembly)
        throw new IllegalStateException(
            "Engine fuel did not survive assembly: before="
                + burnBeforeAssembly
                + ", after="
                + remaining);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not inspect moved engine fuel", exception);
    }
    if (!level.getBlockState(enginePos).isAir()
        || !level.getBlockState(gluedPos).isAir()
        || !level.getBlockState(ungluedPos).is(Blocks.DIAMOND_BLOCK))
      throw new IllegalStateException("Assembler did not move exactly the glued blocks");
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) release();
  }

  private void release() {
    if (!finished && addedTicket) level.setChunkForced(chunk.x, chunk.z, false);
    finished = true;
  }
}
