package dev.aeroscapes.mixin.engineering;

import com.jesz.createdieselgenerators.content.entity_filter.EntityFilterMenu;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityFilterMenu.class, remap = false)
public abstract class DieselFilterMenuMixin {
  @Redirect(
      method = "init",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private MutableComponent selectedTags(String literal) {
    return Component.translatable("createdieselgenerators.gui.entity_filter.selected_tags");
  }

  @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private void clearGhostSample(
      Player player, int index, CallbackInfoReturnable<ItemStack> result) {
    if (index == 36) {
      ((AbstractFilterMenu) (Object) this).getSlot(index).set(ItemStack.EMPTY);
      result.setReturnValue(ItemStack.EMPTY);
    }
  }
}
