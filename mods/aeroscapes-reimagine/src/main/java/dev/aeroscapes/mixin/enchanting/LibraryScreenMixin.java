package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryStatus;
import me.alfie.immersiveenchanting.gui.EnchantingTableScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnchantingTableScreen.class, remap = false)
abstract class LibraryScreenMixin {
  @Unique private LibraryStatus aeroscapes$shownStatus = LibraryStatus.EMPTY;

  @Inject(method = "containerTick", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$refreshLibrary(CallbackInfo callback) {
    var screen = (EnchantingTableScreen) (Object) this;
    var status = ((LibraryStatus.Menu) screen.getMenu()).aeroscapes$libraryStatus();
    if (aeroscapes$shownStatus.equals(status)) return;
    aeroscapes$shownStatus = status;
    screen.tooltipManager().unlockTooltip();
    screen.rebuildBranches();
  }
}
