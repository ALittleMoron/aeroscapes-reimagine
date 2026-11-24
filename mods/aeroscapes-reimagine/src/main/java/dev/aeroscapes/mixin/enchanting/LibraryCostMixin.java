package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryEnchanting;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import me.alfie.immersiveenchanting.util.CostHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CostHelper.class, remap = false)
abstract class LibraryCostMixin {
  @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$applyCost(
      EnchantingTableMenu menu,
      Holder<Enchantment> effect,
      int level,
      Player player,
      CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(LibraryEnchanting.canApply(menu, effect, level, player));
  }

  @Inject(method = "canReplicate", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$copyCost(
      EnchantingTableMenu menu, Player player, CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(LibraryEnchanting.canCopy(menu, player));
  }
}
