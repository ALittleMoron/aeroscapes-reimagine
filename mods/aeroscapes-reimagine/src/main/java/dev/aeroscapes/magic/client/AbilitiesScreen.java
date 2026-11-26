package dev.aeroscapes.magic.client;

import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import dev.aeroscapes.inventory.InventoryNavigation;
import dev.aeroscapes.magic.AbilityLoadout;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.client.ScrollViewport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Manual preparation is independent of the focused book's native inscription slots. */
public final class AbilitiesScreen extends Screen {
  private final ScrollViewport scroll = new ScrollViewport();
  private final ScrollViewport detailsScroll = new ScrollViewport();
  private EditBox search;
  private String query = "";
  private String school = "";
  private String chosen = "";
  private boolean learnedOnly = true;
  private String assignmentFailure = "";
  private int editingWheel = AbilityClient.snapshot().loadout().activeWheel();
  private int targetSlot;
  private boolean dragging;
  private double grabOffset;
  private Button clear;

  public AbilitiesScreen() {
    super(AbilityClient.text("title"));
  }

  @Override
  protected void init() {
    if (search != null) query = search.getValue();
    search = null;
    dragging = false;
    addRenderableWidget(
        Button.builder(Component.literal("×"), button -> onClose())
            .bounds(Math.max(4, width - 33), 13, 17, 18)
            .build());
    if (width < 320 || height < 220) return;
    search =
        addRenderableWidget(
            new EditBox(font, 20, 43, listWidth() - 8, 16, AbilityClient.text("search")));
    search.setHint(AbilityClient.text("search"));
    search.setValue(query);
    search.setResponder(
        value -> {
          query = value;
          refreshMatches();
        });
    addRenderableWidget(
        Button.builder(
                AbilityClient.text("school_filter"),
                button -> {
                  var schools = schools();
                  int index = schools.indexOf(school);
                  school = index + 1 < schools.size() ? schools.get(index + 1) : "";
                  refreshMatches();
                })
            .bounds(detailsLeft(), 41, detailsWidth(), 20)
            .build());
    addRenderableWidget(
        Checkbox.builder(AbilityClient.text("learned_only"), font)
            .pos(20, height - 30)
            .maxWidth(width - 40)
            .selected(learnedOnly)
            .tooltip(Tooltip.create(AbilityClient.text("learned_only_hint")))
            .onValueChange(
                (checkbox, selected) -> {
                  learnedOnly = selected;
                  refreshMatches();
                })
            .build());
    clear =
        addRenderableWidget(
            Button.builder(
                    AbilityClient.text("clear"),
                    button -> {
                      AbilityClient.edit(editingWheel, targetSlot, "");
                      chosen = "";
                      assignmentFailure = "";
                    })
                .bounds(detailsLeft(), height - 130, detailsWidth(), 20)
                .build());
    for (int wheel = 0; wheel < AbilityLoadout.WHEEL_COUNT; wheel++) {
      int index = wheel;
      addRenderableWidget(
          Button.builder(Component.literal(Integer.toString(wheel + 1)), button -> editWheel(index))
              .bounds(20 + wheel * 26, height - 118, 23, 17)
              .tooltip(Tooltip.create(AbilityClient.text("wheel_number", wheel + 1)))
              .build());
    }
    AbilityClient.request();
  }

  private void editWheel(int wheel) {
    if (editingWheel == wheel) return;
    editingWheel = wheel;
    targetSlot = AbilityClient.snapshot().loadout().wheels().get(wheel).selected();
    assignmentFailure = "";
    detailsScroll.reset();
  }

  private int listWidth() {
    return Math.max(130, width - 216);
  }

  private int detailsLeft() {
    return listWidth() + 28;
  }

  private int detailsWidth() {
    return width - detailsLeft() - 20;
  }

  private int bodyHeight() {
    return height - 190;
  }

  private List<String> schools() {
    return AbilityClient.snapshot().entries().stream()
        .map(AbilityNetwork.Entry::school)
        .distinct()
        .sorted()
        .toList();
  }

  private List<AbilityNetwork.Entry> matching() {
    return AbilityCatalogue.matching(
        AbilityClient.snapshot().entries(),
        learnedOnly,
        school,
        query,
        id -> AbilityClient.name(id).getString());
  }

