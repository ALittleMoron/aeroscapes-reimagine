package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchy.client.render.PunchyArmRenderer;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class PunchyMapMixin {
  @Inject(
      method =
          "isFunctionalMap(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;)Z",
      at = @At("HEAD"),
      cancellable = true,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin callback; held-map data may arrive later.
  private static void aeroscapes$renderPendingMap(
      LocalPlayer player, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
    if (stack != null && stack.getItem() instanceof MapItem) callback.setReturnValue(true);
  }
}
