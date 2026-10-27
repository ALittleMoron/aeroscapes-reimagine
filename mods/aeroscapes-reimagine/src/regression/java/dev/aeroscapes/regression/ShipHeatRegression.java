package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

/** Checks Blaze capture, coal heating and actual mixing, with an unheated control. */
public final class ShipHeatRegression {
  private final ServerLevel level;
  private final ChunkPos chunk;
  private final BlockPos hot;
  private final BlockPos cold;
  private final long startTick;
  private final boolean addedTicket;
  private final ItemStack firstSheet;
  private boolean finished;

  private ShipHeatRegression(ServerLevel level, ItemStack firstSheet) {
    this.level = level;
    this.firstSheet = firstSheet;
    chunk = new ChunkPos(level.getSharedSpawnPos());
    int y = Math.min(level.getSharedSpawnPos().getY() + 20, level.getMaxBuildHeight() - 6);
    hot = new BlockPos(chunk.getMiddleBlockX() - 4, y, chunk.getMiddleBlockZ() + 5);
    cold = hot.east(7);
    startTick = level.getServer().getTickCount();
    addedTicket = level.setChunkForced(chunk.x, chunk.z, true);
  }

  public static void start(ServerLevel level, ItemStack firstSheet) {
    ShipHeatRegression fixture = new ShipHeatRegression(level, firstSheet);
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

  private static BlockState block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Missing block " + id);
    return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
  }

  private void place() {
    FakePlayer player =
        new FakePlayer(
            level,
            new GameProfile(UUID.fromString("a2b67f8b-ea5a-4cc9-91e3-4a24ebd4e9f1"), "AeroHeat"));
    player.setPos(hot.getX() + 0.5, hot.getY() + 1, hot.getZ() + 2.5);
    ItemStack emptyBurner = new ItemStack(item("create:empty_blaze_burner"));
    player.setItemInHand(InteractionHand.MAIN_HAND, emptyBurner);
    Blaze blaze = EntityType.BLAZE.create(level);
    if (blaze == null) throw new IllegalStateException("Could not create Blaze");
    blaze.setPos(hot.getX() + 0.5, hot.getY() + 1, hot.getZ() + 0.5);
    if (!level.addFreshEntity(blaze)) throw new IllegalStateException("Could not spawn Blaze");
    emptyBurner.interactLivingEntity(player, blaze, InteractionHand.MAIN_HAND);
    if (!blaze.isRemoved() || !player.getMainHandItem().is(item("create:blaze_burner")))
      throw new IllegalStateException("Capturing Blaze did not produce a filled burner");

    level.setBlockAndUpdate(hot.below(), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(hot, Blocks.AIR.defaultBlockState());
    ItemStack capturedBurner = player.getMainHandItem();
    capturedBurner.useOn(
        new UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(hot.below()), Direction.UP, hot.below(), false)));
    if (!level
        .getBlockState(hot)
        .is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:blaze_burner"))))
      throw new IllegalStateException("Could not place the captured Blaze burner");

    for (BlockPos base : new BlockPos[] {hot, cold}) {
      level.setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState());
      if (base.equals(cold)) level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(base.above(), block("create:basin"));
      level.setBlockAndUpdate(base.above(2), Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(base.above(3), block("create:mechanical_mixer"));
      BlockPos gear = base.above(3).east();
      level.setBlockAndUpdate(
          gear, block("create:cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
      level.setBlockAndUpdate(
          gear.above(),
          block("create:creative_motor").setValue(BlockStateProperties.FACING, Direction.DOWN));
      var motor = level.getBlockEntity(gear.above());
      if (motor == null) throw new IllegalStateException("Missing mixer drive");
      var data = motor.saveCustomOnly(level.registryAccess());
      data.putInt("ScrollValue", 64);
      motor.loadCustomOnly(data, level.registryAccess());
      IItemHandler basin = basin(base);
      insert(basin, new ItemStack(Items.COPPER_INGOT));
      insert(basin, new ItemStack(item("create:zinc_ingot")));
    }
    ItemStack coal = new ItemStack(Items.COAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, coal);
    level
        .getBlockState(hot)
        .useItemOn(
            coal,
            level,
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(hot), Direction.UP, hot, false));
    if (!coal.isEmpty()) throw new IllegalStateException("Blaze burner did not consume coal");
  }

  private IItemHandler basin(BlockPos base) {
    IItemHandler result =
        level.getCapability(Capabilities.ItemHandler.BLOCK, base.above(), Direction.UP);
    if (result == null) throw new IllegalStateException("Basin has no item capability at " + base);
    return result;
  }

  private static void insert(IItemHandler inventory, ItemStack input) {
    ItemStack remaining = input;
    for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++)
      remaining = inventory.insertItem(slot, remaining, false);
    if (!remaining.isEmpty())
      throw new IllegalStateException("Could not insert basin ingredient " + remaining);
  }

  private static int count(IItemHandler inventory, Item item) {
    int total = 0;
    for (int slot = 0; slot < inventory.getSlots(); slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (stack.is(item)) total += stack.getCount();
    }
    return total;
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    long ticks = event.getServer().getTickCount() - startTick;
    if (ticks % 200 == 0) {
      var mixer = level.getBlockEntity(hot.above(3));
      var burner = level.getBlockEntity(hot);
      var basin = level.getBlockEntity(hot.above());
      LogUtils.getLogger()
          .info(
              "Brass fixture tick {}: mixer={}, burner={}, basin={}",
              ticks,
              mixer == null ? "missing" : mixer.saveWithFullMetadata(level.registryAccess()),
              burner == null ? "missing" : burner.saveWithFullMetadata(level.registryAccess()),
              basin == null ? "missing" : basin.saveWithFullMetadata(level.registryAccess()));
    }
    if (ticks > 1200) {
      release();
      throw new IllegalStateException("Heated mixer did not produce brass within 1200 ticks");
    }
    IItemHandler hotInventory = basin(hot);
    IItemHandler coldInventory = basin(cold);
    Item brass = item("create:brass_ingot");
    if (count(coldInventory, brass) != 0) {
      release();
      throw new IllegalStateException("Unheated basin unexpectedly produced brass");
    }
    if (ticks < 100 || count(hotInventory, brass) != 2) return;
    if (count(hotInventory, Items.COPPER_INGOT) != 0
        || count(hotInventory, item("create:zinc_ingot")) != 0
        || count(coldInventory, Items.COPPER_INGOT) != 1
        || count(coldInventory, item("create:zinc_ingot")) != 1) {
      release();
      throw new IllegalStateException("Heated or unheated mixer inventory does not match");
    }
    release();
    LogUtils.getLogger()
        .info(
            "Ship progression Blaze capture and heated brass mixing passed with unheated control");
    ShipEngineRegression.start(level, firstSheet);
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) release();
  }

  private void release() {
    if (!finished && addedTicket) level.setChunkForced(chunk.x, chunk.z, false);
    finished = true;
  }
}
