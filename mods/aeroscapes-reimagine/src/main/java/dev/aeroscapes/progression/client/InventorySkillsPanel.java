package dev.aeroscapes.progression.client;

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
import net.minecraft.network.chat.Component;
import net.neoforged.fml.LogicalSide;

/** Inventory-local viewport retaining the provider's level and progress display. */
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
        row.widget().setPosition(getX() + 10, y);
        boolean hovered =
            mouseY >= bodyTop()
                && mouseY < bodyTop() + bodyHeight()
                && row.widget().isMouseOver(mouseX, mouseY);
        row.widget().setFocused(hovered);
        row.widget().render(graphics, mouseX, mouseY, partialTick);
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