  private void refreshMatches() {
    dragging = false;
    scroll.reset();
    detailsScroll.reset();
    assignmentFailure = "";
    if (matching().stream().noneMatch(entry -> entry.id().equals(chosen))) chosen = "";
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.renderBackground(graphics, mouseX, mouseY, partialTick);
    AbilityWidgets.panel(graphics, 8, 8, width - 16, height - 16);
    graphics.drawString(font, title, 20, 18, AbilityWidgets.GOLD, false);
    if (search == null) {
      graphics.drawWordWrap(
          font,
          AbilityClient.text("small_window"),
          20,
          55,
          Math.max(30, width - 40),
          AbilityWidgets.TEXT);
      return;
    }
    AbilityWidgets.panel(graphics, 16, 67, listWidth(), bodyHeight());
    AbilityWidgets.panel(graphics, detailsLeft() - 4, 67, detailsWidth() + 8, bodyHeight());
    var entries = matching();
    scroll.resize(entries.size() * 31, bodyHeight() - 8);
    graphics.enableScissor(20, 71, listWidth() + 8, 63 + bodyHeight());
    int y = 71 - (int) scroll.offset();
    for (var entry : entries) {
      String assignmentReason = AbilityCatalogue.assignmentReason(entry);
      boolean hovered =
          mouseX >= 20
              && mouseX < listWidth() + 8
              && mouseY >= Math.max(y, 71)
              && mouseY < Math.min(y + 29, 63 + bodyHeight());
      if (entry.id().equals(chosen) || hovered)
        graphics.fill(
            20, y, listWidth() + 7, y + 29, entry.id().equals(chosen) ? 0xff3d536d : 0xff293d54);
      AbilityClient.icon(graphics, entry.id(), 24, y + 5, 18);
      var lines = font.split(AbilityClient.name(entry.id()), listWidth() - 46);
      if (!lines.isEmpty())
        graphics.drawString(
            font,
            lines.getFirst(),
            47,
            y + 3,
            assignmentReason.isEmpty() ? AbilityWidgets.TEXT : AbilityWidgets.MUTED,
            false);
      Component state =
          assignmentReason.isEmpty()
              ? entry.granted() || entry.technique()
                  ? AbilityClient.school(entry)
                  : AbilityClient.text("rank_short", entry.level(), entry.cap())
              : AbilityClient.text(
                  assignmentReason.equals("unlearned")
                          || assignmentReason.equals("technique_unlearned")
                      ? "unlearned_short"
                      : "unavailable_short");
      var stateLines = font.split(state, listWidth() - 46);
      if (!stateLines.isEmpty())
        graphics.drawString(
            font,
            stateLines.getFirst(),
            47,
            y + 16,
            assignmentReason.isEmpty() ? AbilityWidgets.MUTED : 0xffef9b8c,
            false);
      y += 31;
    }
    graphics.disableScissor();
    if (entries.isEmpty()) {
      boolean noLearned =
          learnedOnly
              && AbilityClient.snapshot().entries().stream().noneMatch(AbilityNetwork.Entry::known);
      graphics.enableScissor(20, 71, listWidth() + 8, 63 + bodyHeight());
      graphics.drawWordWrap(
          font,
          AbilityClient.text(
              AbilityClient.snapshot().entries().isEmpty()
                  ? "waiting"
                  : noLearned ? "none_learned_short" : "no_matches"),
          25,
          78,
          listWidth() - 18,
          AbilityWidgets.MUTED);
      graphics.disableScissor();
    }
    int track = listWidth() + 10;
    graphics.fill(track, 71, track + 3, 63 + bodyHeight(), 0xff111b29);
    if (scroll.maximum() > 0)
      graphics.fill(
          track,
          71 + scroll.thumbPosition(),
          track + 3,
          71 + scroll.thumbPosition() + scroll.thumbSize(),
          dragging ? AbilityWidgets.GOLD : AbilityWidgets.MUTED);
    drawDetails(graphics);
    drawSlots(graphics, mouseX, mouseY);
    clear.active = !AbilityClient.slot(editingWheel, targetSlot).isEmpty();
  }

