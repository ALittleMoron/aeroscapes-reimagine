package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryEnchanting;
import dev.aeroscapes.enchanting.LibraryStatus;
import me.alfie.immersiveenchanting.api.description.internal.lines.UnavailableEnchantmentLine;
import me.alfie.immersiveenchanting.api.node.internal.EnchantmentNodeData;
import me.alfie.immersiveenchanting.gui.tab.enchanting.tooltip.NodeTooltip;
import me.alfie.immersiveenchanting.util.EnchantmentUtil;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UnavailableEnchantmentLine.class, remap = false)
abstract class LibraryTooltipMixin {
  @Shadow
  public abstract NodeTooltip tooltip();

  @Inject(method = "getText", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$explainUnavailable(CallbackInfoReturnable<Component> callback) {
    var tip = tooltip();
    if (!(tip.node().data().value() instanceof EnchantmentNodeData data)) return;
    var status = ((LibraryStatus.Menu) tip.screen().getMenu()).aeroscapes$libraryStatus();
    var effect = EnchantmentUtil.toHolder(data.enchantmentId(), tip.screen().registryAccess());
    var reason =
        LibraryEnchanting.restriction(status.ceilings(), status.mastery(), effect, data.level());
    if (reason != null) callback.setReturnValue(reason);
  }
}
