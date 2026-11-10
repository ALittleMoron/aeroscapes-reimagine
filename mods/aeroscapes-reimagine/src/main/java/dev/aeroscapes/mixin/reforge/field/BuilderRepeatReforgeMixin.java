package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
abstract class BuilderRepeatReforgeMixin {
  private static final ResourceLocation MERCURIAL_WAND =
      ResourceLocation.fromNamespaceAndPath("theurgy", "mercurial_wand");

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
    aeroscapes$applyCadence(rank, context, result, true);
    return result;
  }

  @WrapOperation(
      method = "performUseItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;onItemUseFirst(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$wandCadence(
      ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
    boolean wand = MERCURIAL_WAND.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    String rank = wand ? ReforgeProfiles.path(stack) : "";
    InteractionResult result = original.call(stack, context);
    aeroscapes$applyCadence(rank, context, result, false);
    return result;
  }

  private void aeroscapes$applyCadence(
      String rank, UseOnContext context, InteractionResult result, boolean actionBar) {
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