  private void drawDetails(GuiGraphics graphics) {
    int x = detailsLeft() + 3;
    int textWidth = detailsWidth() - 6;
    var entry = AbilityClient.entry(chosen);
    Component filter =
        school.isEmpty()
            ? AbilityClient.text("all_schools")
            : AbilityClient.snapshot().entries().stream()
                .filter(candidate -> candidate.school().equals(school))
                .findFirst()
                .map(AbilityClient::school)
                .orElse(AbilityClient.text("all_schools"));
    int y = drawText(graphics, filter, x, 74, textWidth, AbilityWidgets.GOLD) + 5;
    int viewportTop = y;
    var paragraphs = new ArrayList<Component>();
    if (entry == null) {
      boolean noLearned =
          learnedOnly
              && AbilityClient.snapshot().entries().stream().noneMatch(AbilityNetwork.Entry::known);
      paragraphs.add(
          AbilityClient.text(
              AbilityClient.snapshot().entries().isEmpty()
                  ? "waiting"
                  : noLearned ? "none_learned" : chosen.isEmpty() ? "choose" : "waiting"));
      paragraphs.add(AbilityClient.text("bindings_hint"));
    } else {
      String assignmentReason = AbilityCatalogue.assignmentReason(entry);
      if (!assignmentFailure.isEmpty())
        paragraphs.add(
            AbilityClient.text("assignment_failed", AbilityClient.reason(assignmentFailure))
                .copy()
                .withColor(0xffef9b8c));
      paragraphs.add(AbilityClient.name(entry.id()));
      paragraphs.add(AbilityClient.school(entry));
      var technique = MartialTechnique.byId(entry.id());
      if (technique != null) {
        paragraphs.add(technique.description());
        paragraphs.add(AbilityClient.text("technique_cooldown", technique.cooldownTicks() / 20));
      } else if (entry.program() != null) {
        paragraphs.add(AbilityClient.text("hex_hint"));
      } else if (entry.granted()) {
        paragraphs.add(
            AbilityClient.text(
                net.minecraft.resources.ResourceLocation.parse(entry.id()).getPath() + "_hint"));
      } else {
        String guide = "spell." + entry.id().replace(':', '.') + ".guide";
        if (I18n.exists(guide)) paragraphs.add(Component.translatable(guide));
        paragraphs.add(AbilityClient.text("rank", entry.level(), entry.maxLevel(), entry.cap()));
      }
      paragraphs.add(
          assignmentReason.isEmpty()
              ? entry.technique() ? AbilityClient.status(entry) : AbilityClient.reason(entry)
              : AbilityClient.reason(assignmentReason));
      if (assignmentReason.isEmpty()) {
        paragraphs.add(AbilityClient.text(assignmentHint(entry)));
        paragraphs.add(AbilityClient.text("move_hint"));
      }
      paragraphs.add(AbilityClient.text("controls"));
    }
    int contentHeight =
        paragraphs.stream()
            .mapToInt(text -> font.split(text, textWidth).size() * (font.lineHeight + 1) + 5)
            .sum();
    detailsScroll.resize(contentHeight, Math.max(1, 63 + bodyHeight() - viewportTop));
    graphics.enableScissor(x, viewportTop, x + textWidth, 63 + bodyHeight());
    y -= (int) detailsScroll.offset();
    for (var paragraph : paragraphs)
      y = drawText(graphics, paragraph, x, y, textWidth, AbilityWidgets.TEXT) + 5;
    graphics.disableScissor();
  }

  private static String assignmentHint(AbilityNetwork.Entry entry) {
    if (entry.technique()) return "technique_assign";
    if (entry.program() != null) return "hex_assign";
    if (ExceptionalAccessory.byId(entry.id()) != null
        || PreparedAbilities.DISTRACTION.equals(entry.id())) return "expedition_assign";
    return entry.granted() ? "innate_assign" : "assign_hint";
  }

  private int drawText(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
    for (var line : font.split(text, width)) {
      graphics.drawString(font, line, x, y, color, false);
      y += font.lineHeight + 1;
    }
    return y;
  }

  private int slotWidth() {
    return (width - 48) / 4;
  }

