package dev.aeroscapes.races.client;

import dev.aeroscapes.races.Race;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import top.theillusivec4.curios.client.gui.CuriosScreen;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class InventoryRaceButton extends ImageButton {
  private static final WidgetSprites SPRITES =
      new WidgetSprites(
          ResourceLocation.parse("aeroscapes_race_visuals:race_card"),
          ResourceLocation.parse("aeroscapes_race_visuals:race_card_highlighted"));
  private final AbstractContainerScreen<?> screen;
  private Race shownRace;

  private InventoryRaceButton(AbstractContainerScreen<?> screen) {
    super(10, 10, SPRITES, button -> RaceVisuals.openFromInventory(), RaceVisuals.text("title"));
    this.screen = screen;
    setTooltip(Tooltip.create(RaceVisuals.text("sheet_hint")));
    update();
  }

  private void update() {
    setPosition(screen.getGuiLeft() + 63, screen.getGuiTop() + 10);
    var player = Minecraft.getInstance().player;
    var race = player == null ? null : RaceClient.race(player.getUUID());
    active = race != null;
    if (race != null && race != shownRace) {
      var label = RaceVisuals.text("sheet", RaceVisuals.text(race.id() + ".name"));
      setMessage(label);
      setTooltip(Tooltip.create(label.copy().append("\n").append(RaceVisuals.text("sheet_hint"))));
    }
    shownRace = race;
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void initialize(ScreenEvent.Init.Post event) {
    if (ModList.get().isLoaded("neoorigins")
        && ModList.get().isLoaded("pehkui")
        && (event.getScreen() instanceof InventoryScreen
            || ModList.get().isLoaded("curios") && event.getScreen() instanceof CuriosScreen)) {
      event.addListener(new InventoryRaceButton((AbstractContainerScreen<?>) event.getScreen()));
    }
  }

  @Override
  public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    update();
    super.renderWidget(graphics, mouseX, mouseY, partialTick);
  }
}
