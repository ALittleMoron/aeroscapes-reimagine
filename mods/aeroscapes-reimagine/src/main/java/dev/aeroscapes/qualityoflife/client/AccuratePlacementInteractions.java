package dev.aeroscapes.qualityoflife.client;

import net.clayborn.accurateblockplacement.config.AccurateBlockPlacementConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Repairs two outdated reflection predicates; performs no use/placement action itself. */
public final class AccuratePlacementInteractions {
  private static final ClassValue<Boolean> BLOCK_INTERACTION =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          return overrides(
                  type,
                  BlockBehaviour.class,
                  "useWithoutItem",
                  BlockState.class,
                  Level.class,
                  BlockPos.class,
                  Player.class,
                  BlockHitResult.class)
              || overrides(
                  type,
                  BlockBehaviour.class,
                  "useItemOn",
                  ItemStack.class,
                  BlockState.class,
                  Level.class,
                  BlockPos.class,
                  Player.class,
                  InteractionHand.class,
                  BlockHitResult.class);
        }
      };
  private static final ClassValue<Boolean> ITEM_USE =
      new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
          return overrides(
              type, Item.class, "use", Level.class, Player.class, InteractionHand.class);
        }
      };

  private AccuratePlacementInteractions() {}

  public static boolean blockActivates(Block block) {
    return BLOCK_INTERACTION.get(block.getClass());
  }

  public static boolean itemUses(Item item) {
    var player = Minecraft.getInstance().player;
    if (player != null && player.isUsingItem() && player.getUseItem().getItem() == item)
      return true;
    // Match the provider's explicit opt-ins, reading live options rather than caching them.
    if (item instanceof BlockItem
        || (AccurateBlockPlacementConfig.toolsEnabled
            && (item instanceof ShovelItem
                || item instanceof HoeItem
                || item instanceof AxeItem
                || item instanceof FlintAndSteelItem))
        || (AccurateBlockPlacementConfig.bucketEnabled && item instanceof BucketItem)
        || (AccurateBlockPlacementConfig.armorStandEnabled && item instanceof ArmorStandItem)
        || (AccurateBlockPlacementConfig.itemFrameEnabled && item instanceof ItemFrameItem)
        || (AccurateBlockPlacementConfig.spawnEggsEnabled && item instanceof SpawnEggItem))
      return false;
    return ITEM_USE.get(item.getClass());
  }

  private static boolean overrides(
      Class<?> type, Class<?> base, String name, Class<?>... parameters) {
    for (Class<?> current = type;
        current != null && current != base;
        current = current.getSuperclass()) {
      try {
        current.getDeclaredMethod(name, parameters);
        return true;
      } catch (NoSuchMethodException ignored) {
        // Protected block callbacks and inherited mod overrides both need hierarchy traversal.
        continue;
      }
    }
    return false;
  }
}
