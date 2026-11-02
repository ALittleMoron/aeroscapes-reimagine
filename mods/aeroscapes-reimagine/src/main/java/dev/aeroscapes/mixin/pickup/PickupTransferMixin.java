package dev.aeroscapes.mixin.pickup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.creative.itemphysic.server.ItemPhysicServer;

@Mixin(value = ItemPhysicServer.class, remap = false)
public abstract class PickupTransferMixin {
  @WrapOperation(
      method = "playerPickup",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A final stalled insert can hide an earlier transfer.
  private static boolean aeroscapes$recognizePartialTransfer(
      Inventory inventory, ItemStack stack, Operation<Boolean> operation) {
    int countBefore = stack.getCount();
    boolean nativeResult = operation.call(inventory, stack);
    return nativeResult || stack.getCount() < countBefore;
  }
}
