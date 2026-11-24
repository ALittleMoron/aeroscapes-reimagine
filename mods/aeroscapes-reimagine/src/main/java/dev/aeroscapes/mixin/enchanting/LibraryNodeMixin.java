package dev.aeroscapes.mixin.enchanting;

import me.alfie.immersiveenchanting.api.node.internal.EnchantmentNodeData;
import me.alfie.immersiveenchanting.gui.tab.enchanting.node.Node;
import me.alfie.immersiveenchanting.gui.tab.enchanting.node.NodeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Node.class, remap = false)
abstract class LibraryNodeMixin {
  @Inject(method = "click", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$blockLockedClick(CallbackInfo callback) {
    var node = (Node) (Object) this;
    if (node.data().value() instanceof EnchantmentNodeData && !node.isState(NodeState.UNOBTAINED))
      callback.cancel();
  }
}
