package dev.aeroscapes.trees;

import dev.pandasystems.fallingtrees.api.TreeData;
import dev.pandasystems.fallingtrees.api.TreeHandler;
import dev.ryanhcode.sable.Sable;
import ht.treechop.api.ChopData;
import ht.treechop.common.block.ChoppedLogBlock;
import ht.treechop.common.chop.ChopUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** One server-thread chop transaction, including native Panda and final-break charges. */
public final class TreeTransactions {
  private static final String PAID_CHOPS = "aeroscapes_tree_paid_chops_per_block";
  private static final ThreadLocal<Transaction> CURRENT = new ThreadLocal<>();

  private TreeTransactions() {}

  public static void begin(ServerLevel level, ServerPlayer player, BlockPos position) {
    if (CURRENT.get() != null) throw new IllegalStateException("Nested tree transaction");
    Transaction transaction = new Transaction(level, player, position.immutable());
    CURRENT.set(transaction);
  }

  public static void end() {
    CURRENT.remove();
  }

  public static boolean onShip(ServerLevel level, BlockPos position) {
    return Sable.HELPER.getContaining(level, position) != null;
  }

  public static boolean capture(ChopData data) {
    Transaction transaction = CURRENT.get();
    if (transaction == null || data.getTree().isEmpty()) return false;
    var tree = data.getTree().orElseThrow();
    transaction.logs = tree.streamLogs().map(BlockPos::immutable).distinct().toList();
    transaction.paid = readPaid(transaction.level, transaction.logs);
    for (BlockPos position : transaction.logs) {
      if (transaction.level.getBlockEntity(position) instanceof ChoppedLogBlock.MyEntity entity) {
        transaction.chopsBefore.put(position, entity.getChops());
        transaction.paidBefore.put(position, paidChops(entity));
      }
    }
    var leaves = tree.streamLeaves().map(BlockPos::immutable).distinct().toList();
    return safeTree(transaction, leaves);
  }

  private static int readPaid(ServerLevel level, List<BlockPos> logs) {
    int paid = 0;
    for (BlockPos position : logs) {
      if (level.getBlockEntity(position) instanceof ChoppedLogBlock.MyEntity entity) {
        paid = Math.addExact(paid, paidChops(entity));
      }
    }
    return paid;
  }

  private static int paidChops(ChoppedLogBlock.MyEntity entity) {
    return entity.getPersistentData().contains(PAID_CHOPS)
        ? Math.max(0, entity.getPersistentData().getInt(PAID_CHOPS))
        : entity.getChops();
  }

  public static void recordProgress() {
    Transaction transaction = CURRENT.get();
    if (transaction == null || transaction.finalRequested) return;
    BlockPos charged = null;
    for (BlockPos position : transaction.logs) {
      if (transaction.level.getBlockEntity(position) instanceof ChoppedLogBlock.MyEntity entity
          && entity.getChops() > transaction.chopsBefore.getOrDefault(position, 0)
          && (charged == null || position.equals(transaction.position))) {
        charged = position;
      }
    }
    for (BlockPos position : transaction.logs) {
      if (transaction.level.getBlockEntity(position) instanceof ChoppedLogBlock.MyEntity entity) {
        int paid = transaction.paidBefore.getOrDefault(position, 0);
        entity.getPersistentData().putInt(PAID_CHOPS, position.equals(charged) ? paid + 1 : paid);
        entity.setChanged();
      }
    }
  }

  public static boolean finalRequested() {
    Transaction transaction = CURRENT.get();
    return transaction != null && transaction.finalRequested;
  }

  public static boolean completed() {
    Transaction transaction = CURRENT.get();
    return transaction != null && transaction.completed;
  }

  public static boolean managedFall() {
    Transaction transaction = CURRENT.get();
    return transaction != null && transaction.prepared != null;
  }

  public static TreeData preparedData() {
    Transaction transaction = CURRENT.get();
    if (transaction == null || transaction.prepared == null) {
      throw new IllegalStateException("No prepared tree transaction");
    }
    return transaction.prepared;
  }

  public static int remainingWear(int logs, int paid) {
    if (logs < 1 || paid < 0) throw new IllegalArgumentException("Invalid tree accounting");
    return Math.max(0, Math.subtractExact(Math.addExact(logs, (logs + 1) / 2), paid));
  }

  public static int remainingWear() {
    Transaction transaction = CURRENT.get();
    if (transaction == null || transaction.prepared == null) {
      throw new IllegalStateException("No prepared tree cost");
    }
    return remainingWear(transaction.logCount, transaction.paid);
  }

  public static boolean prepareFinal(ht.treechop.api.TreeData tree) {
    Transaction transaction = CURRENT.get();
    if (transaction == null) throw new IllegalStateException("Felling outside chop transaction");
    transaction.finalRequested = true;
    transaction.logs = tree.streamLogs().map(BlockPos::immutable).distinct().toList();
    transaction.paid = readPaid(transaction.level, transaction.logs);
    List<BlockPos> leaves = tree.streamLeaves().map(BlockPos::immutable).distinct().toList();
    if (!safeTree(transaction, leaves)) {
      transaction.player.displayClientMessage(
          Component.translatable("aeroscapes.trees.unsafe"), true);
      return false;
    }
    transaction.logCount = transaction.logs.size();
    int remaining = remainingWear(transaction.logCount, transaction.paid);
    ItemStack tool = transaction.player.getMainHandItem();
    if (!transaction.player.isCreative()
        && tool.isDamageableItem()
        && tool.getMaxDamage() - tool.getDamageValue() < remaining) {
      transaction.player.displayClientMessage(
          Component.translatable("aeroscapes.trees.insufficient_durability", remaining), true);
      return false;
    }
    transaction.prepared = makeData(transaction, leaves);
    return true;
  }

