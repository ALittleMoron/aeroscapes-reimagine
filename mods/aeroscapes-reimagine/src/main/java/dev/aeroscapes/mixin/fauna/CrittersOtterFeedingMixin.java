package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
    targets = "io.github.bonsaistudi0s.crittersandcompanions.common.entity.OtterEntity",
    remap = false)
public abstract class CrittersOtterFeedingMixin {
  @Redirect(
      method = "mobInteract",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The provider's split+shrink pair otherwise charges twice.
  private ItemStack aeroscapes$copyHeldFood(ItemStack stack, int count) {
    return stack.copyWithCount(count);
  }

  @Redirect(
      method = "mobInteract",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // One creative-aware feeding cost; native superclass breeding
  // remains separate.
  private void aeroscapes$consumeFoodOnce(
      ItemStack stack, int count, @Local(argsOnly = true) Player player) {
    stack.consume(count, player);
  }
}
