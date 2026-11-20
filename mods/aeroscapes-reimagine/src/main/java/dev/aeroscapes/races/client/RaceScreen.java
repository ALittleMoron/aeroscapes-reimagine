package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceNetwork;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
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
  enum Page {
    RACE,
    CLASS,
    PROFESSION,
    SUMMARY;

    static List<Page> remaining(RaceClient.Profile profile) {
      var pages = EnumSet.allOf(Page.class);
      if (profile.race() != null) pages.remove(RACE);
      if (profile.hasClass()) pages.remove(CLASS);
      if (profile.hasProfession()) pages.remove(PROFESSION);
      return List.copyOf(pages);
    }
  }

  private static final int GOLD = 0xffe9c084;
  private static final int TEXT = 0xffd6e0eb;
  private final RacePreview preview = new RacePreview();
  private final List<Page> pages;
  private final boolean sheet;
  private final boolean grantPending;
  private final boolean fromInventory;
  private Race race;
  private CharacterClass characterClass;
  private Profession profession;
  private final int[] styles = new int[Race.values().length];
  private final EnumMap<CharacterClass, String> starters = new EnumMap<>(CharacterClass.class);
  private final EnumMap<Profession, String> professionStarters = new EnumMap<>(Profession.class);
  private Page page;
  private boolean expanded;
  private int waiting;
  private boolean retry;
  private float rotation = -18;
  private int left, top, panelWidth, panelHeight, previewWidth;

  RaceScreen(RaceClient.Profile profile, boolean fromInventory) {
    super(text(profile.complete() ? "title" : "create"));
    this.fromInventory = fromInventory;
    pages = Page.remaining(profile);
    page = pages.getFirst();
    sheet = profile.complete();
    grantPending = profile.grantPending() && page == Page.SUMMARY;
    race = profile.race() != null ? profile.race() : Race.HUMAN;
    if (race.validStyle(profile.style())) styles[race.ordinal()] = profile.style();
    characterClass = profile.hasClass() ? profile.characterClass() : CharacterClass.WARRIOR;
    if (profile.hasClass()) starters.put(characterClass, profile.starter());
    profession = profile.hasProfession() ? profile.profession() : Profession.BLACKSMITH;
    if (profile.hasProfession()) professionStarters.put(profession, profile.professionStarter());
  }

  private int style() {
    return styles[race.ordinal()];
  }

  private String starter() {
    return starters.getOrDefault(characterClass, characterClass.starters().getFirst());
  }

  private String professionStarter() {
    return professionStarters.getOrDefault(profession, profession.starters().getFirst());
  }

  private Component heading() {
    if (sheet) return title;
    return switch (page) {
      case RACE -> text("race_page");
      case CLASS -> text("class_page");
      case PROFESSION -> text("profession_page");
      case SUMMARY -> text("review");
    };
  }

  private Component details() {
    var doc = Component.empty();
    var language = minecraft.getLanguageManager().getSelected();
    var numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.replace('_', '-')));
    if (grantPending) doc.append(text("grant_pending")).append("\n\n");
    else if (retry) doc.append(text("retry")).append("\n\n");
    doc.append(CharacterDetails.identity(race, characterClass, profession))
        .append("\n")
        .append(text("starter", CharacterDetails.talent(starter(), "title")))
        .append("\n")
        .append(
            text(
                "profession_starter",
                CharacterDetails.professionTalent(professionStarter(), "title")))
        .append("\n\n")
        .append(
            CharacterDetails.attributes(
                race, characterClass, professionStarter(), expanded, numbers))
        .append("\n\n")
        .append(CharacterDetails.practice(race, characterClass, profession, expanded));
    if (expanded || page == Page.PROFESSION || page == Page.SUMMARY)
      doc.append("\n\n")
          .append(CharacterDetails.professionProfile(profession, professionStarter(), expanded));
    if (expanded || page == Page.CLASS || page == Page.SUMMARY)
      doc.append("\n\n").append(CharacterDetails.classProfile(characterClass, starter(), expanded));
    if (expanded || page == Page.RACE || page == Page.SUMMARY)
      doc.append("\n\n").append(CharacterDetails.race(race, style(), expanded));
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
          case PROFESSION -> professionControls(right, rightWidth);
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
    if (page != pages.getFirst())
      button(CommonComponents.GUI_BACK, x, footer, half, 18, this::onClose).active = waiting == 0;
    var forward =
        switch (page) {
          case RACE -> text("to_class");
          case CLASS -> text("to_profession");
          case PROFESSION -> text("review");
          case SUMMARY ->
              text(waiting > 0 ? "saving" : grantPending || retry ? "try_again" : "confirm");
        };
    button(forward, x + half + 4, footer, half, 18, this::advance).active = waiting == 0;
  }

  private int raceControls(int right, int rightWidth) {
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
    return starterControl(
        right,
        rightWidth,
        characterClass.starters(),
        starter(),
        id -> CharacterDetails.talent(id, "title"),
        id -> CharacterDetails.talent(id, "description"),
        id -> starters.put(characterClass, id));
  }

  private int professionControls(int right, int rightWidth) {
    for (var choice : Profession.values()) {
      tab(
          CharacterDetails.professionName(choice),
          choice.ordinal(),
          Profession.values().length,
          choice == profession,
          () -> {
            profession = choice;
            rebuildWidgets();
          });
    }
    return starterControl(
        right,
        rightWidth,
        profession.starters(),
        professionStarter(),
        id -> CharacterDetails.professionTalent(id, "title"),
        id -> CharacterDetails.professionTalent(id, "description"),
        id -> professionStarters.put(profession, id));
  }

  private int starterControl(
      int right,
      int rightWidth,
      List<String> choices,
      String selected,
      Function<String, Component> label,
      Function<String, Component> description,
      Consumer<String> select) {
    if (choices.size() == 1) return top + 53;
    addRenderableWidget(
        CycleButton.<String>builder(label)
            .withValues(choices)
            .withInitialValue(selected)
            .withTooltip(id -> Tooltip.create(description.apply(id)))
            .displayOnlyValue()
            .create(
                right,
                top + 62,
                rightWidth,
                18,
                text("ability"),
                (button, value) -> {
                  select.accept(value);
                  rebuildWidgets();
                }));
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
    if (page == Page.SUMMARY) {
      waiting = 100;
      retry = false;
      RaceNetwork.choose(race, style(), characterClass, starter(), profession, professionStarter());
    } else page = pages.get(pages.indexOf(page) + 1);
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
    } else if (waiting == 0 && page != pages.getFirst()) {
      page = pages.get(pages.indexOf(page) - 1);
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
    boolean tabsVisible = page != Page.SUMMARY;
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
    if (page == Page.RACE
        || page == Page.CLASS && characterClass.starters().size() > 1
        || page == Page.PROFESSION && profession.starters().size() > 1) {
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
