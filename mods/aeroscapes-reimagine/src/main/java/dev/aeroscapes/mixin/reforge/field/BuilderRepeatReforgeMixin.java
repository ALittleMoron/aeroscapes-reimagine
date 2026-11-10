package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
abstract class BuilderRepeatReforgeMixin {
  @Shadow @Final private Minecraft minecraft;

  @WrapOperation(
      method = "performUseItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$constructionCadence(
      ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
    String rank = ReforgeProfiles.path(stack);
    InteractionResult result = original.call(stack, context);
    if (!rank.startsWith("builder_")
        || !result.consumesAction()
        || !(context.getPlayer() instanceof LocalPlayer player)) {
      return result;
    }
    var timing = (BuilderClickDelayAccess) minecraft;
    switch (rank) {
      case "builder_poor_stiff" -> timing.aeroscapes$setBuilderClickDelay(7);
      case "builder_fine_ready" -> timing.aeroscapes$setBuilderClickDelay(3);
      case "builder_exceptional_alignment" -> aeroscapes$showFace(player, context, false);
      case "builder_epic_repeat", "builder_legendary_layout" -> {
        timing.aeroscapes$setBuilderClickDelay(2);
        aeroscapes$showFace(player, context, rank.equals("builder_legendary_layout"));
      }
      default -> {}
    }
    return result;
  }

  private static void aeroscapes$showFace(
      LocalPlayer player, UseOnContext context, boolean coordinates) {
    String prefix = "aeroscapes_reimagine.equipment.builder.";
    var direction =
        Component.translatable(prefix + "face." + context.getClickedFace().getSerializedName());
    if (coordinates) {
      BlockPos pos = context.getClickedPos();
      player.displayClientMessage(
          Component.translatable(prefix + "layout", direction, pos.getX(), pos.getY(), pos.getZ()),
          true);
    } else {
      player.displayClientMessage(Component.translatable(prefix + "alignment", direction), true);
    }
  }
}
