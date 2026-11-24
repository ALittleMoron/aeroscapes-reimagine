package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class WorkshopResultClickMixin {
  @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$validateResultClick(
      int slotIndex, int button, ClickType type, Player player, CallbackInfo callback) {
    var menu = (AbstractContainerMenu) (Object) this;
    if (!(player instanceof ServerPlayer) || slotIndex < 0 || slotIndex >= menu.slots.size())
      return;
    var resultSlot = menu.getSlot(slotIndex);
    if (!(resultSlot instanceof ResultSlot)
        && !(menu instanceof SmithingMenu && slotIndex == SmithingMenu.RESULT_SLOT)) return;
    var result = resultSlot.getItem();
    if (WorkshopManufacture.allowed(player, result, false)) return;
    if (menu instanceof SmithingMenu
        && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().is(result.getItem())) return;
    if (!(menu instanceof SmithingMenu)
        && ((CraftingResultInputAccess) resultSlot)
            .aeroscapes$craftInput().getItems().stream()
                .anyMatch(input -> input.is(result.getItem()))) return;
    WorkshopManufacture.explain(player);
    callback.cancel();
  }
}
