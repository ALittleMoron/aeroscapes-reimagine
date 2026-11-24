package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryBooks;
import dev.aeroscapes.enchanting.LibraryStatus;
import me.alfie.immersiveenchanting.gui.EnchantingTableScreen;
import me.alfie.immersiveenchanting.gui.tab.enchanting.tooltip.CostRenderer;
import me.alfie.immersiveenchanting.gui.tab.enchanting.tooltip.RenderedCost;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CostRenderer.class, remap = false)
abstract class LibraryCostRendererMixin {
  @Inject(method = "getCurrentRenderedCost", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$pricedForPlayer(CallbackInfoReturnable<RenderedCost> callback) {
    RenderedCost price = callback.getReturnValue();
    if (price == null
        || price.xpLevels() <= 0
        || !(Minecraft.getInstance().screen instanceof EnchantingTableScreen screen)
        || LibraryBooks.isAncient(screen.getMenu().getToolSlot().getItem())) return;
    var status = ((LibraryStatus.Menu) screen.getMenu()).aeroscapes$libraryStatus();
    int levels =
        screen.player().hasInfiniteMaterials()
            ? 0
            : Math.max(1, price.xpLevels() - status.savings());
    callback.setReturnValue(new RenderedCost(price.stack(), levels));
  }
}