  private void drawSlots(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(
        font, AbilityClient.text("slots"), 104, height - 114, AbilityWidgets.GOLD, false);
    graphics.renderOutline(19 + editingWheel * 26, height - 119, 25, 19, AbilityWidgets.GOLD);
    for (int slot = 0; slot < AbilityClient.SLOT_COUNT; slot++) {
      int x = 20 + (slot % 4) * (slotWidth() + 2);
      int y = height - 97 + (slot / 4) * 27;
      AbilityWidgets.panel(graphics, x, y, slotWidth(), 25);
      if (slot == targetSlot)
        graphics.renderOutline(x + 1, y + 1, slotWidth() - 2, 23, AbilityWidgets.GOLD);
      String id = AbilityClient.slot(editingWheel, slot);
      AbilityClient.icon(graphics, id, x + 4, y + 4, 16);
      graphics.drawString(
          font, Integer.toString(slot + 1), x + 23, y + 4, AbilityWidgets.GOLD, false);
      if (!id.isEmpty()) {
        var lines = font.split(AbilityClient.name(id), slotWidth() - 36);
        if (!lines.isEmpty())
          graphics.drawString(font, lines.getFirst(), x + 33, y + 9, AbilityWidgets.TEXT, false);
      }
      if (mouseX >= x && mouseX < x + slotWidth() && mouseY >= y && mouseY < y + 25)
        graphics.renderComponentTooltip(
            font,
            List.of(
                id.isEmpty() ? AbilityClient.text("empty") : AbilityClient.name(id),
                AbilityClient.text("move_hint"),
                AbilityClient.text("controls")),
            mouseX,
            mouseY);
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (search == null || button != 0 && button != 1)
      return super.mouseClicked(mouseX, mouseY, button);
    if (button == 0 && mouseY >= 71 && mouseY < 63 + bodyHeight()) {
      if (mouseX >= listWidth() + 7 && mouseX < listWidth() + 16 && scroll.maximum() > 0) {
        dragging = true;
        double offset = mouseY - 71 - scroll.thumbPosition();
        grabOffset = offset >= 0 && offset < scroll.thumbSize() ? offset : scroll.thumbSize() / 2.0;
        scroll.drag(mouseY - 71 - grabOffset);
        return true;
      }
      if (mouseX >= 20 && mouseX < listWidth() + 7) {
        int index = (int) ((mouseY - 71 + scroll.offset()) / 31);
        var entries = matching();
        if (index >= 0 && index < entries.size()) {
          chosen = entries.get(index).id();
          assignmentFailure = "";
          detailsScroll.reset();
        }
        return true;
      }
    }
    for (int slot = 0; slot < AbilityClient.SLOT_COUNT; slot++) {
      int x = 20 + (slot % 4) * (slotWidth() + 2);
      int y = height - 97 + (slot / 4) * 27;
      if (mouseX >= x && mouseX < x + slotWidth() && mouseY >= y && mouseY < y + 25) {
        var entry = AbilityClient.entry(chosen);
        if (button == 0 && !chosen.isEmpty()) {
          assignmentFailure = AbilityCatalogue.assignmentReason(entry);
          if (assignmentFailure.isEmpty()) AbilityClient.edit(editingWheel, slot, chosen);
        } else {
          chosen = AbilityClient.slot(editingWheel, slot);
          assignmentFailure = "";
        }
        targetSlot = slot;
        detailsScroll.reset();
        return true;
      }
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (search != null && x >= 16 && x < 16 + listWidth() && y >= 67 && y < 67 + bodyHeight()) {
      scroll.wheel(vertical);
      return true;
    }
    if (search != null
        && x >= detailsLeft()
        && x < detailsLeft() + detailsWidth()
        && y >= 67
        && y < 67 + bodyHeight()) {
      detailsScroll.wheel(vertical);
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
    if (dragging && button == 0) {
      scroll.drag(y - 71 - grabOffset);
      return true;
    }
    return super.mouseDragged(x, y, button, deltaX, deltaY);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    boolean handled = dragging;
    dragging = false;
    return handled || super.mouseReleased(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == GLFW.GLFW_KEY_ESCAPE) {
      onClose();
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public void onClose() {
    InventoryNavigation.openInventory();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
