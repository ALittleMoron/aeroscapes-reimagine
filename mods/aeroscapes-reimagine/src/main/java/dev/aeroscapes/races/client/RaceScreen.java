package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceNetwork;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class RaceScreen extends Screen {
  private static final int GOLD = 0xffe9c084;
  private static final int TEXT = 0xffd6e0eb;
  private static final int MUTED = 0xff92a9c3;
  private final RacePreview preview = new RacePreview();
  private final boolean fixed;
  private final boolean fromInventory;
  private Race race;
  private final int[] styles = new int[Race.values().length];
  private boolean review;
  private int waiting;
  private boolean retry;
  private float rotation = -18;
  private int left, top, panelWidth, panelHeight, previewWidth;

  RaceScreen(@Nullable Race chosen, int style, boolean fromInventory) {
    super(text(chosen == null ? "select" : "title"));
    fixed = chosen != null;
    this.fromInventory = fromInventory;
    race = fixed ? chosen : Race.HUMAN;
    if (race.validStyle(style)) styles[race.ordinal()] = style;
  }

  private int style() {
    return styles[race.ordinal()];
  }

  private Component name() {
    return text(race.id() + ".name");
  }

  private Component styleName() {
    return text(race.id() + ".style." + style());
  }

  private Component details() {
    return review
        ? text(retry ? "retry" : waiting > 0 ? "saving" : "permanent", name(), styleName())
        : text(race.id() + ".description");
  }

  @Override
  public Component getNarrationMessage() {
    return title
        .copy()
        .append(". ")
        .append(name())
        .append(". ")
        .append(styleName())
        .append(". ")
        .append(details());
  }

  @Override
  protected void init() {
    panelWidth = Math.min(394, width - 8);
    panelHeight = Math.min(250, height - 8);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    previewWidth = Math.min(132, panelWidth / 3);
    int x = left + 10, y = top + 27;
    int right = x + previewWidth + 10;
    int rightWidth = left + panelWidth - 10 - right;
    if (!fixed) {
      var exit =
          minecraft.isLocalServer()
              ? Component.translatable("menu.returnToMenu")
              : CommonComponents.GUI_DISCONNECT;
      int exitWidth = font.width(exit) + 12;
      button(exit, left + panelWidth - 10 - exitWidth, top + 7, exitWidth, 17, this::leaveWorld);
    }
    if (!review && !fixed) {
      int last = x;
      for (var choice : Race.values()) {
        int end = x + (choice.ordinal() + 1) * (panelWidth - 20) / Race.values().length;
        var tab =
            button(
                text(choice.id() + ".name"),
                last,
                y,
                end - last - 2,
                19,
                () -> {
                  race = choice;
                  rebuildWidgets();
                });
        tab.active = choice != race;
        last = end;
      }
      addRenderableWidget(
          CycleButton.<Integer>builder(index -> text(race.id() + ".style." + index))
              .withValues(IntStream.range(0, race.styleCount()).boxed().toList())
              .withInitialValue(style())
              .withTooltip(index -> Tooltip.create(text("next_style")))
              .displayOnlyValue()
              .create(
                  right,
                  top + 66,
                  rightWidth,
                  20,
                  text("appearance"),
                  (button, index) -> styles[race.ordinal()] = index));
    }
    button(
        text("rotate"),
        x,
        top + panelHeight - 53,
        previewWidth,
        18,
        () -> rotation = (rotation + 45) % 360);
    int footer = top + panelHeight - 26;
    int half = (panelWidth - 24) / 2;
    if (fixed) {
      button(CommonComponents.GUI_DONE, x, footer, panelWidth - 20, 18, this::onClose);
    } else if (review) {
      button(text("confirm"), x, footer, half, 18, this::submit).active = waiting == 0;
      button(
                  CommonComponents.GUI_BACK,
                  x + half + 4,
                  footer,
                  half,
                  18,
                  () -> {
                    review = false;
                    retry = false;
                    rebuildWidgets();
                  })
              .active =
          waiting == 0;
    } else {
      button(
          text("review"),
          x + half + 4,
          footer,
          half,
          18,
          () -> {
            review = true;
            rebuildWidgets();
          });
    }
  }

  private Button button(Component label, int x, int y, int width, int height, Runnable action) {
    return addRenderableWidget(
        Button.builder(label, button -> action.run()).bounds(x, y, width, height).build());
  }

  private void submit() {
    if (waiting != 0 || fixed || !review) return;
    waiting = 100;
    retry = false;
    rebuildWidgets();
    RaceNetwork.choose(race, style());
  }

  void declined() {
    if (waiting == 0) return;
    waiting = 0;
    retry = true;
    rebuildWidgets();
  }

  @Override
  public void tick() {
    if (minecraft == null || minecraft.player == null) return;
    if (!fixed && RaceClient.race(minecraft.player.getUUID()) != null) {
      closeSheet();
    } else if (waiting == 1) {
      declined();
    } else if (waiting > 1) {
      waiting--;
    }
  }

  @Override
  public void onClose() {
    if (review && waiting == 0) {
      review = false;
      retry = false;
      rebuildWidgets();
      return;
    }
    if (fixed) closeSheet();
  }

  private void leaveWorld() {
    if (minecraft == null || minecraft.level == null) return;
    boolean local = minecraft.isLocalServer();
    var server = minecraft.getCurrentServer();
    minecraft.level.disconnect();
    if (local)
      minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
    else minecraft.disconnect();
    var titleScreen = new TitleScreen();
    if (local) minecraft.setScreen(titleScreen);
    else if (server != null && server.isRealm())
      minecraft.setScreen(new RealmsMainScreen(titleScreen));
    else minecraft.setScreen(new JoinMultiplayerScreen(titleScreen));
  }

  private void closeSheet() {
    if (minecraft == null) return;
    minecraft.setScreen(
        fromInventory && minecraft.player != null && minecraft.player.isAlive()
            ? new InventoryScreen(minecraft.player)
            : null);
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.renderBackground(graphics, mouseX, mouseY, partialTick);
    int x = left + 10;
    int right = x + previewWidth + 10;
    int bottom = top + panelHeight;
    int rightWidth = left + panelWidth - 10 - right;
    graphics.fill(left, top, left + panelWidth, bottom, 0xff111b29);
    graphics.renderOutline(left + 1, top + 1, panelWidth - 2, panelHeight - 2, 0xff3d536d);
    graphics.fill(left + 3, top + 3, left + panelWidth - 3, bottom - 3, 0xff1b2b3d);
    graphics.hLine(left + 7, left + 29, top + 4, 0xffb99a6c);
    graphics.hLine(left + panelWidth - 29, left + panelWidth - 7, bottom - 5, 0xffb99a6c);
    graphics.drawString(font, review ? text("review") : title, x, top + 12, GOLD, false);
    if (fixed || review) graphics.drawString(font, name(), x, top + 32, TEXT, false);
    int previewY = top + 51, previewBottom = bottom - 57;
    graphics.fillGradient(x, previewY, x + previewWidth, previewBottom, 0xff162333, 0xff263c52);
    graphics.renderOutline(x, previewY, previewWidth, previewBottom - previewY, 0xff3d536d);
    int floor = previewBottom - 7;
    graphics.hLine(x + 5, x + previewWidth - 6, floor, 0xff718399);
    if (minecraft != null && minecraft.player != null) {
      graphics.enableScissor(x + 1, previewY + 1, x + previewWidth - 1, previewBottom - 1);
      preview.draw(
          graphics,
          minecraft.player,
          race,
          style(),
          rotation,
          x + previewWidth / 2,
          floor,
          floor - previewY - 4);
      graphics.disableScissor();
    }
    graphics.drawString(font, text("appearance"), right, top + 53, MUTED, false);
    if (review || fixed) graphics.drawString(font, styleName(), right, top + 69, TEXT, false);
    graphics.drawString(
        font, text(review ? "final_choice" : "traits"), right, top + 96, GOLD, false);
    graphics.drawWordWrap(font, details(), right, top + 110, rightWidth, TEXT);
  }

  @Override
  public boolean isPauseScreen() {
    return !fixed;
  }
}
