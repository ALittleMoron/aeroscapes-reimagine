package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aeroscapes.races.ChangeNetwork;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.CharacterSubclass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceNetwork;
import java.text.NumberFormat;
import java.util.Arrays;
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

final class RaceScreen extends Screen implements ChangeView {
  enum Page {
    RACE,
    CLASS,
    SUBCLASS,
    PROFESSION,
    SUMMARY;

    static List<Page> remaining(RaceClient.Profile profile) {
      var pages = EnumSet.allOf(Page.class);
      if (profile.complete()
          || profile.grantPending()
          || profile.subclass() != null
          || profile.subclassDeferred()) pages.remove(SUBCLASS);
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
  private final boolean optionalSubclass;
  private final boolean grantPending;
  private final boolean fromInventory;
  private final long changeSession;
  private final Screen changeMenu;
  private final RaceClient.Profile original;
  private Race race;
  private CharacterClass characterClass;
  private CharacterSubclass chosenSubclass;
  private boolean subclassDeferred;
  private Profession profession;
  private final int[] styles = new int[Race.values().length];
  private final int[] tints = new int[Race.values().length];
  private final EnumMap<CharacterClass, String> starters = new EnumMap<>(CharacterClass.class);
  private final EnumMap<Profession, String> professionStarters = new EnumMap<>(Profession.class);
  private Page page;
  private boolean expanded;
  private int waiting;
  private boolean retry;
  private RaceTintPicker tintPicker;
  private float rotation = -18;
  private int left, top, panelWidth, panelHeight, previewWidth;

  RaceScreen(RaceClient.Profile profile, boolean fromInventory) {
    this(profile, fromInventory, 0, null, null);
  }

  static RaceScreen chooseSubclass(RaceClient.Profile profile, boolean fromInventory) {
    return new RaceScreen(profile, fromInventory, 0, Page.SUBCLASS, null);
  }

  RaceScreen(RaceClient.Profile profile, long session, Page edit, Screen menu) {
    this(profile, false, session, edit, menu);
  }

  private RaceScreen(
      RaceClient.Profile profile, boolean fromInventory, long session, Page edit, Screen menu) {
    super(text(profile.complete() ? "title" : "create"));
    this.original = profile;
    this.changeSession = session;
    this.changeMenu = menu;
    this.fromInventory = fromInventory;
    pages = edit == null ? Page.remaining(profile) : List.of(edit);
    page = pages.getFirst();
    sheet = profile.complete() && edit == null;
    optionalSubclass = edit == Page.SUBCLASS && session == 0;
    chosenSubclass = profile.subclass();
    subclassDeferred = edit == null && profile.subclassDeferred();
    grantPending = profile.grantPending() && page == Page.SUMMARY;
    race = profile.race() != null ? profile.race() : Race.HUMAN;
    if (race.validStyle(profile.style())) styles[race.ordinal()] = profile.style();
    Arrays.fill(tints, Race.DEFAULT_TINT);
    tints[race.ordinal()] = profile.tint();
    characterClass = profile.hasClass() ? profile.characterClass() : CharacterClass.WARRIOR;
    if (profile.hasClass()) starters.put(characterClass, profile.starter());
    profession = profile.hasProfession() ? profile.profession() : Profession.BLACKSMITH;
    if (profile.hasProfession()) professionStarters.put(profession, profile.professionStarter());
  }

  private int style() {
    return styles[race.ordinal()];
  }

  private boolean hasTintControl() {
    return changeSession == 0
        && (page == Page.RACE || sheet)
        && (race.hasEars() || race.hasBeard(style()));
  }

  private int tint() {
    return race.canTint(style(), tints[race.ordinal()]) ? tints[race.ordinal()] : Race.DEFAULT_TINT;
  }

  @Override
  public long changeSession() {
    return changeSession;
  }

  Component changeDescription() {
    return switch (page) {
      case RACE ->
          text("identity", text(race.id() + ".name"), text(race.id() + ".style." + style()));
      case CLASS ->
          CharacterDetails.className(characterClass)
              .append(" · ")
              .append(CharacterDetails.talent(starter(), "title"))
              .append(
                  subclass() == null
                      ? Component.empty()
                      : Component.literal("\n").append(CharacterDetails.subclassName(subclass())));
      case SUBCLASS -> CharacterDetails.subclassProfile(subclass());
      case PROFESSION ->
          CharacterDetails.professionName(profession)
              .append(" · ")
              .append(CharacterDetails.professionTalent(professionStarter(), "title"));
      default -> throw new IllegalStateException("No change on the summary page");
    };
  }

  private String starter() {
    return starters.getOrDefault(characterClass, characterClass.starters().getFirst());
  }

  private CharacterSubclass subclass() {
    if (sheet) return original.subclass();
    if (subclassDeferred) return null;
    if (chosenSubclass != null && chosenSubclass.mainClass() == characterClass)
      return chosenSubclass;
    var choices = CharacterSubclass.choices(characterClass);
    return choices.isEmpty() ? null : choices.getFirst();
  }

  private String subclassId() {
    var selected = subclass();
    return selected == null ? "" : selected.id();
  }

  private String professionStarter() {
    return professionStarters.getOrDefault(profession, profession.starters().getFirst());
  }

  private Component heading() {
    if (tintPicker != null) return tintLabel();
    if (sheet) return title;
    return switch (page) {
      case RACE -> text("race_page");
      case CLASS -> text("class_page");
      case SUBCLASS -> text("subclass_page");
      case PROFESSION -> text("profession_page");
      case SUMMARY -> text("review");
    };
  }

  private Component details() {
    var doc = Component.empty();
    var language = minecraft.getLanguageManager().getSelected();
    var numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.replace('_', '-')));
    if (changeSession != 0) doc.append(ChangeClient.text("keep_kit")).append("\n\n");
    if (grantPending) doc.append(text("grant_pending")).append("\n\n");
    else if (retry) doc.append(text("retry")).append("\n\n");
    if (comparingSubclasses()) {
      if (page == Page.CLASS)
        doc.append(CharacterDetails.className(characterClass))
            .append("\n")
            .append(CharacterDetails.talent(starter(), "title"))
            .append("\n");
      for (var choice : CharacterSubclass.choices(characterClass))
        doc.append(CharacterDetails.subclassProfile(choice)).append("\n");
      return doc;
    }
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
    if (subclass() != null) doc.append("\n\n").append(CharacterDetails.subclassProfile(subclass()));
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
    return tintPicker == null ? heading().copy().append("\n").append(details()) : heading();
  }

