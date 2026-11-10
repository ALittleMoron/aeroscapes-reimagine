package dev.aeroscapes.equipment.tools;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

public final class AreaToolActions {
  private static final int VEIN_LIMIT = 6;

  private AreaToolActions() {}

  public static @Nullable MiningPattern miningPattern(ServerPlayer player) {
    if (!canUseAreaTool(player)) return null;
    return switch (ReforgeProfiles.path(player.getMainHandItem())) {
      case "pick_legendary_tunnel" -> MiningPattern.TUNNEL;
      case "pick_legendary_vein" -> MiningPattern.VEIN;
      case "shovel_legendary_excavator" -> MiningPattern.EXCAVATION;
      case "shovel_legendary_grader" -> MiningPattern.GRADING;
      case "shears_legendary_gardener" -> MiningPattern.GARDEN;
      default -> null;
    };
  }

  public static @Nullable UsePattern usePattern(
      ServerPlayer player, ItemStack stack, InteractionHand hand) {
    if (!canUseAreaTool(player)) return null;
    if (hand == InteractionHand.OFF_HAND) {
      return ReforgeProfiles.has(player.getMainHandItem(), "hoe_legendary_row_sower")
              && isSeed(stack)
          ? UsePattern.SOW_ROW
          : null;
    }
    return switch (ReforgeProfiles.path(stack)) {
      case "shovel_legendary_path" -> UsePattern.PATH;
      case "axe_legendary_bark" -> UsePattern.BARK;
      case "hoe_legendary_tiller" -> UsePattern.TILL;
      case "hoe_legendary_sower" -> UsePattern.SOW;
      default -> null;
    };
  }

  private static boolean canUseAreaTool(ServerPlayer player) {
    return !player.isShiftKeyDown() && ToolReforges.isSurvivalPlayer(player);
  }

  public static boolean mine(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      BlockPos origin,
      MiningPattern pattern,
      BooleanSupplier original) {
    if (!canInteract(player, level, origin)) return original.getAsBoolean();
    BlockState center = level.getBlockState(origin);
    ItemStack tool = player.getMainHandItem();
    HitResult aimed = player.pick(player.blockInteractionRange(), 1.0F, false);
    if (!(aimed instanceof BlockHitResult hit)
        || hit.getType() != HitResult.Type.BLOCK
        || !origin.equals(hit.getBlockPos())
        || !pattern.accepts(center)
        || !canHarvest(player, level, origin, center, tool, Float.MAX_VALUE)) {
      return original.getAsBoolean();
    }
    float maximumHardness = Math.max(1.0F, center.getDestroySpeed(level, origin) * 2.0F);
    boolean success = original.getAsBoolean();
    if (!success
        || level.getBlockState(origin).is(center.getBlock())
        || !stillHolding(player, tool)) return success;

    var mining = new MiningAction(gameMode, player, level, tool, pattern, maximumHardness);
    if (pattern == MiningPattern.VEIN) {
      mineVein(mining, origin, center);
    } else if (pattern == MiningPattern.GRADING) {
      Direction side = player.getDirection().getClockWise();
      mining.breakTarget(origin.relative(side), center.getBlock());
      if (stillHolding(player, tool))
        mining.breakTarget(origin.relative(side.getOpposite()), center.getBlock());
    } else {
      Direction.Axis axis = hit.getDirection().getAxis();
      Direction first = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
      Direction second = axis == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
      for (BlockPos pos :
          BlockPos.betweenClosed(
              origin.relative(first, -1).relative(second, -1),
              origin.relative(first).relative(second))) {
        if (!stillHolding(player, tool)) break;
        if (!origin.equals(pos)) mining.breakTarget(pos.immutable(), null);
      }
    }
    return true;
  }

  private static void mineVein(MiningAction mining, BlockPos origin, BlockState center) {
    var pending = new ArrayDeque<BlockPos>();
    var seen = new HashSet<BlockPos>();
    seen.add(origin);
    pending.add(origin);
    int mined = 1;
    while (!pending.isEmpty()
        && mined < VEIN_LIMIT
        && stillHolding(mining.player(), mining.tool())) {
      BlockPos current = pending.removeFirst();
      for (Direction side : Direction.values()) {
        BlockPos next = current.relative(side);
        if (seen.add(next) && mining.breakTarget(next, center.getBlock())) {
          mined++;
          if (mined == VEIN_LIMIT || !stillHolding(mining.player(), mining.tool())) return;
          pending.addLast(next);
        }
      }
    }
  }

