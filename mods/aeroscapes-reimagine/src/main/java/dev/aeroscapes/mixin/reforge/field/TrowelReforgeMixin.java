package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.TrowelReforges;
import dev.aeroscapes.equipment.tools.ToolReforges;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.violetmoon.quark.content.tools.item.TrowelItem", remap = false)
abstract class TrowelReforgeMixin {
  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              ordinal = 0,
              target =
                  "Lnet/minecraft/world/item/ItemStack;set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Object aeroscapes$preservePreviewSeed(
      ItemStack stack,
      DataComponentType<?> component,
      Object value,
      Operation<Object> original,
      @Local(argsOnly = true) UseOnContext context) {
    if (context.getPlayer() != null
        && context.getPlayer().isShiftKeyDown()
        && ReforgeProfiles.has(stack, "trowel_legendary_preview")) return stack.get(component);
    return original.call(stack, component, value);
  }

  @WrapOperation(
      method = "useOn",
      at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Object aeroscapes$useChosenSlot(
      List<Integer> candidates,
      int index,
      Operation<Object> original,
      @Local(argsOnly = true) UseOnContext context) {
    Object chosen = original.call(candidates, index);
    return TrowelReforges.selectedSlot(context, candidates, (Integer) chosen);
  }

  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$pinPreview(
      ItemStack block,
      UseOnContext nativeContext,
      Operation<InteractionResult> original,
      @Local(argsOnly = true) UseOnContext context,
      @Local int chosenSlot) {
    if (TrowelReforges.preview(context, block, chosenSlot)) return InteractionResult.FAIL;
    String path = ReforgeProfiles.path(context.getItemInHand());
    ItemStack prior = path.startsWith("trowel_") ? block.copyWithCount(1) : ItemStack.EMPTY;
    InteractionResult result = original.call(block, nativeContext);
    if (!prior.isEmpty()) TrowelReforges.used(context, prior, chosenSlot, result);
    return result;
  }

  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/violetmoon/zeta/util/MiscUtil;damageStack(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$trowelWear(
      ItemStack stack, int wear, Player player, EquipmentSlot slot, Operation<Void> original) {
    int saving =
        switch (ReforgeProfiles.path(stack)) {
          case "trowel_fine_selection" -> 15;
          case "trowel_epic_repeat", "trowel_legendary_preview" -> 30;
          default -> 0;
        };
    original.call(stack, ToolReforges.modifyWear(player, wear, saving), player, slot);
  }
}
