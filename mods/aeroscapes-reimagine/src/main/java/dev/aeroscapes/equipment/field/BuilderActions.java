package dev.aeroscapes.equipment.field;

import dev.aeroscapes.mixin.reforge.field.BuilderClickDelayAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public final class BuilderActions {
  private BuilderActions() {}

  public static void apply(
      Minecraft minecraft,
      String rank,
      UseOnContext context,
      InteractionResult result,
      boolean actionBar) {
    if (!rank.startsWith("builder_")
        || !result.consumesAction()
        || !(context.getPlayer() instanceof LocalPlayer player)) {
      return;
    }
    var timing = (BuilderClickDelayAccess) minecraft;
    switch (rank) {
      case "builder_poor_stiff" -> timing.aeroscapes$setBuilderClickDelay(7);
      case "builder_fine_ready" -> timing.aeroscapes$setBuilderClickDelay(3);
      case "builder_exceptional_alignment" ->
          aeroscapes$showFace(player, context, false, actionBar);
      case "builder_epic_repeat", "builder_legendary_layout" -> {
        timing.aeroscapes$setBuilderClickDelay(2);
        aeroscapes$showFace(player, context, rank.equals("builder_legendary_layout"), actionBar);
      }
      default -> {}
    }
  }

  private static void aeroscapes$showFace(
      LocalPlayer player, UseOnContext context, boolean coordinates, boolean actionBar) {
    String prefix = "aeroscapes_reimagine.equipment.builder.";
    var direction =
        Component.translatable(prefix + "face." + context.getClickedFace().getSerializedName());
    if (coordinates) {
      BlockPos pos = context.getClickedPos();
      player.displayClientMessage(
          Component.translatable(prefix + "layout", direction, pos.getX(), pos.getY(), pos.getZ()),
          actionBar);
    } else {
      player.displayClientMessage(
          Component.translatable(prefix + "alignment", direction), actionBar);
    }
  }
}
