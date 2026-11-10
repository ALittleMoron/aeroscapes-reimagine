package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class TrowelReforges {
  private static final Map<Player, Choice> CHOICES =
      Collections.synchronizedMap(new WeakHashMap<>());

  private TrowelReforges() {}

  public static int selectedSlot(UseOnContext context, List<Integer> candidates, int normalSlot) {
    Player player = context.getPlayer();
    if (player == null) return normalSlot;
    ItemStack trowel = context.getItemInHand();
    String profile = ReforgeProfiles.path(trowel);
    if (!profile.startsWith("trowel_")
        || profile.equals("trowel_ordinary")
        || profile.equals("trowel_poor_stiff")) {
      return normalSlot;
    }
    var choice = CHOICES.get(player);
    boolean reusable =
        choice != null
            && choice.trowel().get() == trowel
            && candidates.contains(choice.slot())
            && ItemStack.isSameItemSameComponents(
                choice.block(), player.getInventory().getItem(choice.slot()));
    if (reusable
        && (player.isShiftKeyDown() && !profile.equals("trowel_fine_selection")
            || profile.equals("trowel_legendary_preview") && matchesPin(context, choice))) {
      return choice.slot();
    }
    if (!player.isShiftKeyDown()) return normalSlot;
    return candidates.getFirst();
  }

  public static boolean preview(UseOnContext context, ItemStack block, int slot) {
    Player player = context.getPlayer();
    if (player == null
        || !(block.getItem() instanceof BlockItem)
        || !ReforgeProfiles.has(context.getItemInHand(), "trowel_legendary_preview")) return false;
    var previous = CHOICES.get(player);
    boolean matches =
        previous != null
            && previous.trowel().get() == context.getItemInHand()
            && matchesPin(context, previous);
    boolean same =
        matches
            && previous.slot() == slot
            && ItemStack.isSameItemSameComponents(previous.block(), block);
    if (!player.isShiftKeyDown()) {
      if (matches && !same) {
        CHOICES.remove(player);
        if (player instanceof ServerPlayer serverPlayer) {
          serverPlayer.displayClientMessage(
              Component.translatable("aeroscapes_reimagine.equipment.trowel.unavailable"), true);
        }
        return true;
      }
      return false;
    }
    if (same) return true;
    CHOICES.put(
        player,
        new Choice(
            new WeakReference<>(context.getItemInHand()),
            block.copyWithCount(1),
            slot,
            context.getClickedPos().immutable(),
            context.getClickedFace(),
            context.getLevel().getGameTime() + 100));
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.displayClientMessage(
          Component.translatable(
              "aeroscapes_reimagine.equipment.trowel.preview", block.getHoverName()),
          true);
    }
    return true;
  }

  private static boolean matchesPin(UseOnContext context, Choice choice) {
    return choice.position() != null
        && choice.position().equals(context.getClickedPos())
        && choice.face() == context.getClickedFace()
        && choice.expires() >= context.getLevel().getGameTime();
  }

  public static void used(
      UseOnContext context, ItemStack block, int slot, InteractionResult result) {
    Player player = context.getPlayer();
    if (player == null || !result.consumesAction()) return;
    ItemStack trowel = context.getItemInHand();
    switch (ReforgeProfiles.path(trowel)) {
      case "trowel_poor_stiff" -> player.getCooldowns().addCooldown(trowel.getItem(), 4);
      case "trowel_exceptional_repeat", "trowel_epic_repeat", "trowel_legendary_preview" ->
          CHOICES.put(
              player,
              new Choice(new WeakReference<>(trowel), block.copyWithCount(1), slot, null, null, 0));
      default -> {}
    }
  }

  private record Choice(
      WeakReference<ItemStack> trowel,
      ItemStack block,
      int slot,
      BlockPos position,
      Direction face,
      long expires) {}
}
