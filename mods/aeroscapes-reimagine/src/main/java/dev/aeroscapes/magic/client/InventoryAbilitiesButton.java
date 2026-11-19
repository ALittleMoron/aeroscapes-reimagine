package dev.aeroscapes.magic.client;

import dev.aeroscapes.inventory.InventoryNavigation;
import dev.aeroscapes.progression.client.InventoryButtonLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import top.theillusivec4.curios.client.gui.CuriosScreen;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class InventoryAbilitiesButton extends Button {
  private final AbstractContainerScreen<?> screen;

  private InventoryAbilitiesButton(AbstractContainerScreen<?> screen) {
    super(
        0,
        0,
        InventoryButtonLayout.button(3).width(),
        InventoryButtonLayout.button(3).height(),
        AbilityClient.text("title"),
        button -> {
          if (InventoryNavigation.openInventory())
            Minecraft.getInstance().setScreen(new AbilitiesScreen());
        },
        DEFAULT_NARRATION);
    this.screen = screen;
    updatePosition();
    setTooltip(
        Tooltip.create(
            AbilityClient.text("title")
                .copy()
                .append("\n")
                .append(AbilityClient.text("controls"))));
  }

  private void updatePosition() {
    var placement = InventoryButtonLayout.button(3);
    setPosition(screen.getGuiLeft() + placement.left(), screen.getGuiTop() + placement.top());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void initialize(ScreenEvent.Init.Post event) {
    if (ModList.get().isLoaded("irons_spellbooks")
        && (event.getScreen() instanceof InventoryScreen
            || event.getScreen() instanceof CuriosScreen))
      event.addListener(
          new InventoryAbilitiesButton((AbstractContainerScreen<?>) event.getScreen()));
  }

  @Override
  public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    updatePosition();
    int x = getX();
    int y = getY();
    graphics.fill(x, y, x + getWidth(), y + getHeight(), 0xff111b29);
    graphics.fill(
        x + 1,
        y + 1,
        x + getWidth() - 1,
        y + getHeight() - 1,
        isHoveredOrFocused() ? 0xffb99a6c : 0xff3d536d);
    graphics.fill(x + 2, y + 2, x + getWidth() - 2, y + getHeight() - 2, 0xff1b2b3d);
    int centerX = x + getWidth() / 2;
    int centerY = y + getHeight() / 2;
    graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, AbilityWidgets.MUTED);
    graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, AbilityWidgets.GOLD);
    graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, AbilityWidgets.GOLD);
    graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xff1b2b3d);
  }
}
