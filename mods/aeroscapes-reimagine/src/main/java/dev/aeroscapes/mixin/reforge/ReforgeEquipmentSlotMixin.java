package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.herobrot.tieredneo.reforge.ReforgeMenu", remap = false)
abstract class ReforgeEquipmentSlotMixin {
  @WrapOperation(
      method =
          "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
      at =
          @At(
              value = "NEW",
              target = "(Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/Slot;",
              ordinal = 1))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Slot aeroscapes$onePiecePerRoll(
      Container container, int index, int x, int y, Operation<Slot> original) {
    return new Slot(container, index, x, y) {
      @Override
      public int getMaxStackSize() {
        return 1;
      }
    };
  }
}
