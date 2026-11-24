package dev.aeroscapes.mixin.enchanting;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.enchanting.LibraryEnchanting;
import dev.aeroscapes.enchanting.LibraryStatus;
import me.alfie.immersiveenchanting.api.node.BuildBranchesEvent;
import me.alfie.immersiveenchanting.gui.tab.enchanting.node.BranchFactory;
import me.alfie.immersiveenchanting.gui.tab.enchanting.node.NodeState;
import me.alfie.immersiveenchanting.util.EnchantmentUtil;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BranchFactory.class, remap = false)
abstract class LibraryBranchesMixin {
  @Inject(method = "buildEnchantingBranches", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$skipOrdinaryBooks(
      BuildBranchesEvent event, CallbackInfo callback) {
    var stack = event.getStack();
    if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) callback.cancel();
  }

  @ModifyArg(
      method = "buildEnchantingBranch",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lme/alfie/immersiveenchanting/api/node/NodeTemplate;<init>(Lnet/minecraft/network/chat/Component;ILme/alfie/immersiveenchanting/gui/tab/enchanting/node/NodeState;Lme/alfie/immersiveenchanting/gui/tab/enchanting/node/NodeTier;Lme/alfie/immersiveenchanting/api/node/NodeIcon;Lme/alfie/immersiveenchanting/api/node/NodeData;)V"),
      index = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static NodeState aeroscapes$levelState(
      NodeState original,
      @Local(argsOnly = true) BuildBranchesEvent event,
      @Local(argsOnly = true) Holder<Enchantment> effect,
      @Local(ordinal = 1) int index) {
    int level = index + 1;
    if (EnchantmentUtil.getEnchantmentLevel(event.getStack(), effect) >= level)
      return NodeState.OBTAINED;
    var screen = event.getCanvas().screen();
    if (screen.player().hasInfiniteMaterials()) return NodeState.UNOBTAINED;
    var status = ((LibraryStatus.Menu) screen.getMenu()).aeroscapes$libraryStatus();
    return LibraryEnchanting.restriction(status.ceilings(), status.mastery(), effect, level) == null
        ? NodeState.UNOBTAINED
        : NodeState.LOCKED;
  }

  @Inject(method = "buildTransmuteBranch", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$hideTransmutation(
      BuildBranchesEvent event, CallbackInfo callback) {
    callback.cancel();
  }
}
