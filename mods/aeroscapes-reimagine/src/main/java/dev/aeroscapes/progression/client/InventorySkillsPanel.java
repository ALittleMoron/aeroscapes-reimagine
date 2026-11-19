package dev.aeroscapes.progression.client;

import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.client.AbilityClient;
import dev.aeroscapes.mixin.progression.InventoryLayoutAccess;
import harmonised.pmmo.client.gui.glossary.components.parts.PlayerSkillWidget;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.config.codecs.SkillData;
import harmonised.pmmo.config.codecs.SkillTypeData;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.setup.datagen.LangProvider;
import harmonised.pmmo.storage.Experience;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.LogicalSide;

/** Inventory-local practice levels, with real magic credit over the provider's other skill rows. */
public final class InventorySkillsPanel extends AbstractWidget {
  public static final int PANEL_WIDTH = 130;
  private final InventoryScreen screen;
  private final EditBox search;
  private final List<Group> groups = new ArrayList<>();
  private final ScrollViewport scroll = new ScrollViewport();
  private boolean dragging;
  private double grabOffset;
  private int naturalLeft;
  private int adjustedLeft;
  private boolean shifted;

  InventorySkillsPanel(InventoryScreen screen) {
    super(
        screen.getGuiLeft() - PANEL_WIDTH - 4,
        screen.getGuiTop(),
        PANEL_WIDTH,
        screen.getYSize(),
        Component.translatable("aeroscapes_reimagine.progression.skills_button"));
    this.screen = screen;
    search =
        new EditBox(
            Minecraft.getInstance().font,
            0,
            0,
            PANEL_WIDTH - 16,
            14,
            Component.translatable("aeroscapes_reimagine.progression.search"));
    search.setHint(Component.translatable("aeroscapes_reimagine.progression.search"));
    search.setResponder(query -> scroll.reset());
    var placed = new HashSet<String>();
    Config.skills().types().entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<String, SkillTypeData>>comparingInt(
                    entry -> entry.getValue().getOrder())
                .thenComparing(Map.Entry::getKey))
        .forEach(
            entry -> {
              List<Row> rows = new ArrayList<>();
              for (String skill : entry.getValue().getSkills()) {
                var data = Config.skills().skills().get(skill);
                if (data != null && data.getShowInList() && placed.add(skill)) {
                  rows.add(new Row(skill, data));
                }
              }
              groups.add(
                  new Group(
                      entry.getValue().getDisplayName(entry.getKey()),
                      entry.getValue().getColor(),
                      rows));
            });
    var remaining = new ArrayList<Row>();
    Config.skills().skills().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .filter(entry -> entry.getValue().getShowInList() && placed.add(entry.getKey()))
        .forEach(entry -> remaining.add(new Row(entry.getKey(), entry.getValue())));
    if (!remaining.isEmpty()) groups.add(new Group(Component.empty(), 0xff92a9c3, remaining));
    AbilityClient.request();
    update();
  }

  void update() {
    if (screen.getRecipeBookComponent().isVisible()) SkillsPanelButton.hidePanel();
    visible = SkillsPanelButton.panelShown();
    if (!visible && shifted) {
      if (screen.getGuiLeft() == adjustedLeft) moveInventory(naturalLeft);
      shifted = false;
    }
    if (visible && screen.getGuiLeft() < PANEL_WIDTH + 6) {
      naturalLeft = screen.getGuiLeft();
      adjustedLeft = Math.min(PANEL_WIDTH + 6, screen.width - screen.getXSize() - 2);
      moveInventory(adjustedLeft);
      shifted = true;
    }
    setPosition(screen.getGuiLeft() - PANEL_WIDTH - 4, screen.getGuiTop());
    search.setPosition(getX() + 8, getY() + 8);
    if (!visible) {
      search.setFocused(false);
      dragging = false;
    }
  }

  private void moveInventory(int left) {
    int delta = left - screen.getGuiLeft();
    ((InventoryLayoutAccess) screen).aeroscapes$leftPos(left);
    for (var child : screen.children()) {
      if (child instanceof AbstractWidget widget && child != this)
        widget.setX(widget.getX() + delta);
    }
  }

  void defocusSearch() {
    search.setFocused(false);
  }

  @Override
  public void setFocused(boolean focused) {
    super.setFocused(focused);
    if (!focused) search.setFocused(false);
  }

  private List<Row> matching(Group group) {
    String query = search.getValue().toLowerCase(Locale.ROOT);
    return group.rows().stream()
        .filter(
            row ->
                LangProvider.skill(row.skill())
                    .getString()
                    .toLowerCase(Locale.ROOT)
                    .contains(query))
        .toList();
  }

  private int bodyTop() {
    return getY() + 27;
  }

  private int bodyHeight() {
    return height - 35;
  }

  @Override
  public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    update();
    if (!visible) return;
    AtlasProgressionWidgets.panel(graphics, getX(), getY(), width, height);
    search.render(graphics, mouseX, mouseY, partialTick);
    int contentHeight =
        groups.stream()
            .mapToInt(
                group -> {
                  int count = matching(group).size();
                  return count == 0 ? 0 : 14 + count * 25 + 4;
                })
            .sum();
    scroll.resize(contentHeight, bodyHeight());
    graphics.enableScissor(getX() + 5, bodyTop(), getRight() - 10, bodyTop() + bodyHeight());
    int y = bodyTop() - (int) scroll.offset();
    List<Component> magicTooltip = List.of();
    for (Group group : groups) {
      var rows = matching(group);
      if (rows.isEmpty()) continue;
      int bottom = y + 14 + rows.size() * 25;
      graphics.fill(getX() + 6, y, getX() + 8, bottom, 0xff000000 | group.color());
      graphics.drawString(
          Minecraft.getInstance().font,
          group.label(),
          getX() + 10,
          y + 2,
          0xff000000 | group.color(),
          false);
      y += 14;
      for (Row row : rows) {
        boolean hovered =
            mouseY >= bodyTop()
                && mouseY < bodyTop() + bodyHeight()
                && mouseX >= getX() + 10
                && mouseX < getRight() - 13
                && mouseY >= y
                && mouseY < y + 24;
        if (row.skill().equals("magic")) {
          drawMagic(graphics, row.data, y, hovered);
          if (hovered) magicTooltip = magicTooltip();
        } else {
          row.widget().setPosition(getX() + 10, y);
          row.widget().setFocused(hovered);
          row.widget().render(graphics, mouseX, mouseY, partialTick);
        }
        y += 25;
      }
      y += 4;
    }
    graphics.disableScissor();
    int track = getRight() - 7;
    graphics.fill(track, bodyTop(), track + 3, bodyTop() + bodyHeight(), 0xff111b29);
    if (scroll.maximum() > 0) {
      int top = bodyTop() + scroll.thumbPosition();
      graphics.fill(
          track, top, track + 3, top + scroll.thumbSize(), dragging ? 0xffe9c084 : 0xff92a9c3);
    }
    if (!magicTooltip.isEmpty()) {
      var lines = new ArrayList<FormattedCharSequence>();
      for (Component line : magicTooltip)
        lines.addAll(Minecraft.getInstance().font.split(line, Math.min(220, screen.width - 24)));
      graphics.renderTooltip(
          Minecraft.getInstance().font, lines, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY);
    }
  }

  private static Component magicText(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.progression.magic." + key, arguments);
  }

  private void drawMagic(GuiGraphics graphics, SkillData data, int y, boolean hovered) {
    var font = Minecraft.getInstance().font;
    var snapshot = AbilityClient.snapshot();
    int x = getX() + 10, rowWidth = PANEL_WIDTH - 23;
    int color = 0xff000000 | data.getColor();
    graphics.blitSprite(
        ResourceLocation.parse("pmmo:stat_background" + (hovered ? "_highlighted" : "")),
        x,
        y,
        rowWidth,
        24);
    int iconSize = data.getIconSize();
    graphics.blit(
        data.getIcon(), x + 3, y + 3, 18, 18, 0, 0, iconSize, iconSize, iconSize, iconSize);
    String level = AbilityClient.hasSnapshot() ? String.valueOf(snapshot.practiceLevel()) : "…";
    graphics.pose().pushPose();
    graphics.pose().translate(x + 24, y + 5, 0);
    graphics.pose().scale(0.85f, 0.85f, 1);
    graphics.drawString(
        font,
        font.plainSubstrByWidth(
            LangProvider.skill("magic").getString(),
            (int) ((rowWidth - 35 - font.width(level)) / 0.85)),
        0,
        0,
        color,
        false);
    graphics.pose().popPose();
    graphics.drawString(font, level, x + rowWidth - 7 - font.width(level), y + 5, color, false);
    long baseline =
        snapshot.practiceLevel() < 50
            ? MagicPracticeProgress.minimumXp(snapshot.practiceLevel())
            : MagicPracticeProgress.xpForPoints(
                MagicPracticeProgress.points(snapshot.practiceXp(), MagicTalents.pointCap()));
    long goal =
        snapshot.practiceLevel() < 50
            ? MagicPracticeProgress.minimumXp(snapshot.practiceLevel() + 1) - baseline
            : Math.max(0, snapshot.nextTalentXp() - baseline);
    long earned = Math.max(0, snapshot.practiceXp() - baseline);
    if (hovered) {
      Component progress;
      if (!AbilityClient.hasSnapshot()) progress = magicText("waiting");
      else if (snapshot.practiceLevel() < 50)
        progress =
            magicText("row_next_level", Math.max(0, goal - earned), snapshot.practiceLevel() + 1);
      else if (snapshot.nextTalentXp() > 0)
        progress =
            magicText(
                "row_next_talent", Math.max(0, snapshot.nextTalentXp() - snapshot.practiceXp()));
      else progress = magicText("row_capped");
      graphics.pose().pushPose();
      graphics.pose().translate(x + 24, y + 14, 0);
      graphics.pose().scale(0.6f, 0.6f, 1);
      graphics.drawString(
          font,
          font.plainSubstrByWidth(progress.getString(), (int) ((rowWidth - 27) / 0.6)),
          0,
          0,
          color,
          false);
      graphics.pose().popPose();
    } else {
      int barWidth = rowWidth - 27;
      double fraction = goal > 0 ? Math.clamp((double) earned / goal, 0, 1) : 1;
      graphics.fill(x + 24, y + 15, x + 24 + barWidth, y + 20, 0xff111b29);
      graphics.fill(x + 24, y + 15, x + 24 + (int) (barWidth * fraction), y + 20, color);
    }
  }

  private List<Component> magicTooltip() {
    if (!AbilityClient.hasSnapshot()) return List.of(magicText("waiting"));
    var snapshot = AbilityClient.snapshot();
    var tooltip = new ArrayList<Component>();
    tooltip.add(magicText("level", snapshot.practiceLevel()));
    tooltip.add(magicText("total_xp", snapshot.practiceXp()));
    if (snapshot.practiceLevel() < 50)
      tooltip.add(
          magicText(
              "next_level",
              Math.max(
                  0,
                  MagicPracticeProgress.minimumXp(snapshot.practiceLevel() + 1)
                      - snapshot.practiceXp()),
              snapshot.practiceLevel() + 1));
    else tooltip.add(magicText("practice_cap"));
    tooltip.add(
        snapshot.nextTalentXp() > 0
            ? magicText("next_talent", Math.max(0, snapshot.nextTalentXp() - snapshot.practiceXp()))
            : magicText("talent_cap"));
    return tooltip;
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    if (!visible || !isMouseOver(mouseX, mouseY)) return false;
    scroll.wheel(vertical);
    return true;
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (!visible || !isMouseOver(mouseX, mouseY)) return false;
    boolean editing = search.mouseClicked(mouseX, mouseY, button);
    search.setFocused(editing);
    if (button == 0
        && mouseX >= getRight() - 10
        && mouseY >= bodyTop()
        && mouseY < bodyTop() + bodyHeight()
        && scroll.maximum() > 0) {
      dragging = true;
      double relative = mouseY - bodyTop() - scroll.thumbPosition();
      grabOffset =
          relative >= 0 && relative < scroll.thumbSize() ? relative : scroll.thumbSize() / 2.0;
      scroll.drag(mouseY - bodyTop() - grabOffset);
    }
    return true;
  }

  @Override
  public boolean mouseDragged(
      double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    if (!visible || !dragging || button != 0) return false;
    scroll.drag(mouseY - bodyTop() - grabOffset);
    return true;
  }

  @Override
  public boolean mouseReleased(double mouseX, double mouseY, int button) {
    boolean handled = dragging;
    dragging = false;
    return handled;
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    return visible
        && search.isFocused()
        && (search.keyPressed(key, scan, modifiers)
            || key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && search.canConsumeInput());
  }

  @Override
  public boolean charTyped(char character, int modifiers) {
    return visible && search.isFocused() && search.charTyped(character, modifiers);
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(NarratedElementType.TITLE, getMessage());
  }

  private static final class Row {
    private final String skill;
    private final SkillData data;
    private PlayerSkillWidget widget;
    private Experience experience;

    private Row(String skill, SkillData data) {
      this.skill = skill;
      this.data = data;
    }

    private String skill() {
      return skill;
    }

    private PlayerSkillWidget widget() {
      Experience current = Core.get(LogicalSide.CLIENT).getData().getXpMap(null).get(skill);
      // Provider packets replace Experience objects; its row retains the constructor reference.
      if (widget == null || current != experience) {
        experience = current;
        widget = new PlayerSkillWidget(PANEL_WIDTH - 23, skill, data);
      }
      return widget;
    }
  }

  private record Group(Component label, int color, List<Row> rows) {}
}