  private static boolean safeTree(Transaction transaction, List<BlockPos> leaves) {
    if (transaction.logs.isEmpty() || transaction.logs.size() > 320) return false;
    ServerLevel level = transaction.level;
    boolean naturalLeaves =
        leaves.stream()
            .map(level::getBlockState)
            .anyMatch(
                state ->
                    state.is(BlockTags.LEAVES)
                        && (!state.hasProperty(LeavesBlock.PERSISTENT)
                            || !state.getValue(LeavesBlock.PERSISTENT)));
    if (!naturalLeaves && !nativeFungusCanopy(transaction, leaves)) return false;
    Set<BlockPos> logs = new HashSet<>(transaction.logs);
    int bottom = transaction.logs.stream().mapToInt(BlockPos::getY).min().orElseThrow();
    boolean rooted = false;
    for (BlockPos position : transaction.logs) {
      if (!safePosition(transaction, position)) return false;
      BlockState state = ChopUtil.getLogState(level, position);
      if (!state.is(BlockTags.LOGS)) return false;
      if (position.getY() == bottom && !logs.contains(position.below())) {
        BlockState below = level.getBlockState(position.below());
        if (TreeRoots.supported(transaction.level, position, state, below)) rooted = true;
        else if (!below.isAir() && !below.is(BlockTags.LEAVES)) return false;
      }
    }
    for (BlockPos position : leaves) if (!safePosition(transaction, position)) return false;
    return rooted;
  }

  private static boolean nativeFungusCanopy(Transaction transaction, List<BlockPos> leaves) {
    boolean crimson =
        transaction.logs.stream()
            .map(position -> ChopUtil.getLogState(transaction.level, position))
            .allMatch(state -> state.is(BlockTags.CRIMSON_STEMS));
    boolean warped =
        transaction.logs.stream()
            .map(position -> ChopUtil.getLogState(transaction.level, position))
            .allMatch(state -> state.is(BlockTags.WARPED_STEMS));
    return (crimson || warped)
        && leaves.stream()
            .map(transaction.level::getBlockState)
            .anyMatch(
                state ->
                    state.is(Blocks.SHROOMLIGHT)
                        || crimson && state.is(Blocks.NETHER_WART_BLOCK)
                        || warped && state.is(Blocks.WARPED_WART_BLOCK));
  }

  private static boolean safePosition(Transaction transaction, BlockPos position) {
    ServerLevel level = transaction.level;
    return level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)
        && !onShip(level, position)
        && level.mayInteract(transaction.player, position)
        && !transaction.player.blockActionRestricted(
            level, position, transaction.player.gameMode.getGameModeForPlayer())
        && (level.getBlockEntity(position) == null
            || level.getBlockEntity(position) instanceof ChoppedLogBlock.MyEntity);
  }

  private static TreeData makeData(Transaction transaction, List<BlockPos> leaves) {
    List<BlockPos> blocks = new ArrayList<>(transaction.logs);
    blocks.addAll(leaves);
    List<ItemStack> drops = new ArrayList<>();
    List<TreeData.AwardedStat> stats = new ArrayList<>();
    for (BlockPos position : blocks) {
      drops.addAll(
          Block.getDrops(
              ChopUtil.getLogState(transaction.level, position),
              transaction.level,
              position,
              transaction.level.getBlockEntity(position),
              transaction.player,
              transaction.player.getMainHandItem()));
    }
    for (BlockPos position : transaction.logs) {
      stats.add(
          new TreeData.AwardedStat(
              Stats.BLOCK_MINED.get(ChopUtil.getLogState(transaction.level, position).getBlock()),
              1));
    }
    int logCount = transaction.logCount;
    return new TreeData(blocks, drops, stats, logCount, speed -> speed, food -> food * logCount);
  }

  public static boolean animate() {
    Transaction transaction = CURRENT.get();
    if (transaction == null || transaction.prepared == null) return false;
    transaction.completed =
        TreeHandler.destroyTree(transaction.level, transaction.position, transaction.player);
    if (!transaction.completed) {
      transaction.player.displayClientMessage(
          Component.translatable("aeroscapes.trees.fall_declined"), true);
    }
    return transaction.completed;
  }

  private static final class Transaction {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos position;
    private List<BlockPos> logs = List.of();
    private final Map<BlockPos, Integer> chopsBefore = new HashMap<>();
    private final Map<BlockPos, Integer> paidBefore = new HashMap<>();
    private int paid;
    private int logCount;
    private boolean finalRequested;
    private boolean completed;
    private TreeData prepared;

    private Transaction(ServerLevel level, ServerPlayer player, BlockPos position) {
      this.level = level;
      this.player = player;
      this.position = position;
    }
  }
}