  @Override
  protected void init() {
    panelWidth = Math.min(520, width - 8);
    int preferredHeight = page == Page.SUBCLASS ? 184 : comparingSubclasses() ? 235 : 310;
    panelHeight = Math.min(preferredHeight, height - 8);
    left = (width - panelWidth) / 2;
    top = (height - panelHeight) / 2;
    previewWidth = Math.min(134, panelWidth / 4);
    int x = left + 10;
    int right = x + previewWidth + 10;
    int rightWidth = left + panelWidth - 10 - right;
    int bottom = top + panelHeight;
    if (!sheet && !optionalSubclass && changeSession == 0) {
      var exit =
          minecraft.isLocalServer()
              ? Component.translatable("menu.returnToMenu")
              : CommonComponents.GUI_DISCONNECT;
      int exitWidth = font.width(exit) + 12;
      button(exit, left + panelWidth - 10 - exitWidth, top + 7, exitWidth, 17, this::leaveWorld);
    }
    if (tintPicker != null) {
      pickerControls(x, right, rightWidth, bottom);
      return;
    }
    int contentY =
        switch (page) {
          case RACE -> raceControls(right, rightWidth);
          case CLASS ->
              comparingSubclasses()
                  ? classControls(x, panelWidth - 20)
                  : classControls(right, rightWidth);
          case SUBCLASS -> top + 33;
          case PROFESSION -> professionControls(right, rightWidth);
          case SUMMARY -> top + 32;
        };
    if (hasTintControl()) {
      button(tintLabel(), right, contentY, rightWidth, 18, this::openTintPicker);
      contentY += 23;
    }
    if (comparingSubclasses()) {
      subclassCards(x, contentY, panelWidth - 20, bottom);
    } else {
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
    }
    int footer = bottom - 26;
    int half = (panelWidth - 24) / 2;
    if (changeSession != 0) {
      button(CommonComponents.GUI_BACK, x, footer, half, 18, this::onClose);
      var button =
          button(ChangeClient.text("preview"), x + half + 4, footer, half, 18, this::previewChange);
      button.active =
          switch (page) {
            case RACE -> race != original.race();
            case CLASS -> characterClass != original.characterClass();
            case SUBCLASS -> subclass() != original.subclass();
            case PROFESSION -> profession != original.profession();
            case SUMMARY -> false;
          };
      return;
    }
    if (optionalSubclass || page == Page.SUBCLASS) {
      button(text("subclass_later"), x, footer, half, 18, this::deferSubclass).active =
          waiting == 0;
      var confirm =
          button(
              text(waiting > 0 ? "saving" : retry ? "try_again" : "confirm"),
              x + half + 4,
              footer,
              half,
              18,
              this::advance);
      confirm.active = waiting == 0 && subclass() != null;
      if (retry) confirm.setTooltip(Tooltip.create(text("retry")));
      return;
    }
    if (sheet) {
      if (original.needsSubclass()) {
        button(CommonComponents.GUI_DONE, x, footer, half, 18, this::closeSheet);
        button(
            text("subclass_choose"),
            x + half + 4,
            footer,
            half,
            18,
            () -> RaceVisuals.openSubclassChoice(original, fromInventory));
      } else button(CommonComponents.GUI_DONE, x, footer, panelWidth - 20, 18, this::closeSheet);
      return;
    }
    if (page != pages.getFirst())
      button(CommonComponents.GUI_BACK, x, footer, half, 18, this::onClose).active = waiting == 0;
    var forward =
        switch (page) {
          case RACE -> text("to_class");
          case CLASS -> text(pages.contains(Page.SUBCLASS) ? "to_subclass" : "to_profession");
          case SUBCLASS -> text("confirm");
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

  private Component tintLabel() {
    return text(race.hasEars() ? "ear_tint" : "beard_tint");
  }

  private void openTintPicker() {
    if (tintPicker != null) return;
    tintPicker = new RaceTintPicker(tint(), race.hasEars() ? 0xc68b6d : 0x7c583f);
    rebuildWidgets();
  }

  private void pickerControls(int leftX, int rightX, int rightWidth, int bottom) {
    var picker = tintPicker;
    int paletteTop = top + 33;
    int paletteHeight = Math.clamp(bottom - paletteTop - 91, 12, 118);
    int hueTop = paletteTop + paletteHeight + 9;
    addRenderableWidget(picker.palette(rightX, paletteTop, rightWidth, paletteHeight));
    addRenderableWidget(picker.hueStrip(rightX, hueTop, rightWidth, 13));
    var resetLabel = text("reset_tint");
    int resetWidth = font.width(resetLabel) + 12;
    int resetY = hueTop + 20;
    button(resetLabel, rightX + rightWidth - resetWidth, resetY, resetWidth, 18, picker::reset)
        .setTooltip(Tooltip.create(text(race.hasEars() ? "ear_tint_hint" : "beard_tint_hint")));
    var autoLabel = text(race.hasEars() ? "tint_from_skin" : "tint_original_brown");
    var customLabel = text("tint_custom");
    addRenderableOnly(
        (graphics, mouseX, mouseY, partialTick) -> {
          int color = picker.color();
          if (color == Race.DEFAULT_TINT) {
            graphics.drawString(font, autoLabel, rightX, resetY + 5, TEXT, false);
          } else {
            graphics.fill(rightX, resetY + 3, rightX + 12, resetY + 15, color | 0xff000000);
            graphics.renderOutline(rightX, resetY + 3, 12, 12, TEXT);
            graphics.drawString(font, customLabel, rightX + 16, resetY + 5, TEXT, false);
          }
        });
    button(
        text("rotate"),
        leftX,
        bottom - 72,
        previewWidth,
        18,
        () -> rotation = (rotation + 45) % 360);
    int footer = bottom - 26;
    int half = (panelWidth - 24) / 2;
    button(CommonComponents.GUI_CANCEL, leftX, footer, half, 18, this::closeTintPicker);
    button(
        sheet ? text("apply_tint") : CommonComponents.GUI_DONE,
        leftX + half + 4,
        footer,
        half,
        18,
        this::acceptTint);
  }

  private void acceptTint() {
    if (tintPicker == null) return;
    int selected = tintPicker.color();
    if (sheet && tint() != selected) RaceNetwork.recolor(race, style(), selected);
    tints[race.ordinal()] = selected;
    closeTintPicker();
  }

  private void closeTintPicker() {
    if (tintPicker == null) return;
    tintPicker = null;
    rebuildWidgets();
  }

  private void previewChange() {
    var request =
        switch (page) {
          case RACE -> new ChangeNetwork.Request(changeSession, "race", race.id(), style(), "", "");
          case CLASS ->
              new ChangeNetwork.Request(
                  changeSession, "class", characterClass.id(), 0, starter(), subclassId());
          case SUBCLASS ->
              new ChangeNetwork.Request(changeSession, "subclass", subclassId(), 0, "", "");
          case PROFESSION ->
              new ChangeNetwork.Request(
                  changeSession, "profession", profession.id(), 0, professionStarter(), "");
          case SUMMARY -> throw new IllegalStateException("No change on the summary page");
        };
    ChangeClient.preview(this, request, changeDescription());
  }

  private int classControls(int right, int rightWidth) {
    var classes = CharacterClass.selectable();
    for (int index = 0; index < classes.size(); index++) {
      var choice = classes.get(index);
      tab(
          CharacterDetails.className(choice),
          index,
          classes.size(),
          choice == characterClass,
          () -> {
            characterClass = choice;
            subclassDeferred = false;
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

  private boolean comparingSubclasses() {
    return page == Page.SUBCLASS || page == Page.CLASS && changeSession != 0;
  }

  private void subclassCards(int x, int y, int width, int bottom) {
    var choices = CharacterSubclass.choices(characterClass);
    int columnWidth = (width - 6) / 2;
    for (int index = 0; index < choices.size(); index++) {
      var choice = choices.get(index);
      int column = x + index * (columnWidth + 6);
      boolean selected = choice == subclass();
      var selector =
          button(
              CharacterDetails.subclassName(choice),
              column,
              y,
              columnWidth,
              18,
              () -> {
                chosenSubclass = choice;
                subclassDeferred = false;
                rebuildWidgets();
              });
      selector.active = !selected && waiting == 0;
      selector.setTooltip(Tooltip.create(CharacterDetails.subclassProfile(choice)));
      if (selected)
        addRenderableOnly(
            (graphics, mouseX, mouseY, partialTick) ->
                graphics.renderOutline(column - 1, y - 1, columnWidth + 2, 20, GOLD));
      addRenderableWidget(
          new FittingMultiLineTextWidget(
                  column + 3,
                  y + 24,
                  columnWidth - 6,
                  Math.max(1, bottom - y - 56),
                  CharacterDetails.subclassSummary(choice),
                  font)
              .setColor(TEXT));
    }
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
    if (optionalSubclass) {
      waiting = 100;
      retry = false;
      RaceNetwork.chooseSubclass(subclass());
    } else if (page == Page.SUMMARY) {
      waiting = 100;
      retry = false;
      RaceNetwork.choose(
          race,
          style(),
          tint(),
          characterClass,
          starter(),
          profession,
          professionStarter(),
          subclassId());
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
    if (changeSession != 0) return;
    if (minecraft == null || minecraft.player == null) return;
    var confirmed = RaceClient.profile(minecraft.player.getUUID());
    if (!sheet
        && confirmed != null
        && confirmed.complete()
        && (!optionalSubclass || confirmed.subclass() != null)) closeSheet();
    else if (waiting == 1) declined();
    else if (waiting > 1) waiting--;
  }

  @Override
  public void onClose() {
    if (tintPicker != null) {
      closeTintPicker();
    } else if (changeSession != 0) {
      minecraft.setScreen(changeMenu);
    } else if (sheet) {
      closeSheet();
    } else if (optionalSubclass && waiting == 0) {
      deferSubclass();
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

  private void deferSubclass() {
    if (optionalSubclass) {
      RaceNetwork.deferSubclass();
      closeSheet();
    } else {
      subclassDeferred = true;
      chosenSubclass = null;
      advance();
    }
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
    if (comparingSubclasses()) {
      if (page == Page.CLASS && characterClass.starters().size() > 1)
        graphics.drawString(font, text("ability"), x, top + 51, GOLD, false);
      return;
    }
    boolean pickingTint = tintPicker != null;
    boolean tabsVisible = !pickingTint && page != Page.SUMMARY && page != Page.SUBCLASS;
    int previewY = top + (tabsVisible ? 51 : 30), previewBottom = bottom - 76;
    graphics.fillGradient(x, previewY, x + previewWidth, previewBottom, 0xff162333, 0xff263c52);
    graphics.renderOutline(x, previewY, previewWidth, previewBottom - previewY, 0xff3d536d);
    int floor = previewBottom - 6;
    if (!pickingTint) graphics.hLine(x + 5, x + previewWidth - 6, floor, 0xff718399);
    if (minecraft != null && minecraft.player != null && floor > previewY + 4) {
      graphics.enableScissor(x + 1, previewY + 1, x + previewWidth - 1, previewBottom - 1);
      preview.draw(
          graphics,
          minecraft.player,
          race,
          style(),
          pickingTint ? tintPicker.color() : tint(),
          rotation,
          x + previewWidth / 2,
          pickingTint ? (previewY + previewBottom) / 2 : floor,
          Math.min(floor - previewY - 4, (previewWidth - 8) * (pickingTint ? 1 : 2)),
          pickingTint);
      graphics.disableScissor();
    }
    if (tabsVisible
        && (page == Page.RACE
            || page == Page.CLASS && characterClass.starters().size() > 1
            || page == Page.PROFESSION && profession.starters().size() > 1)) {
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
    return !sheet && !optionalSubclass && changeSession == 0;
  }
}
