package dev.aeroscapes.atmosphere;

import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.mixin.accessor.HandledScreenAccessor;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import net.createmod.ponder.enums.PonderKeybinds;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Resolves native tutorial targets without depending on the previous render's tick. */
public final class InventoryPonderFocus {
  private InventoryPonderFocus() {}

  public static boolean holdsTutorial(Screen screen) {
    if (!PonderTooltipHandler.enable || !PonderKeybinds.PONDER.isDown()) return false;
    if (screen instanceof AbstractContainerScreen<?> container
        && !container.getMenu().getCarried().isEmpty()) return false;
    Minecraft minecraft = Minecraft.getInstance();
    var window = minecraft.getWindow();
    int mouseX =
        (int)
            (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
    int mouseY =
        (int)
            (minecraft.mouseHandler.ypos()
                * window.getGuiScaledHeight()
                / window.getScreenHeight());
    if (screen instanceof HandledScreenAccessor slots) {
      var slot = slots.invokeGetSlotAt(mouseX, mouseY);
      if (slot != null && ponderable(slot.getItem())) return true;
    }
    if (screen instanceof AbstractStorageTerminalScreen<?> terminal
        && terminalTarget(terminal, mouseX, mouseY)) return true;
    if (EmiScreenBase.getCurrent().isEmpty()
        || EmiScreenManager.isDisabled()
        || !EmiScreenManager.draggedStack.isEmpty()) return false;
    EmiScreenManager.recalculate();
    var hovered = EmiScreenManager.getHoveredStack(mouseX, mouseY, false, true);
    return hovered instanceof EmiScreenManager.SidebarEmiStackInteraction
        && ponderable(hovered.getStack());
  }

  private static boolean terminalTarget(
      AbstractStorageTerminalScreen<?> terminal, int mouseX, int mouseY) {
    for (var slot : terminal.getMenu().storageSlotList) {
      int x = terminal.getGuiLeft() + slot.xDisplayPosition;
      int y = terminal.getGuiTop() + slot.yDisplayPosition;
      if (slot.stack != null
          && mouseX >= x - 1
          && mouseX < x + 17
          && mouseY >= y - 1
          && mouseY < y + 17) return ponderable(slot.stack.getStack());
    }
    return false;
  }

  private static boolean ponderable(EmiIngredient ingredient) {
    if (ingredient instanceof EmiFavorite favorite) return ponderable(favorite.getStack());
    if (ingredient instanceof ListEmiIngredient list) {
      var choices = list.getIngredients();
      if (choices.isEmpty()) return false;
      int current = (int) (System.currentTimeMillis() / 1000 % choices.size());
      return ponderable(choices.get(current));
    }
    return ingredient instanceof EmiStack stack && ponderable(stack.getItemStack());
  }

  private static boolean ponderable(ItemStack stack) {
    return !stack.isEmpty()
        && PonderIndex.getSceneAccess()
            .doScenesExistForId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }
}
