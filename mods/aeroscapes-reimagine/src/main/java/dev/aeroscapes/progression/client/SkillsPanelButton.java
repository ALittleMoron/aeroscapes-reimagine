package dev.aeroscapes.progression.client;

import com.dannyandson.nutritionalbalance.Config;
import com.dannyandson.nutritionalbalance.gui.NutrientButton;
import com.dannyandson.nutritionalbalance.network.GUITrigger;
import com.dannyandson.nutritionalbalance.network.ModNetworkHandler;
import dev.aeroscapes.inventory.InventoryNavigation;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.puffish.skillsmod.client.SkillsClientMod;
import top.theillusivec4.curios.client.gui.CuriosScreen;

/** A stable inventory rail shared by the vanilla and Curios screens. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SkillsPanelButton extends ImageButton {
  private static boolean shown;
  private final AbstractContainerScreen<?> screen;
  private final ScreenRectangle placement;

  private SkillsPanelButton(
      AbstractContainerScreen<?> screen,
      ScreenRectangle placement,
      String sprite,
      Component title,
      OnPress action) {
    super(
        screen.getGuiLeft() + placement.left(),
        screen.getGuiTop() + placement.top(),
        placement.width(),
        placement.height(),
        new WidgetSprites(
            ResourceLocation.parse(sprite), ResourceLocation.parse(sprite + "_highlighted")),
        action,
        title);
    this.screen = screen;
    this.placement = placement;
    setTooltip(Tooltip.create(title));
  }

  static boolean panelShown() {
    return shown;
  }

  static void hidePanel() {
    shown = false;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void addToInventory(ScreenEvent.Init.Post event) {
    if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
        || !(screen instanceof InventoryScreen || screen instanceof CuriosScreen)) return;
    if (screen instanceof CuriosScreen) shown = false;
    if (ModList.get().isLoaded("nutritionalbalance")) {
      for (var child : List.copyOf(event.getListenersList())) {
        if (child instanceof NutrientButton) event.removeListener(child);
      }
    }
    if (ModList.get().isLoaded("pmmo")) {
      if (screen instanceof InventoryScreen inventory) {
        if (shown) closeRecipeBook(inventory);
        event.addListener(new InventorySkillsPanel(inventory));
      }
      event.addListener(
          new SkillsPanelButton(
              screen,
              InventoryButtonLayout.button(0),
              "aeroscapes_reimagine:skills/button",
              Component.translatable("aeroscapes_reimagine.progression.skills_button"),
              button -> toggle(screen)));
    }
    if (ModList.get().isLoaded("nutritionalbalance") && Config.NUTRIENT_BUTTON_ENABLED.get()) {
      event.addListener(
          new SkillsPanelButton(
              screen,
              InventoryButtonLayout.button(1),
              "aeroscapes_reimagine:skills/nutrients",
              Component.translatable("aeroscapes_reimagine.progression.nutrients_button"),
              button -> {
                if (InventoryNavigation.openInventory())
                  ModNetworkHandler.sendToServer(new GUITrigger());
              }));
    }
    if (ModList.get().isLoaded("puffish_skills")) {
      event.addListener(
          new SkillsPanelButton(
              screen,
              InventoryButtonLayout.button(2),
              "aeroscapes_reimagine:skills/talents",
              Component.translatable("aeroscapes_reimagine.progression.talents_button"),
              button -> {
                if (InventoryNavigation.openInventory())
                  SkillsClientMod.getInstance().openScreen(Optional.empty());
              }));
    }
  }

  @SubscribeEvent
  public static void updatePanel(ScreenEvent.Render.Pre event) {
    for (var child : event.getScreen().children()) {
      if (child instanceof InventorySkillsPanel panel) panel.update();
    }
  }

  @SubscribeEvent
  public static void defocusPanel(ScreenEvent.MouseButtonPressed.Pre event) {
    for (var child : event.getScreen().children()) {
      if (child instanceof InventorySkillsPanel panel
          && !panel.isMouseOver(event.getMouseX(), event.getMouseY())) panel.defocusSearch();
    }
  }

  private static void toggle(AbstractContainerScreen<?> screen) {
    if (screen instanceof CuriosScreen) {
      shown = true;
      if (!InventoryNavigation.openInventory()) shown = false;
      return;
    }
    InventoryScreen inventory = (InventoryScreen) screen;
    if (!shown) closeRecipeBook(inventory);
    shown = !shown;
    for (var child : screen.children()) {
      if (child instanceof InventorySkillsPanel panel) panel.update();
    }
  }

  private static void closeRecipeBook(InventoryScreen inventory) {
    if (inventory.getRecipeBookComponent().isVisible()) {
      for (var child : inventory.children()) {
        if (child instanceof ImageButton button
            && !(button instanceof SkillsPanelButton)
            && button.getX() == inventory.getGuiLeft() + 104
            && button.getY() == inventory.height / 2 - 22) {
          button.onPress();
          break;
        }
      }
    }
  }

  @Override
  public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    setPosition(screen.getGuiLeft() + placement.left(), screen.getGuiTop() + placement.top());
    super.renderWidget(graphics, mouseX, mouseY, partialTick);
  }
}
