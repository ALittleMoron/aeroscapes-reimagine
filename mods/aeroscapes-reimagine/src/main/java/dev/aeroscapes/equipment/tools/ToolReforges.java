package dev.aeroscapes.equipment.tools;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

public final class ToolReforges {
  private ToolReforges() {}

  public static void register(IEventBus modBus) {
    NaturalBlockProvenance.register(modBus);
    NeoForge.EVENT_BUS.addListener(ToolReforges::addMiningFortune);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolReforges::adjustMiningDrops);
    if (ModList.get().isLoaded("farmersdelight"))
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolReforges::allowBatchCut);
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
      default -> {}
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
