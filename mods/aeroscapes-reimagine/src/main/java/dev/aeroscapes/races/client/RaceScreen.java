package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceNetwork;
import java.util.EnumMap;
import java.util.stream.IntStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class RaceScreen extends Screen {
  private enum Page {
    RACE,
    CLASS,
    SUMMARY
  }

  private static final int GOLD = 0xffe9c084;
  private static final int TEXT = 0xffd6e0eb;
  private final RacePreview preview = new RacePreview();
  private final boolean raceLocked;
  private final boolean sheet;
  private final boolean grantPending;
  private final boolean fromInventory;
  private Race race;
  private CharacterClass characterClass;
  private final int[] styles = new int[Race.values().length];
  private final EnumMap<CharacterClass, String> starters = new EnumMap<>(CharacterClass.class);
  private Page page;
  private boolean expanded;
  private int waiting;
  private boolean retry;
  private float rotation = -18;
  private int left, top, panelWidth, panelHeight, previewWidth;

  RaceScreen(RaceClient.Profile profile, boolean fromInventory) {
    super(text(profile.complete() ? "title" : "create"));
    this.fromInventory = fromInventory;
    raceLocked = profile.race() != null;
    sheet = profile.complete();
    grantPending = profile.grantPending();
    race = raceLocked ? profile.race() : Race.HUMAN;
    if (race.validStyle(profile.style())) styles[race.ordinal()] = profile.style();
    characterClass = profile.hasSelection() ? profile.characterClass() : CharacterClass.WARRIOR;
    if (profile.hasSelection()) starters.put(characterClass, profile.starter());
    page = profile.hasSelection() ? Page.SUMMARY : raceLocked ? Page.CLASS : Page.RACE;
  }

  private int style() {
    return styles[race.ordinal()];
  }

  private String starter() {
    return starters.getOrDefault(characterClass, characterClass.starters().getFirst());
  }

  private Component heading() {
    if (sheet) return title;
    return switch (page) {
      case RACE -> text("race_page");
      case CLASS -> text("class_page");
      case SUMMARY -> text("review");
    };
  }

  private Component details() {
    var doc = Component.empty();
    if (grantPending) doc.append(text("grant_pending")).append("\n\n");
    else if (retry) doc.append(text("retry")).append("\n\n");
    if (page == Page.SUMMARY) {
      doc.append(
              text("sheet", text(race.id() + ".name"), CharacterDetails.className(characterClass)))
          .append("\n")
          .append(text("starter", CharacterDetails.talent(starter(), "title")))
          .append("\n\n");
    }
    if (page == Page.RACE && raceLocked) doc.append(text("race_saved")).append("\n\n");
    if (page != Page.CLASS) {
      doc.append(CharacterDetails.race(race, style(), expanded)).append("\n\n");
    }
    if (page != Page.RACE) {
      if (page == Page.CLASS)
        doc.append(text("selected_race", text(race.id() + ".name"))).append("\n\n");
      doc.append(CharacterDetails.classProfile(characterClass, starter(), expanded)).append("\n\n");
    }
    doc.append(
        CharacterDetails.practice(race, page == Page.RACE ? null : characterClass, expanded));
    if (page == Page.SUMMARY && !sheet && !grantPending)
      doc.append("\n\n").append(text("permanent"));
    return doc;
  }

  @Override
  public Component getNarrationMessage() {
    return heading().copy().append("\n").append(details());
  }

  @Override
  protected void init() {
    panelWidth = Math.min(520, width - 8);
    panelHeight = Math.min(310, height - 8);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    previewWidth = Math.min(134, panelWidth / 4);
    int x = left + 10;
    int right = x + previewWidth + 10;
    int rightWidth = left + panelWidth - 10 - right;
    int bottom = top + panelHeight;
    if (!sheet) {
      var exit =
          minecraft.isLocalServer()
              ? Component.translatable("menu.returnToMenu")
              : CommonComponents.GUI_DISCONNECT;
      int exitWidth = font.width(exit) + 12;
      button(exit, left + panelWidth - 10 - exitWidth, top + 7, exitWidth, 17, this::leaveWorld);
    }
    int contentY =
        switch (page) {
          case RACE -> raceControls(right, rightWidth);
          case CLASS -> classControls(right, rightWidth);
          case SUMMARY -> top + 32;
        };
    addRenderableWidget(
        new FittingMultiLineTextWidget(
                right, contentY, rightWidth - 9, bottom - 34 - contentY, details(), font)
            .setColor(TEXT));
    button(
        text("rotate"), x, bottom - 72, previewWidth, 18, () -> rotation = (rotation + 45) % 360);
    button(
        text(expanded ? "brief" : "details"),
        x,
        bottom - 51,
        previewWidth,
        18,
        () -> {
          expanded = !expanded;
          rebuildWidgets();
        });
    int footer = bottom - 26;
    int half = (panelWidth - 24) / 2;
    if (sheet) {
      button(CommonComponents.GUI_DONE, x, footer, panelWidth - 20, 18, this::closeSheet);
      return;
    }
    if (page != Page.RACE && !grantPending)
      button(CommonComponents.GUI_BACK, x, footer, half, 18, this::onClose).active = waiting == 0;
    var forward =
        switch (page) {
          case RACE -> text("to_class");
          case CLASS -> text("review");
          case SUMMARY ->
              text(waiting > 0 ? "saving" : grantPending || retry ? "try_again" : "confirm");
        };
    button(forward, x + half + 4, footer, half, 18, this::advance).active = waiting == 0;
  }

  private int raceControls(int right, int rightWidth) {
    if (raceLocked) return top + 32;
    for (var choice : Race.values()) {
      tab(
          text(choice.id() + ".name"),
          choice.ordinal(),
          Race.values().length,
          choice == race,
          () -> {
            race = choice;
            rebuildWidgets();
          });
    }
    addRenderableWidget(
        CycleButton.<Integer>builder(index -> text(race.id() + ".style." + index))
            .withValues(IntStream.range(0, race.styleCount()).boxed().toList())
            .withInitialValue(style())
            .withTooltip(index -> Tooltip.create(text("next_style")))
            .displayOnlyValue()
            .create(
                right,
                top + 62,
                rightWidth,
                18,
                text("appearance"),
                (button, index) -> {
                  styles[race.ordinal()] = index;
                  rebuildWidgets();
                }));
    return top + 86;
  }

  private int classControls(int right, int rightWidth) {
    for (var choice : CharacterClass.values()) {
      tab(
          CharacterDetails.className(choice),
          choice.ordinal(),
          CharacterClass.values().length,
          choice == characterClass,
          () -> {
            characterClass = choice;
            rebuildWidgets();
          });
    }
    if (characterClass.starters().size() == 1) return top + 53;
    int choices = characterClass.starters().size();
    for (int i = 0; i < choices; i++) {
      String id = characterClass.starters().get(i);
      int start = rightWidth * i / choices;
      int end = rightWidth * (i + 1) / choices;
      var option =
          button(
              CharacterDetails.talent(id, "title"),
              right + start,
              top + 62,
              end - start - 2,
              18,
              () -> {
                starters.put(characterClass, id);
                rebuildWidgets();
              });
      option.active = !starter().equals(id);
      option.setTooltip(Tooltip.create(CharacterDetails.talent(id, "description")));
    }
    return top + 86;
  }

  private void tab(Component label, int index, int count, boolean selected, Runnable action) {
    int start = (panelWidth - 20) * index / count;
    int end = (panelWidth - 20) * (index + 1) / count;
    button(label, left + 10 + start, top + 28, end - start - 2, 18, action).active = !selected;
  }

  private Button button(Component label, int x, int y, int width, int height, Runnable action) {
    return addRenderableWidget(
        Button.builder(label, button -> action.run()).bounds(x, y, width, height).build());
  }

  private void advance() {
    if (sheet || waiting > 0) return;
    switch (page) {
      case RACE -> page = Page.CLASS;
      case CLASS -> page = Page.SUMMARY;
      case SUMMARY -> {
        waiting = 100;
        retry = false;
        RaceNetwork.choose(race, style(), characterClass, starter());
      }
    }
    rebuildWidgets();
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
    var confirmed = RaceClient.profile(minecraft.player.getUUID());
    if (!sheet && confirmed != null && confirmed.complete()) closeSheet();
    else if (waiting == 1) declined();
    else if (waiting > 1) waiting--;
  }

  @Override
  public void onClose() {
    if (sheet) {
      closeSheet();
    } else if (!grantPending && waiting == 0 && page != Page.RACE) {
      page = page == Page.SUMMARY ? Page.CLASS : Page.RACE;
      retry = false;
      rebuildWidgets();
    }
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
    int bottom = top + panelHeight;
    graphics.fill(left, top, left + panelWidth, bottom, 0xff111b29);
    graphics.renderOutline(left + 1, top + 1, panelWidth - 2, panelHeight - 2, 0xff3d536d);
    graphics.fill(left + 3, top + 3, left + panelWidth - 3, bottom - 3, 0xff1b2b3d);
    graphics.hLine(left + 7, left + 29, top + 4, 0xffb99a6c);
    graphics.hLine(left + panelWidth - 29, left + panelWidth - 7, bottom - 5, 0xffb99a6c);
    graphics.drawString(font, heading(), x, top + 12, GOLD, false);
    boolean tabsVisible = page == Page.CLASS || page == Page.RACE && !raceLocked;
    int previewY = top + (tabsVisible ? 51 : 30), previewBottom = bottom - 76;
    graphics.fillGradient(x, previewY, x + previewWidth, previewBottom, 0xff162333, 0xff263c52);
    graphics.renderOutline(x, previewY, previewWidth, previewBottom - previewY, 0xff3d536d);
    int floor = previewBottom - 6;
    graphics.hLine(x + 5, x + previewWidth - 6, floor, 0xff718399);
    if (minecraft != null && minecraft.player != null && floor > previewY + 4) {
      graphics.enableScissor(x + 1, previewY + 1, x + previewWidth - 1, previewBottom - 1);
      preview.draw(
          graphics,
          minecraft.player,
          race,
          style(),
          rotation,
          x + previewWidth / 2,
          floor,
          Math.min(floor - previewY - 4, (previewWidth - 8) * 2));
      graphics.disableScissor();
    }
    if (page == Page.RACE && !raceLocked
        || page == Page.CLASS && characterClass.starters().size() > 1) {
      graphics.drawString(
          font,
          text(page == Page.RACE ? "appearance" : "ability"),
          x + previewWidth + 10,
          top + 51,
          GOLD,
          false);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return !sheet;
  }
}
