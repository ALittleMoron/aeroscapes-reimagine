package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.trees.TreeTransactions;
import dev.pandasystems.fallingtrees.FallingTreesRegistriesKt;
import dev.pandasystems.fallingtrees.api.TreeData;
import dev.pandasystems.fallingtrees.api.TreeHandler;
import dev.pandasystems.fallingtrees.api.TreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TreeHandler.class, remap = false)
public abstract class PandaTreeHandlerMixin {
  @Inject(method = "destroyTree", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // TreeChop is the pack's sole harvesting owner.
  private static void aeroscapes$managedOnly(
      Level level, BlockPos position, Player player, CallbackInfoReturnable<Boolean> callback) {
    if (!TreeTransactions.managedFall()) callback.setReturnValue(false);
  }

  @ModifyExpressionValue(
      method = "destroyTree",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/pandasystems/fallingtrees/FallingTreesRegistriesKt;getTree(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/pandasystems/fallingtrees/api/TreeType;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // A chopped stump is not in minecraft:logs.
  private static TreeType aeroscapes$type(TreeType original) {
    return TreeTransactions.managedFall()
        ? FallingTreesRegistriesKt.getTree(
            ResourceLocation.fromNamespaceAndPath("fallingtrees", "generic"))
        : original;
  }

  @WrapOperation(
      method = "destroyTree",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/pandasystems/fallingtrees/api/TreeHandler;tryGatherTreeData(Ldev/pandasystems/fallingtrees/api/TreeType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Z)Ldev/pandasystems/fallingtrees/api/TreeData;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Both providers use one bounded, validated block set.
  private static TreeData aeroscapes$data(
      TreeHandler handler,
      TreeType type,
      BlockPos position,
      Level level,
      Player player,
      boolean ignoreExceptions,
      Operation<TreeData> original) {
    return TreeTransactions.preparedData();
  }

  @WrapOperation(
      method = "destroyTree",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native enchantment handling rolls only the unpaid cost.
  private static void aeroscapes$cost(
      ItemStack tool,
      int amount,
      LivingEntity player,
      EquipmentSlot slot,
      Operation<Void> original) {
    int remaining = TreeTransactions.remainingWear();
    if (remaining > 0) original.call(tool, remaining, player, slot);
  }
}
