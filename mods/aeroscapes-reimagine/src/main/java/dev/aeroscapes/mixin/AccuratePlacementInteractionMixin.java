package dev.aeroscapes.mixin;

import dev.aeroscapes.qualityoflife.client.AccuratePlacementInteractions;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native @Unique helpers are merged by the provider's priority1000 mixin first. */
@Mixin(value = GameRenderer.class, priority = 900, remap = false)
@SuppressWarnings("PMD.UnusedPrivateMethod")
public abstract class AccuratePlacementInteractionMixin {
  @Inject(
      method = "doesBlockHaveOverriddenActivateMethod(Lnet/minecraft/world/level/block/Block;)Z",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned placement injection.
  private static void aeroscapes$modernBlockInteraction(
      Block block, CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(AccuratePlacementInteractions.blockActivates(block));
  }

  @Inject(
      method = "doesItemHaveOverriddenUseMethod(Lnet/minecraft/world/item/Item;)Z",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned placement injection.
  private static void aeroscapes$modernItemUse(
      Item item, CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(AccuratePlacementInteractions.itemUses(item));
  }
}
