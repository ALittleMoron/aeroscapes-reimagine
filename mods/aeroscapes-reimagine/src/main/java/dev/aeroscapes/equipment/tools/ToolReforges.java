package dev.aeroscapes.equipment.tools;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

public final class ToolReforges {
  private ToolReforges() {}

  public static void register(IEventBus modBus) {
    NaturalBlockProvenance.register(modBus);
    NeoForge.EVENT_BUS.addListener(ToolReforges::addMiningFortune);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolReforges::adjustMiningSpeed);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolReforges::adjustMiningDrops);
    if (ModList.get().isLoaded("farmersdelight"))
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolReforges::allowBatchCut);
  }

  private static void adjustMiningSpeed(PlayerEvent.BreakSpeed event) {
    Player player = event.getEntity();
    if (!isSurvivalPlayer(player)) return;
    BlockState block = event.getState();
    float factor =
        switch (ReforgeProfiles.path(player.getMainHandItem())) {
          case "tool_exceptional_with_ore_edge" -> block.is(Tags.Blocks.ORES) ? 1.10F : 1;
          case "tool_exceptional_with_stone_edge" -> isRock(block) ? 1.08F : 1;
          case "pick_epic_reach_with_stone_edge" -> isRock(block) ? 1.14F : 1;
          case "tool_poor_with_drifting_edge" ->
              block.is(BlockTags.SAND) || block.is(BlockTags.SNOW) ? 0.85F : 1;
          case "tool_exceptional_with_sand_edge" -> block.is(BlockTags.SAND) ? 1.15F : 1;
          case "tool_exceptional_with_snow_edge" -> block.is(BlockTags.SNOW) ? 1.15F : 1;
          case "shovel_epic_with_earth_edge" -> isLooseGround(block) ? 1.12F : 1;
          case "axe_epic_wood_with_woodsman" -> block.is(BlockTags.LOGS) ? 1.12F : 1;
          case "shears_epic_with_leaf_edge" -> block.is(BlockTags.LEAVES) ? 1.12F : 1;
          case "tool_poor_with_water_drag" -> isSubmerged(player) ? 0.88F : 1;
          case "tool_exceptional_with_water_edge" -> isSubmerged(player) ? 1.12F : 1;
          case "underwater_tool_epic_with_water_edge" -> isSubmerged(player) ? 1.16F : 1;
          default -> 1;
        };
    if (factor != 1) event.setNewSpeed(event.getNewSpeed() * factor);
  }

  public static int miningWear(ItemStack tool, BlockState block, LivingEntity user, int amount) {
    if (!(user instanceof ServerPlayer player) || !isSurvivalPlayer(player)) return amount;
    int chance =
        switch (ReforgeProfiles.path(tool)) {
          case "tool_poor_with_brittle_point" ->
              block.is(Blocks.DEEPSLATE) || block.is(Blocks.BASALT) || block.is(Blocks.BLACKSTONE)
                  ? -20
                  : 0;
          case "tool_ordinary_with_stone_edge" -> isRock(block) ? 5 : 0;
          case "tool_fine_with_stone_edge" -> isRock(block) ? 10 : 0;
          case "tool_exceptional_with_stone_edge" -> isRock(block) ? 15 : 0;
          case "pick_epic_reach_with_stone_edge" -> isRock(block) ? 22 : 0;
          case "tool_ordinary_with_earth_edge" -> isLooseGround(block) ? 5 : 0;
          case "tool_fine_with_earth_edge" -> isLooseGround(block) ? 10 : 0;
          case "shovel_epic_with_earth_edge" -> isLooseGround(block) ? 20 : 0;
          case "tool_poor_with_split_edge" -> isChoppableLog(block) ? -20 : 0;
          case "axe_exceptional_damage_with_woodsman" -> isChoppableLog(block) ? 15 : 0;
          case "axe_epic_wood_with_woodsman" -> isChoppableLog(block) ? 22 : 0;
          case "shears_ordinary_with_leaf_edge" -> block.is(BlockTags.LEAVES) ? 5 : 0;
          case "shears_fine_with_leaf_edge" -> block.is(BlockTags.LEAVES) ? 10 : 0;
          case "shears_exceptional_with_leaf_edge" -> block.is(BlockTags.LEAVES) ? 16 : 0;
          case "shears_epic_with_leaf_edge" -> block.is(BlockTags.LEAVES) ? 22 : 0;
          case "tool_ordinary_with_water_edge" -> isSubmerged(player) ? 5 : 0;
          case "tool_fine_with_water_edge" -> isSubmerged(player) ? 10 : 0;
          case "tool_exceptional_with_water_edge" -> isSubmerged(player) ? 15 : 0;
          case "underwater_tool_epic_with_water_edge" -> isSubmerged(player) ? 22 : 0;
          default -> 0;
        };
    return modifyWear(player, amount, chance);
  }

  public static boolean isSubmerged(Player player) {
    return player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
  }

  public static int barkWear(ItemStack tool, LivingEntity user, int amount) {
    int chance =
        switch (ReforgeProfiles.path(tool)) {
          case "tool_ordinary_with_bark_edge" -> 5;
          case "tool_fine_with_bark_edge" -> 12;
          case "axe_exceptional_damage_with_woodsman" -> 15;
          case "axe_epic_wood_with_woodsman" -> 22;
          default -> 0;
        };
    return modifyWear(user, amount, chance);
  }

  public static int tillingWear(ItemStack tool, LivingEntity user, int amount) {
    int chance =
        switch (ReforgeProfiles.path(tool)) {
          case "tool_poor_with_brittle_tine" -> -20;
          case "tool_ordinary_with_tilling_tine" -> 5;
          case "tool_fine_with_tilling_tine" -> 10;
          case "tool_exceptional_reach_with_tilling_tine" -> 15;
          case "hoe_epic_with_tilling_tine" -> 22;
          default -> 0;
        };
    return modifyWear(user, amount, chance);
  }

  public static int modifyWear(LivingEntity user, int amount, int chance) {
    if (amount <= 0
        || chance == 0
        || !(user instanceof ServerPlayer player)
        || !isSurvivalPlayer(player)
        || player.getRandom().nextInt(100) >= Math.abs(chance)) return amount;
    return chance < 0 ? amount + 1 : 0;
  }

  static boolean isLooseGround(BlockState state) {
    return state.is(BlockTags.DIRT)
        || state.is(BlockTags.SAND)
        || state.is(BlockTags.SNOW)
        || state.is(Blocks.GRAVEL);
  }

  private static boolean isRock(BlockState state) {
    return state.is(Blocks.STONE)
        || state.is(Blocks.DEEPSLATE)
        || state.is(Blocks.GRANITE)
        || state.is(Blocks.DIORITE)
        || state.is(Blocks.ANDESITE)
        || state.is(Blocks.TUFF)
        || state.is(Blocks.CALCITE)
        || state.is(Blocks.BASALT)
        || state.is(Blocks.BLACKSTONE);
  }

  private static boolean isChoppableLog(BlockState state) {
    return state.is(BlockTags.LOGS)
        || (ModList.get().isLoaded("treechop") && TreeChopLogs.isChopped(state));
  }

  private static final class TreeChopLogs {
    private static boolean isChopped(BlockState state) {
      return state.getBlock() instanceof ht.treechop.common.block.ChoppedLogBlock;
    }
  }

  private static void allowBatchCut(PlayerInteractEvent.RightClickBlock event) {
    if (event.getUseBlock() == TriState.DEFAULT
        && !event.getUseItem().isFalse()
        && event.getEntity().isShiftKeyDown()
        && ReforgeProfiles.has(event.getItemStack(), "knife_legendary_batch")
        && event.getLevel().getBlockEntity(event.getPos()) instanceof CuttingBoardBlockEntity board
        && !board.getStoredItem().isEmpty()) {
      // Secondary use ordinarily bypasses the board's interaction with a held knife.
      event.setUseBlock(TriState.TRUE);
    }
  }

  public static boolean isSurvivalPlayer(Player player) {
    if (player instanceof ServerPlayer serverPlayer) {
      return !(serverPlayer instanceof FakePlayer)
          && serverPlayer.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
    }
    return !player.isCreative() && !player.isSpectator();
  }

  private static void addMiningFortune(GetEnchantmentLevelEvent event) {
    if (!event.isTargetting(Enchantments.FORTUNE)
        || !ReforgeProfiles.has(event.getStack(), "pick_epic_fortune")) return;
    event
        .getHolder(Enchantments.FORTUNE)
        .ifPresent(
            fortune -> {
              var enchantments = event.getEnchantments();
              enchantments.set(fortune, enchantments.getLevel(fortune) + 1);
            });
  }

  private static void adjustMiningDrops(BlockDropsEvent event) {
    if (!(event.getBreaker() instanceof ServerPlayer player)
        || !isSurvivalPlayer(player)
        || event.getBlockEntity() != null
        || event.getState().hasBlockEntity()
        || event.getDrops().isEmpty()) return;

    switch (ReforgeProfiles.path(event.getTool())) {
      case "pick_legendary_extra" -> addExtraDrop(event, player);
      case "shovel_epic_flint" -> improveFlintDrop(event, player);
      case "shears_legendary_cuttings" -> propagateLeaf(event, player);
      default -> {}
    }
  }

  private static void propagateLeaf(BlockDropsEvent event, ServerPlayer player) {
    BlockState state = event.getState();
    if (!state.hasProperty(BlockStateProperties.PERSISTENT)
        || state.getValue(BlockStateProperties.PERSISTENT)
        || !NaturalBlockProvenance.isNatural(event.getLevel(), event.getPos())) return;
    Item sapling;
    if (state.is(Blocks.OAK_LEAVES)) sapling = Items.OAK_SAPLING;
    else if (state.is(Blocks.SPRUCE_LEAVES)) sapling = Items.SPRUCE_SAPLING;
    else if (state.is(Blocks.BIRCH_LEAVES)) sapling = Items.BIRCH_SAPLING;
    else if (state.is(Blocks.JUNGLE_LEAVES)) sapling = Items.JUNGLE_SAPLING;
    else if (state.is(Blocks.ACACIA_LEAVES)) sapling = Items.ACACIA_SAPLING;
    else if (state.is(Blocks.DARK_OAK_LEAVES)) sapling = Items.DARK_OAK_SAPLING;
    else if (state.is(Blocks.CHERRY_LEAVES)) sapling = Items.CHERRY_SAPLING;
    else if (state.is(Blocks.MANGROVE_LEAVES)) sapling = Items.MANGROVE_PROPAGULE;
    else return;
    if (player.getRandom().nextFloat() >= 0.10F) return;
    for (ItemEntity drop : event.getDrops()) {
      ItemStack item = drop.getItem();
      if (!item.is(state.getBlock().asItem()) || !item.getComponentsPatch().isEmpty()) continue;
      ItemStack cutting = new ItemStack(sapling);
      if (item.getCount() == 1) {
        drop.setItem(cutting);
      } else {
        item.shrink(1);
        event.getDrops().add(copyDrop(drop, cutting));
      }
      return;
    }
  }

  private static void addExtraDrop(BlockDropsEvent event, ServerPlayer player) {
    if (player.getRandom().nextFloat() >= 0.12F
        || !NaturalBlockProvenance.isNatural(event.getLevel(), event.getPos())) return;
    for (ItemEntity drop : event.getDrops()) {
      ItemStack item = drop.getItem();
      if (item.isEmpty() || item.getMaxStackSize() == 1 || !item.getComponentsPatch().isEmpty())
        continue;
      if (item.getCount() < item.getMaxStackSize()) {
        item.grow(1);
      } else {
        event.getDrops().add(copyDrop(drop, item.copyWithCount(1)));
      }
      return;
    }
  }

  private static void improveFlintDrop(BlockDropsEvent event, ServerPlayer player) {
    if (!event.getState().is(Blocks.GRAVEL) || player.getRandom().nextFloat() >= 0.15F) return;
    var enchantments = event.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    if (event.getTool().getEnchantmentLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH)) > 0)
      return;
    for (ItemEntity drop : event.getDrops()) {
      ItemStack item = drop.getItem();
      if (!item.is(Items.GRAVEL) || !item.getComponentsPatch().isEmpty()) continue;
      ItemStack flint = new ItemStack(Items.FLINT);
      if (item.getCount() == 1) {
        drop.setItem(flint);
      } else {
        item.shrink(1);
        event.getDrops().add(copyDrop(drop, flint));
      }
      return;
    }
  }

  private static ItemEntity copyDrop(ItemEntity source, ItemStack item) {
    ItemEntity drop =
        new ItemEntity(source.level(), source.getX(), source.getY(), source.getZ(), item);
    drop.setDeltaMovement(source.getDeltaMovement());
    drop.setDefaultPickUpDelay();
    return drop;
  }

  public static List<ItemStack> bonusWool(
      IShearable target, Player player, ItemStack tool, List<ItemStack> drops) {
    if (!(target instanceof Sheep)
        || !(player instanceof ServerPlayer serverPlayer)
        || !isSurvivalPlayer(serverPlayer)
        || !ReforgeProfiles.has(tool, "shears_legendary_shepherd")
        || serverPlayer.getRandom().nextFloat() >= 0.20F) return drops;
    for (ItemStack stack : drops) {
      if (!stack.is(ItemTags.WOOL)) continue;
      List<ItemStack> result = new ArrayList<>(drops);
      result.add(stack.copyWithCount(1));
      return result;
    }
    return drops;
  }
}
