package dev.aeroscapes.mixin.inventory;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.event.CuriosEventHandler;

@Mixin(CuriosEventHandler.class)
public abstract class CuriosQuickEquipMixin {
  @Inject(method = "curioRightClick", at = @At("HEAD"), cancellable = true, remap = false)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the mixin injector.
  private void aeroscapes$deferEquip(
      PlayerInteractEvent.RightClickItem event, CallbackInfo callback) {
    // ItemStack.use handles every air equip after interaction events have run. The native listener
    // writes on both sides, including swaps with the same item, before ordinary item use can run.
    callback.cancel();
  }
}
