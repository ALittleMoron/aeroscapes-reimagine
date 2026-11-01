package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fuzs.easyanvils.EasyAnvils;
import fuzs.easyanvils.config.ServerConfig;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import fuzs.easyanvils.world.inventory.state.AnvilMenuState;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ModAnvilMenu.class, remap = false)
public abstract class EasyAnvilsFreeRenameMixin {
  @WrapOperation(
      method = "createResult()V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lfuzs/easyanvils/world/inventory/state/AnvilMenuState;equals(Lfuzs/easyanvils/world/inventory/state/AnvilMenuState;Lfuzs/easyanvils/world/inventory/state/AnvilMenuState;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Stable pinned Easy Anvils comparator call.
  private boolean aeroscapes$allowConfiguredFreeRename(
      AnvilMenuState actual, AnvilMenuState vanilla, Operation<Boolean> original) {
    if (original.call(actual, vanilla)) return true;
    // Allurement changes the probe's rename price; preserve every non-price recipe distinction.
    return !actual.getLeftInput().isEmpty()
        && actual.getRightInput().isEmpty()
        && vanilla.getRightInput().isEmpty()
        && !actual.getResult().isEmpty()
        && actual.getLevelCost() >= 0
        && vanilla.getLevelCost() >= 0
        && EasyAnvils.CONFIG
            .get(ServerConfig.class)
            .costs
            .freeRenames
            .filter
            .test(actual.getLeftInput())
        && actual.getRepairItemCountCost() == 0
        && actual.getRepairItemCountCost() == vanilla.getRepairItemCountCost()
        && Objects.equals(actual.getItemName(), vanilla.getItemName())
        && ItemStack.matches(actual.getResult(), vanilla.getResult())
        && ItemStack.matches(actual.getLeftInput(), vanilla.getLeftInput())
        && ItemStack.matches(actual.getRightInput(), vanilla.getRightInput());
  }
}