  public static InteractionResult use(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      ItemStack tool,
      InteractionHand hand,
      BlockHitResult hit,
      UsePattern pattern,
      Supplier<InteractionResult> original) {
    BlockPos origin = hit.getBlockPos();
    if (!canInteract(player, level, origin)) return original.get();
    BlockState center = level.getBlockState(origin);
    if (pattern == UsePattern.SOW) return sow(gameMode, player, level, hit, original);
    if (pattern == UsePattern.SOW_ROW) return sowRow(gameMode, player, level, tool, hit, original);

    BlockState expected = preview(player, hand, hit, center, pattern);
    InteractionResult result = original.get();
    if (expected == null
        || expected == center
        || !result.consumesAction()
        || level.getBlockState(origin) != expected
        || !stillHolding(player, tool)) return result;

    var use = new ToolUse(gameMode, player, level, hand, tool, pattern, hit.getDirection());
    switch (pattern) {
      case PATH -> use.row(origin, player.getDirection().getClockWise());
      case BARK ->
          use.row(
              origin,
              Direction.fromAxisAndDirection(
                  center.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE));
      case TILL -> use.square(origin);
      case SOW, SOW_ROW -> {}
    }
    return result;
  }

  private static InteractionResult sowRow(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      ItemStack seeds,
      BlockHitResult hit,
      Supplier<InteractionResult> original) {
    BlockPos origin = hit.getBlockPos();
    if (hit.getDirection() != Direction.UP
        || !isPlantable(level, origin)
        || !canInteract(player, level, origin.above())) return original.get();
    ItemStack hoe = player.getMainHandItem();
    var seed = (BlockItem) seeds.getItem();
    InteractionResult result = original.get();
    if (!result.consumesAction() || !level.getBlockState(origin.above()).is(seed.getBlock()))
      return result;
    var use =
        new ToolUse(
            gameMode,
            player,
            level,
            InteractionHand.OFF_HAND,
            seeds,
            UsePattern.SOW_ROW,
            Direction.UP);
    Direction forward = player.getDirection();
    for (int offset = 1; offset < 5 && stillHolding(player, hoe); offset++) {
      BlockPos next = origin.relative(forward, offset);
      if (!use.apply(next).consumesAction()
          || !level.getBlockState(next.above()).is(seed.getBlock())) break;
    }
    return result;
  }

  private static InteractionResult sow(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      BlockHitResult hit,
      Supplier<InteractionResult> original) {
    InteractionResult result = original.get();
    ItemStack seeds = player.getOffhandItem();
    BlockPos origin = hit.getBlockPos();
    if (result != InteractionResult.PASS
        || hit.getDirection() != Direction.UP
        || !isSeed(seeds)
        || !isPlantable(level, origin)) return result;

    var use =
        new ToolUse(
            gameMode, player, level, InteractionHand.OFF_HAND, seeds, UsePattern.SOW, Direction.UP);
    InteractionResult planted = use.apply(origin);
    if (planted.consumesAction() && !level.getBlockState(origin.above()).isAir())
      use.square(origin);
    return planted;
  }

  private static boolean isSeed(ItemStack stack) {
    return stack.getItem() instanceof BlockItem seed
        && (seed.getBlock() instanceof CropBlock || seed.getBlock() instanceof StemBlock);
  }

  private static boolean isPlantable(ServerLevel level, BlockPos pos) {
    return level.getBlockState(pos).getBlock() instanceof FarmBlock
        && !level.isOutsideBuildHeight(pos.above())
        && level.getBlockState(pos.above()).isAir();
  }

  private static @Nullable BlockState preview(
      ServerPlayer player,
      InteractionHand hand,
      BlockHitResult hit,
      BlockState current,
      UsePattern pattern) {
    if (current.hasBlockEntity() || pattern.ability == null) return null;
    if (pattern == UsePattern.BARK
        && (!current.is(BlockTags.LOGS) || !current.hasProperty(BlockStateProperties.AXIS)))
      return null;
    return current.getToolModifiedState(new UseOnContext(player, hand, hit), pattern.ability, true);
  }

