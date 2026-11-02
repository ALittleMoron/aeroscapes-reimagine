package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.trees.TreeTransactions;
import dev.pandasystems.fallingtrees.entity.TreeEntity;
import ht.treechop.api.ChopEvent;
import ht.treechop.common.block.ChoppedLogBlock;
import ht.treechop.common.chop.ChopUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/** Real break-event, native enchantment, cancellation, drop and save boundaries. */
public final class TreesRegression {
  private TreesRegression() {}

  public static void verify(ServerLevel level) {
    long originalTime = level.getGameTime();
    require(
        TreeTransactions.remainingWear(100, 24) == 126, "100 logs cost 150 including paid cuts");
    require(TreeTransactions.remainingWear(3, 1) == 4, "Odd log totals round upward once");
    BlockPos origin =
        level
            .getSharedSpawnPos()
            .offset(48, 32, 48)
            .atY(Math.min(level.getSharedSpawnPos().getY() + 32, level.getMaxBuildHeight() - 204));
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) level.getChunkAt(origin.offset(x, 0, z));
    }
    Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    for (BlockPos position :
        BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 201, 2))) {
      require(level.getBlockEntity(position) == null, "Fixture may not replace a block entity");
      before.put(position.immutable(), level.getBlockState(position));
    }
    AABB bounds =
        new AABB(
            Vec3.atLowerCornerOf(origin.offset(-3, -2, -3)),
            Vec3.atLowerCornerOf(origin.offset(3, 204, 3)));
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("ac39ac2c-b41c-4630-bcaf-36219a210a45"),
            "TreeTests",
            Vec3.atCenterOf(origin).add(2, 0, 0),
            null)) {
      ServerPlayer player = session.player;
      player.setGameMode(GameType.SURVIVAL);
      List<String> failures = new ArrayList<>();
      for (int logs : new int[] {10, 50, 100, 200}) {
        runCase(
            level,
            before,
            bounds,
            failures,
            "felling-" + logs,
            () -> {
              prepare(level, origin, logs);
              ItemStack axe = equip(player);
              int actions = fell(player, origin, 64);
              require(
                  actions > 1 && actions < logs,
                  "Logarithmic chopping must progress before felling " + logs + " logs");
              require(
                  axe.getDamageValue() == logs + (logs + 1) / 2,
                  "Full transaction wear for "
                      + logs
                      + "; actual="
                      + axe.getDamageValue()
                      + ", actions="
                      + actions
                      + ", exhaustion="
                      + player.getFoodData().getExhaustionLevel());
              require(
                  Math.abs(player.getFoodData().getExhaustionLevel() - logs * 0.005F) < 0.00001F,
                  "One native exhaustion charge per yielded log");
              verifyDropsAndSave(level, bounds, logs);
            });
      }
      runCase(level, before, bounds, failures, "manual", () -> verifyManual(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "insufficient",
          () -> verifyInsufficient(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "cancellation",
          () -> verifyCancellation(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "unbreaking",
          () -> verifyUnbreaking(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "built-timber",
          () -> verifyBuiltTimber(level, player, origin));
      runCase(
          level, before, bounds, failures, "mangrove", () -> verifyMangrove(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "fungus",
          () -> verifyNetherFungus(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "warped-fungus",
          () -> verifyWarpedFungus(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "artificial-fungus-canopy",
          () -> verifyArtificialFungusCanopy(level, player, origin));
      runCase(
          level, before, bounds, failures, "desert", () -> verifyDesertTree(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "coconut",
          () -> verifyCoconutTree(level, player, origin));
      runCase(
          level,
          before,
          bounds,
          failures,
          "shared-cuts",
          () -> verifySharedCutsAfterManualRemoval(level, player, origin));
      require(failures.isEmpty(), "Native tree cases failed: " + String.join("; ", failures));
    } finally {
      for (var entity : level.getEntitiesOfClass(TreeEntity.class, bounds)) entity.discard();
      for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) entity.discard();
      before.forEach(level::setBlockAndUpdate);
      ((ServerLevelData) level.getLevelData()).setGameTime(originalTime);
    }
  }

  private static void runCase(
      ServerLevel level,
      Map<BlockPos, BlockState> before,
      AABB bounds,
      List<String> failures,
      String name,
      Runnable check) {
    clear(level, before);
    try {
      check.run();
      LogUtils.getLogger().info("Tree native case {} passed", name);
    } catch (RuntimeException failure) {
      failures.add(name + ": " + failure.getMessage());
      LogUtils.getLogger().error("Tree native case {} failed", name, failure);
    } finally {
      for (var entity : level.getEntitiesOfClass(TreeEntity.class, bounds)) entity.discard();
      for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) entity.discard();
    }
  }

  private static ItemStack equip(ServerPlayer player) {
    ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
    player.setItemSlot(EquipmentSlot.MAINHAND, axe);
    player.getFoodData().setExhaustion(0);
    player.setShiftKeyDown(false);
    return axe;
  }

  private static void clear(ServerLevel level, Map<BlockPos, BlockState> before) {
    for (BlockPos position : before.keySet())
      level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
  }

  private static void prepare(ServerLevel level, BlockPos origin, int logs) {
    level.setBlockAndUpdate(origin.below(), Blocks.DIRT.defaultBlockState());
    for (int height = 0; height < logs; height++) {
      level.setBlockAndUpdate(origin.above(height), Blocks.OAK_LOG.defaultBlockState());
    }
    level.setBlockAndUpdate(
        origin.above(logs),
        Blocks.OAK_LEAVES
            .defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false)
            .setValue(LeavesBlock.DISTANCE, 1));
  }

  private static int fell(ServerPlayer player, BlockPos origin, int limit) {
    int actions = 0;
    while (!player.level().getBlockState(origin).isAir() && actions < limit) {
      breakLog(player, origin);
      actions++;
    }
    require(
        player.level().getBlockState(origin).isAir(), "Tree did not finish within action bound");
    return actions;
  }

  private static void breakLog(ServerPlayer player, BlockPos position) {
    ServerLevel level = player.serverLevel();
    // HT's real recursion guard permits only one chop per player per game-time tick.
    ((ServerLevelData) level.getLevelData()).setGameTime(Math.addExact(level.getGameTime(), 1L));
    player.gameMode.destroyBlock(position);
  }

  private static void verifyDropsAndSave(ServerLevel level, AABB bounds, int logs) {
    var falling = level.getEntitiesOfClass(TreeEntity.class, bounds);
    require(falling.size() == 1, "One animation entity per tree");
    TreeEntity tree = falling.getFirst();
    CompoundTag saved = new CompoundTag();
    require(tree.save(saved), "Falling tree must participate in actual entity save");
    require(
        saved.getList("AeroscapesTreeBlocks", CompoundTag.TAG_COMPOUND).size() == logs + 1,
        "Saved snapshot includes every log and the native leaf");
    var savedDrops = saved.getList("AeroscapesTreeDrops", CompoundTag.TAG_COMPOUND);
    int savedLogs = 0;
    for (int index = 0; index < savedDrops.size(); index++) {
      ItemStack stack =
          ItemStack.parseOptional(level.registryAccess(), savedDrops.getCompound(index));
      if (stack.is(Items.OAK_LOG)) savedLogs += stack.getCount();
    }
    require(savedLogs == logs, "Saved falling-tree log batch; actual=" + savedLogs);
    tree.discard();
    TreeEntity restored = new TreeEntity(tree.getType(), level);
    restored.load(saved);
    require(level.addFreshEntity(restored), "Reload falling tree");
    require(restored.getBlocks().size() == logs + 1, "Reload retains visual geometry");
    for (int tick = 0; tick <= restored.getMaxLifeTimeTick() && !restored.isRemoved(); tick++) {
      level.tickNonPassenger(restored);
    }
    int yielded =
        level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
            .map(ItemEntity::getItem)
            .filter(stack -> stack.is(Items.OAK_LOG))
            .mapToInt(ItemStack::getCount)
            .sum();
    require(
        yielded == logs,
        "Exactly one conserved log batch after save/reload; actual="
            + yielded
            + ", age="
            + restored.tickCount
            + ", lifetime="
            + restored.getMaxLifeTimeTick()
            + ", position="
            + restored.position()
            + ", savedLogs="
            + savedLogs);
    for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) entity.discard();
    require(level.getEntitiesOfClass(TreeEntity.class, bounds).isEmpty(), "Animation expires once");
  }

  private static void verifyManual(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepare(level, origin, 6);
    ItemStack axe = equip(player);
    player.setShiftKeyDown(true);
    breakLog(player, origin);
    require(
        level.getBlockState(origin).isAir()
            && level.getBlockState(origin.above()).is(Blocks.OAK_LOG),
        "Sneak breaks one ordinary log");
    require(axe.getDamageValue() == 1, "Manual sneak has ordinary axe wear");
    player.setShiftKeyDown(false);
    level.setBlockAndUpdate(origin, Blocks.OAK_LOG.defaultBlockState());
    level.setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
    axe = equip(player);
    breakLog(player, origin);
    require(
        axe.getDamageValue() == 1 && level.getBlockState(origin).isAir(),
        "Isolated log without leaves has ordinary wear");
  }

  private static void verifyInsufficient(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepare(level, origin, 10);
    ItemStack first = equip(player);
    breakLog(player, origin);
    int paid = first.getDamageValue();
    require(paid == 1, "Native first chop costs one");
    int needed = ChopUtil.getTree(level, origin).numChopsNeededToFell();
    for (int action = 1; action < needed - 1; action++) breakLog(player, origin);
    paid = first.getDamageValue();
    var chopped = level.getBlockEntity(origin);
    require(chopped != null, "Progress has a native chopped-log block entity");
    CompoundTag saved = chopped.saveWithFullMetadata(level.registryAccess());
    BlockState state = level.getBlockState(origin);
    level.removeBlockEntity(origin);
    var restored = BlockEntity.loadStatic(origin, state, saved, level.registryAccess());
    require(restored != null, "Reload paid chopped-log progress");
    level.setBlockEntity(restored);
    int progress = ChopUtil.getTree(level, origin).getChops();
    ItemStack weak = new ItemStack(Items.WOODEN_AXE);
    weak.setDamageValue(weak.getMaxDamage() - 1);
    player.setItemSlot(EquipmentSlot.MAINHAND, weak);
    breakLog(player, origin);
    require(!level.getBlockState(origin).isAir(), "Insufficient axe cannot remove tree");
    require(
        ChopUtil.getTree(level, origin).getChops() == progress, "Refusal preserves paid progress");
    require(weak.getDamageValue() == weak.getMaxDamage() - 1, "Refusal charges nothing");
    ItemStack replacement = equip(player);
    fell(player, origin, 2);
    require(
        paid + replacement.getDamageValue() == 15,
        "Tool replacement retains aggregate nominal cost");
    for (var entity :
        level.getEntitiesOfClass(TreeEntity.class, new AABB(origin).inflate(3, 12, 3)))
      entity.discard();
  }

  private static void verifyCancellation(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepare(level, origin, 10);
    ItemStack axe = equip(player);
    int needed = ChopUtil.getTree(level, origin).numChopsNeededToFell();
    for (int action = 0; action < needed - 1; action++) breakLog(player, origin);
    int damage = axe.getDamageValue();
    int progress = ChopUtil.getTree(level, origin).getChops();
    Consumer<ChopEvent.BeforeFellEvent> cancel = event -> event.setCanceled(true);
    AtomicInteger afterFell = new AtomicInteger();
    AtomicInteger finishedFell = new AtomicInteger();
    Consumer<ChopEvent.AfterFellEvent> after = event -> afterFell.incrementAndGet();
    Consumer<ChopEvent.FinishChopEvent> finish =
        event -> {
          if (event.getFelled()) finishedFell.incrementAndGet();
        };
    NeoForge.EVENT_BUS.addListener(
        EventPriority.LOWEST, false, ChopEvent.BeforeFellEvent.class, cancel);
    NeoForge.EVENT_BUS.addListener(ChopEvent.AfterFellEvent.class, after);
    NeoForge.EVENT_BUS.addListener(ChopEvent.FinishChopEvent.class, finish);
    try {
      breakLog(player, origin);
      require(
          !level.getBlockState(origin).isAir(), "Cancelled native felling cannot remove a stump");
      require(axe.getDamageValue() == damage, "Cancelled felling cannot charge final wear");
      require(
          ChopUtil.getTree(level, origin).getChops() == progress,
          "Cancellation preserves progress");
      require(
          afterFell.get() == 0 && finishedFell.get() == 0,
          "Cancelled public BeforeFell cannot report completion");
      NeoForge.EVENT_BUS.unregister(cancel);
      fell(player, origin, 2);
      require(
          afterFell.get() == 1 && finishedFell.get() == 1,
          "Successful owned fall reports native completion exactly once; after="
              + afterFell.get()
              + ", finish="
              + finishedFell.get());
    } finally {
      NeoForge.EVENT_BUS.unregister(cancel);
      NeoForge.EVENT_BUS.unregister(after);
      NeoForge.EVENT_BUS.unregister(finish);
    }
    discardFalls(level, origin, 12);
  }

  private static void verifyUnbreaking(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepare(level, origin, 100);
    ItemStack axe = equip(player);
    axe.enchant(
        level
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.UNBREAKING),
        3);
    player.getRandom().setSeed(710327);
    fell(player, origin, 64);
    require(
        axe.getDamageValue() > 0 && axe.getDamageValue() < 100,
        "Native Unbreaking reduces nominal 150 wear");
    for (var entity :
        level.getEntitiesOfClass(TreeEntity.class, new AABB(origin).inflate(3, 102, 3)))
      entity.discard();
  }

  private static void verifyBuiltTimber(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepare(level, origin, 10);
    level.setBlockAndUpdate(origin.below(), Blocks.STONE_BRICKS.defaultBlockState());
    ItemStack axe = equip(player);
    breakLog(player, origin);
    require(
        level.getBlockState(origin).isAir()
            && level.getBlockState(origin.above(9)).is(Blocks.OAK_LOG),
        "Nonsoil timber is broken one block at a time even beside natural leaves");
    require(axe.getDamageValue() == 1, "Unsafe timber never wastes progressive chopping work");
  }

  private static void verifyMangrove(ServerLevel level, ServerPlayer player, BlockPos origin) {
    BlockPos trunk = origin.above();
    prepareSpecies(
        level,
        trunk,
        Blocks.MANGROVE_LOG.defaultBlockState(),
        Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1),
        Blocks.MANGROVE_ROOTS.defaultBlockState());
    level.setBlockAndUpdate(origin.below(), Blocks.MUD.defaultBlockState());
    ItemStack axe = equip(player);
    fell(player, trunk, 16);
    require(axe.getDamageValue() == 15, "Mangrove on natural roots retains the same felling cost");
    require(level.getBlockState(origin).is(Blocks.MANGROVE_ROOTS), "Mangrove roots stay planted");
    discardFalls(level, origin, 14);
  }

  private static void verifyNetherFungus(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepareSpecies(
        level,
        origin,
        Blocks.CRIMSON_STEM.defaultBlockState(),
        Blocks.NETHER_WART_BLOCK.defaultBlockState(),
        Blocks.CRIMSON_NYLIUM.defaultBlockState());
    ItemStack axe = equip(player);
    fell(player, origin, 16);
    require(axe.getDamageValue() == 15, "Native HT fungus recognition works on nylium");
    discardFalls(level, origin, 12);
  }

  private static void verifyWarpedFungus(ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepareSpecies(
        level,
        origin,
        Blocks.WARPED_STEM.defaultBlockState(),
        Blocks.WARPED_WART_BLOCK.defaultBlockState(),
        Blocks.WARPED_NYLIUM.defaultBlockState());
    ItemStack axe = equip(player);
    fell(player, origin, 16);
    require(axe.getDamageValue() == 15, "Warped fungus preserves native felling and cost");
    discardFalls(level, origin, 12);
  }

  private static void verifyArtificialFungusCanopy(
      ServerLevel level, ServerPlayer player, BlockPos origin) {
    prepareSpecies(
        level,
        origin,
        Blocks.OAK_LOG.defaultBlockState(),
        Blocks.NETHER_WART_BLOCK.defaultBlockState(),
        Blocks.DIRT.defaultBlockState());
    ItemStack axe = equip(player);
    breakLog(player, origin);
    require(
        axe.getDamageValue() == 1 && level.getBlockState(origin.above(9)).is(Blocks.OAK_LOG),
        "A wart-block canopy cannot turn oak timbers into a natural tree");
    prepareSpecies(
        level,
        origin,
        Blocks.CRIMSON_STEM.defaultBlockState(),
        Blocks.SHROOMLIGHT.defaultBlockState(),
        Blocks.DIRT.defaultBlockState());
    axe = equip(player);
    breakLog(player, origin);
    require(
        axe.getDamageValue() == 1 && level.getBlockState(origin.above(9)).is(Blocks.CRIMSON_STEM),
        "Fungus-like foliage still requires the native matching nylium footing");
  }

  private static void verifyDesertTree(ServerLevel level, ServerPlayer player, BlockPos origin) {
    BlockState log =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.fromNamespaceAndPath("natures_spirit", "saxaul_log"))
            .orElseThrow()
            .defaultBlockState();
    BlockState leaves =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.fromNamespaceAndPath("natures_spirit", "saxaul_leaves"))
            .orElseThrow()
            .defaultBlockState()
            .setValue(LeavesBlock.DISTANCE, 1);
    prepareSpecies(level, origin, log, leaves, Blocks.SAND.defaultBlockState());
    ItemStack axe = equip(player);
    fell(player, origin, 16);
    require(
        axe.getDamageValue() == 15,
        "Native sandy-sapling survival supports the selected desert tree");
    discardFalls(level, origin, 12);
  }

  private static void verifyCoconutTree(ServerLevel level, ServerPlayer player, BlockPos origin) {
    BlockState log =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.fromNamespaceAndPath("natures_spirit", "coconut_log"))
            .orElseThrow()
            .defaultBlockState();
    BlockState leaves =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.fromNamespaceAndPath("natures_spirit", "coconut_leaves"))
            .orElseThrow()
            .defaultBlockState()
            .setValue(LeavesBlock.DISTANCE, 1);
    prepareSpecies(level, origin, log, leaves, Blocks.SAND.defaultBlockState());
    ItemStack axe = equip(player);
    fell(player, origin, 16);
    require(axe.getDamageValue() == 15, "Native coconut tree can be felled on sand");
    discardFalls(level, origin, 12);
    prepareSpecies(level, origin, log, leaves, Blocks.STONE_BRICKS.defaultBlockState());
    axe = equip(player);
    breakLog(player, origin);
    require(
        axe.getDamageValue() == 1 && level.getBlockState(origin.above(9)).equals(log),
        "Coconut sprout's broad survival cannot authorize mass felling built timbers");
  }

  private static void prepareSpecies(
      ServerLevel level, BlockPos origin, BlockState log, BlockState leaves, BlockState soil) {
    level.setBlockAndUpdate(origin.below(), soil);
    for (int height = 0; height < 10; height++) level.setBlockAndUpdate(origin.above(height), log);
    level.setBlockAndUpdate(origin.above(10), leaves);
  }

  private static void verifySharedCutsAfterManualRemoval(
      ServerLevel level, ServerPlayer firstPlayer, BlockPos origin) {
    prepare(level, origin, 100);
    BlockPos secondRoot = origin.east();
    BlockPos thirdRoot = secondRoot.south();
    level.setBlockAndUpdate(secondRoot, Blocks.OAK_LOG.defaultBlockState());
    level.setBlockAndUpdate(secondRoot.below(), Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(thirdRoot, Blocks.OAK_LOG.defaultBlockState());
    level.setBlockAndUpdate(thirdRoot.below(), Blocks.DIRT.defaultBlockState());
    ItemStack firstAxe = equip(firstPlayer);
    for (int action = 0; action < 7; action++) breakLog(firstPlayer, origin);
    require(firstAxe.getDamageValue() == 7, "First player pays native progress on its trunk");
    try (var second =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("087a57a2-146d-4bb1-b5eb-cbed75203029"),
            "SharedTreeCuts",
            Vec3.atCenterOf(origin).add(2, 0, 1),
            null)) {
      second.player.setGameMode(GameType.SURVIVAL);
      ItemStack secondAxe = equip(second.player);
      for (int action = 0; action < 3; action++) breakLog(second.player, secondRoot);
      require(secondAxe.getDamageValue() == 3, "Second player contributes three native cuts");
      breakLog(firstPlayer, thirdRoot);
      require(firstAxe.getDamageValue() == 8, "Another surviving trunk has a separately paid cut");
      require(
          level.getBlockEntity(secondRoot) instanceof ChoppedLogBlock.MyEntity,
          "Both trunks carry independent native progress");
      firstPlayer.setShiftKeyDown(true);
      breakLog(firstPlayer, origin);
      firstPlayer.setShiftKeyDown(false);
      require(level.getBlockState(origin).isAir(), "Remove the first chopped trunk manually");
      var survivor = (ChoppedLogBlock.MyEntity) level.getBlockEntity(secondRoot);
      require(
          survivor != null && survivor.getChops() == 3,
          "Remaining trunk keeps the second player's native progress");
      // Exercise migration of this one unrecorded block beside other live chopped blocks.
      survivor.getPersistentData().remove("aeroscapes_tree_paid_chops_per_block");
      fell(second.player, secondRoot, 64);
      require(
          secondAxe.getDamageValue() + 1 == 152,
          "Remaining 101 logs credit both unrecorded and recorded surviving paid cuts");
    }
    discardFalls(level, origin, 102);
  }

  private static void discardFalls(ServerLevel level, BlockPos origin, int height) {
    for (var entity :
        level.getEntitiesOfClass(TreeEntity.class, new AABB(origin).inflate(3, height, 3))) {
      entity.discard();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