  private static boolean canInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
    return !level.isOutsideBuildHeight(pos)
        && level.getWorldBorder().isWithinBounds(pos)
        && player.canInteractWithBlock(pos, 0.0)
        && isLoaded(level, pos)
        && level.mayInteract(player, pos)
        && !NaturalBlockProvenance.isOnShip(level, pos);
  }

  public static boolean canReapExtra(Player player, Level level, BlockPos pos) {
    if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
      return canInteract(serverPlayer, serverLevel, pos);
    }
    return !level.isOutsideBuildHeight(pos)
        && isLoaded(level, pos)
        && player.canInteractWithBlock(pos, 0.0);
  }

  private static boolean isLoaded(Level level, BlockPos pos) {
    int x = SectionPos.blockToSectionCoord(pos.getX());
    int z = SectionPos.blockToSectionCoord(pos.getZ());
    if (level instanceof ServerLevel server)
      return server.getChunkSource().getChunkNow(x, z) != null;
    return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null;
  }

  private static boolean canHarvest(
      ServerPlayer player,
      ServerLevel level,
      BlockPos pos,
      BlockState state,
      ItemStack tool,
      float maximumHardness) {
    if (state.hasBlockEntity()
        || state.isSignalSource()
        || state.getBlock() instanceof PistonBaseBlock
        || state.is(BlockTags.RAILS)
        || state.is(BlockTags.DOORS)
        || state.is(BlockTags.TRAPDOORS)
        || !tool.getItem().canAttackBlock(state, level, pos, player)) return false;
    float hardness = state.getDestroySpeed(level, pos);
    return hardness >= 0
        && hardness <= maximumHardness
        && state.canHarvestBlock(level, pos, player);
  }

  private static boolean stillHolding(ServerPlayer player, ItemStack tool) {
    return !tool.isEmpty() && player.getMainHandItem() == tool;
  }

  public enum MiningPattern {
    TUNNEL,
    VEIN,
    EXCAVATION,
    GRADING,
    GARDEN;

    private boolean accepts(BlockState state) {
      return switch (this) {
        case TUNNEL -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        case VEIN -> state.is(Tags.Blocks.ORES) && state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        case EXCAVATION -> state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        case GRADING -> ToolReforges.isLooseGround(state);
        case GARDEN -> state.is(BlockTags.LEAVES) || state.is(Blocks.VINE);
      };
    }
  }

  public enum UsePattern {
    PATH(ItemAbilities.SHOVEL_FLATTEN),
    BARK(ItemAbilities.AXE_STRIP),
    TILL(ItemAbilities.HOE_TILL),
    SOW(null),
    SOW_ROW(null);

    private final @Nullable ItemAbility ability;

    UsePattern(@Nullable ItemAbility ability) {
      this.ability = ability;
    }
  }

  private record MiningAction(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      ItemStack tool,
      MiningPattern pattern,
      float maximumHardness) {
    private boolean breakTarget(BlockPos pos, @Nullable Block requiredBlock) {
      if (!canInteract(player, level, pos)) return false;
      BlockState state = level.getBlockState(pos);
      return (requiredBlock == null || state.is(requiredBlock))
          && pattern.accepts(state)
          && canHarvest(player, level, pos, state, tool, maximumHardness)
          && gameMode.destroyBlock(pos)
          && !level.getBlockState(pos).is(state.getBlock());
    }
  }

  private record ToolUse(
      ServerPlayerGameMode gameMode,
      ServerPlayer player,
      ServerLevel level,
      InteractionHand hand,
      ItemStack stack,
      UsePattern pattern,
      Direction face) {
    private void row(BlockPos origin, Direction side) {
      apply(origin.relative(side));
      apply(origin.relative(side.getOpposite()));
    }

    private void square(BlockPos origin) {
      for (BlockPos pos :
          BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 0, 1))) {
        if (stack.isEmpty() || player.getItemInHand(hand) != stack) break;
        if (!origin.equals(pos)) apply(pos.immutable());
      }
    }

    private InteractionResult apply(BlockPos pos) {
      if (stack.isEmpty()
          || player.getItemInHand(hand) != stack
          || !canInteract(player, level, pos)) return InteractionResult.PASS;
      var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
      if (pattern == UsePattern.SOW || pattern == UsePattern.SOW_ROW) {
        if (!isPlantable(level, pos) || !canInteract(player, level, pos.above()))
          return InteractionResult.PASS;
      } else {
        BlockState state = level.getBlockState(pos);
        BlockState expected = preview(player, hand, hit, state, pattern);
        if (expected == null || expected == state) return InteractionResult.PASS;
      }
      return gameMode.useItemOn(player, level, stack, hand, hit);
    }
  }
}
