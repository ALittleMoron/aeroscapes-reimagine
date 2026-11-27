package dev.aeroscapes.magic.client;

import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.SpellProgression;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Native menu slots remain authoritative; the button requests personal study. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SpellStudyScreen extends AbstractContainerScreen<InscriptionTableMenu> {
  private Button study;
  private String studyState = "";
  private RepeatStudy repeats;
  private boolean repeatPointer;

  public SpellStudyScreen(InscriptionTableMenu menu, Inventory inventory) {
    super(menu, inventory, AbilityClient.text("study.title"));
    imageWidth = 256;
    imageHeight = 166;
  }

  @SubscribeEvent
  public static void replace(ScreenEvent.Opening event) {
    if (event.getNewScreen() instanceof InscriptionTableScreen original
        && Minecraft.getInstance().player != null)
      event.setNewScreen(
          new SpellStudyScreen(original.getMenu(), Minecraft.getInstance().player.getInventory()));
  }

  @Override
  protected void init() {
    super.init();
    study =
        addRenderableWidget(
            Button.builder(
                    AbilityClient.text("study.action"),
                    button -> {
                      if (studyStatus().equals("ready")
                          && minecraft != null
                          && minecraft.gameMode != null)
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, -1);
                    })
                .bounds(leftPos + 44, topPos + 52, 118, 20)
                .build());
    study.setTooltip(Tooltip.create(AbilityClient.text("study.rank_rules")));
    study.active = false;
    studyState = "";
    addRenderableWidget(
        Button.builder(
                AbilityClient.text("study.repeat.button"),
                button -> {
                  repeats = new RepeatStudy();
                  repeats.init(minecraft, width, height);
                  AbilityClient.request();
                })
            .bounds(leftPos + 183, topPos + 130, 60, 20)
            .build());
    if (repeats != null) repeats.resize(minecraft, width, height);
    AbilityClient.request();
  }

  private SpellData scrollSpell() {
    var stack = menu.getScrollSlot().getItem();
    if (!(stack.getItem() instanceof Scroll)) return SpellData.EMPTY;
    var container = ISpellContainer.get(stack);
    return container == null || container.getMaxSpellCount() == 0
        ? SpellData.EMPTY
        : container.getSpellAtIndex(0);
  }

  private String studyStatus() {
    if (!(menu.getSpellBookSlot().getItem().getItem() instanceof SpellBook)) return "need_book";
    var data = scrollSpell();
    if (data == SpellData.EMPTY) return "need_scroll";
    var entry = AbilityClient.entry(data.getSpell().getSpellId());
    if (entry == null) return "waiting";
    if (entry.reason().equals("disabled")) return "disabled";
    String rankReason = SpellProgression.studyReason(entry.level(), data.getLevel(), entry.cap());
    if (!rankReason.isEmpty())
      return rankReason.equals("unlearned") ? "rank_unlearned" : rankReason;
    return entry.reason().equals("discovery") ? "discovery" : "ready";
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    AbilityWidgets.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    AbilityWidgets.panel(graphics, leftPos + 176, topPos + 16, 74, 144);
    for (int index = 0; index < menu.slots.size() - 1; index++) {
      var slot = menu.slots.get(index);
      graphics.fill(
          leftPos + slot.x - 1,
          topPos + slot.y - 1,
          leftPos + slot.x + 17,
          topPos + slot.y + 17,
          0xff627b96);
      graphics.fill(
          leftPos + slot.x,
          topPos + slot.y,
          leftPos + slot.x + 16,
          topPos + slot.y + 16,
          0xff111b29);
    }
    var data = scrollSpell();
    String state = studyStatus();
    study.active = state.equals("ready");
    if (!state.equals(studyState)) {
      studyState = state;
      study.setTooltip(
          Tooltip.create(
              AbilityClient.text("study." + state)
                  .copy()
                  .append("\n\n")
                  .append(AbilityClient.text("study.rank_rules"))));
    }
    if (data != SpellData.EMPTY) {
      var lines = font.split(data.getSpell().getDisplayName(minecraft.player), 118);
      if (!lines.isEmpty())
        graphics.drawString(
            font, lines.getFirst(), leftPos + 44, topPos + 21, AbilityWidgets.GOLD, false);
      graphics.drawString(
          font,
          AbilityClient.text("study.scroll_rank", data.getLevel()),
          leftPos + 44,
          topPos + 35,
          AbilityWidgets.TEXT,
          false);
    } else
      graphics.drawWordWrap(
          font,
          AbilityClient.text("study.inputs"),
          leftPos + 44,
          topPos + 22,
          118,
          AbilityWidgets.MUTED);
    graphics.drawWordWrap(
        font,
        AbilityClient.text("study.routes"),
        leftPos + 183,
        topPos + 24,
        60,
        AbilityWidgets.TEXT);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, title, 8, 6, AbilityWidgets.GOLD, false);
    graphics.drawString(font, playerInventoryTitle, 8, 74, AbilityWidgets.MUTED, false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    if (repeats != null) {
      super.render(graphics, -1, -1, partialTick);
      graphics.flush();
      repeats.render(graphics, mouseX, mouseY, partialTick);
      return;
    }
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
    if (mouseX >= leftPos + 176
        && mouseX < leftPos + 250
        && mouseY >= topPos + 16
        && mouseY < topPos + 128) {
      var tooltip = new ArrayList<Component>();
      tooltip.add(AbilityClient.text("study." + studyStatus()));
      tooltip.add(AbilityClient.text("study.rank_rules"));
      var data = scrollSpell();
      var entry =
          data == SpellData.EMPTY ? null : AbilityClient.entry(data.getSpell().getSpellId());
      if (entry != null)
        tooltip.add(AbilityClient.text("rank", entry.level(), entry.maxLevel(), entry.cap()));
      graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (repeats == null) return super.mouseClicked(x, y, button);
    repeatPointer = true;
    repeats.mouseClicked(x, y, button);
    return true;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (!repeatPointer && repeats == null) return super.mouseReleased(x, y, button);
    repeatPointer = false;
    if (repeats != null) repeats.mouseReleased(x, y, button);
    return true;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return repeats != null || super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (repeats == null) return super.mouseScrolled(x, y, horizontal, vertical);
    repeats.mouseScrolled(x, y, horizontal, vertical);
    return true;
  }

  @Override
  public boolean keyPressed(int key, int scanCode, int modifiers) {
    if (repeats == null) return super.keyPressed(key, scanCode, modifiers);
    if (minecraft.options.keyInventory.matches(key, scanCode)) repeats.onClose();
    else repeats.keyPressed(key, scanCode, modifiers);
    return true;
  }

  @Override
  protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
    if (slotId != 38 && (slot == null || slot.index != 38))
      super.slotClicked(slot, slotId, mouseButton, type);
  }

  @Override
  protected void renderSlot(GuiGraphics graphics, Slot slot) {
    if (slot.index != 38) super.renderSlot(graphics, slot);
  }

  @Override
  protected void renderSlotHighlight(
      GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
    if (slot.index != 38) super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
  }

  private final class RepeatStudy extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int READY = 0xffc5fa77;
    private Button back;
    private Button learn;
    private int firstRow;
    private String selected = "";
    private AbilityNetwork.Snapshot submitted;

    RepeatStudy() {
      super(AbilityClient.text("study.repeat.title"));
    }

    @Override
    protected void init() {
      back =
          addRenderableWidget(
              Button.builder(Component.translatable("gui.back"), button -> onClose())
                  .bounds(leftPos + 42, topPos + 103, 65, 20)
                  .build());
      learn =
          addRenderableWidget(
              Button.builder(AbilityClient.text("study.repeat.learn"), button -> submit())
                  .bounds(leftPos + 112, topPos + 103, 102, 20)
                  .build());
    }

    private int rowCount(List<AbilityNetwork.StudyEntry> entries) {
      return entries.size() > 1 ? 2 : 1;
    }

    private int panelTop(List<AbilityNetwork.StudyEntry> entries) {
      return topPos + 29 - (rowCount(entries) - 1) * ROW_HEIGHT / 2;
    }

    private int listTop(List<AbilityNetwork.StudyEntry> entries) {
      return panelTop(entries) + 29;
    }

    private AbilityNetwork.StudyEntry current(List<AbilityNetwork.StudyEntry> entries) {
      return entries.stream()
          .filter(entry -> entry.spellId().equals(selected))
          .findFirst()
          .orElse(entries.isEmpty() ? null : entries.getFirst());
    }

    private boolean canLearn(AbilityNetwork.StudyEntry entry) {
      return entry != null
          && entry.reason().isEmpty()
          && !AbilityClient.studyKnown(entry)
          && menu.getSpellBookSlot().getItem().getItem() instanceof SpellBook
          && submitted != AbilityClient.snapshot();
    }

    private void submit() {
      var entry = current(AbilityClient.studyEntries());
      if (canLearn(entry)) {
        submitted = AbilityClient.snapshot();
        PacketDistributor.sendToServer(
            new AbilityNetwork.FirstStudy(menu.containerId, entry.spellId()));
      }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.renderBackground(graphics, mouseX, mouseY, partialTick);
      var entries = AbilityClient.studyEntries();
      var active = current(entries);
      int top = panelTop(entries);
      int rows = rowCount(entries);
      int listTop = listTop(entries);
      int left = leftPos + 42;
      int right = leftPos + 214;
      firstRow = Math.clamp(firstRow, 0, Math.max(0, entries.size() - rows));
      graphics.fill(0, 0, width, height, 0x770b121d);
      AbilityWidgets.panel(graphics, leftPos + 33, top, 190, 105 + (rows - 1) * ROW_HEIGHT);
      graphics.drawString(font, title, left, top + 10, AbilityWidgets.GOLD, false);
      if (entries.isEmpty())
        graphics.drawWordWrap(
            font, AbilityClient.text("study.waiting"), left, listTop, 172, AbilityWidgets.MUTED);
      for (int index = firstRow; index < Math.min(entries.size(), firstRow + rows); index++) {
        var entry = entries.get(index);
        int y = listTop + (index - firstRow) * ROW_HEIGHT;
        graphics.fill(left, y, right, y + ROW_HEIGHT - 2, 0xff111b29);
        if (entry.equals(active))
          graphics.renderOutline(left, y, 172, ROW_HEIGHT - 2, AbilityWidgets.GOLD);
        AbilityClient.icon(graphics, entry.spellId(), left + 5, y + 5, 16);
        var name = AbilityClient.name(entry.spellId());
        graphics.drawString(
            font,
            font.plainSubstrByWidth(name.getString(), 141),
            left + 25,
            y + 3,
            AbilityWidgets.TEXT,
            false);
        graphics.drawString(
            font,
            AbilityClient.studyProgress(entry),
            left + 25,
            y + 15,
            entry.reason().isEmpty() || AbilityClient.studyKnown(entry)
                ? READY
                : AbilityWidgets.MUTED,
            false);
      }
      if (entries.size() > rows) {
        int height = rows * ROW_HEIGHT - 2;
        int thumb = Math.max(4, height * rows / entries.size());
        int thumbTop = listTop + firstRow * (height - thumb) / (entries.size() - rows);
        graphics.fill(right - 3, listTop, right - 1, listTop + height, 0xff3d536d);
        graphics.fill(right - 3, thumbTop, right - 1, thumbTop + thumb, AbilityWidgets.MUTED);
      }
      int footer = top + 74 + (rows - 1) * ROW_HEIGHT;
      back.setY(footer);
      learn.setY(footer);
      learn.active = canLearn(active);
      if (active != null)
        learn.setTooltip(
            Tooltip.create(
                menu.getSpellBookSlot().getItem().getItem() instanceof SpellBook
                    ? AbilityClient.text("study.repeat.book_cost")
                    : AbilityClient.text("study.need_book")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      super.render(graphics, mouseX, mouseY, partialTick);
      var entries = AbilityClient.studyEntries();
      int hovered = rowAt(entries, mouseX, mouseY);
      if (hovered >= 0) {
        var entry = entries.get(hovered);
        var hint = new ArrayList<Component>();
        hint.add(AbilityClient.name(entry.spellId()));
        if (!AbilityClient.studyKnown(entry)) {
          if (entry.requiredMastery() > 0) hint.add(AbilityClient.studyMastery(entry));
          if (!entry.reason().isEmpty()) hint.add(AbilityClient.reason(entry.reason()));
        }
        graphics.renderTooltip(
            font,
            hint.stream().flatMap(line -> font.split(line, 190).stream()).toList(),
            mouseX,
            mouseY);
      }
    }

    private int rowAt(List<AbilityNetwork.StudyEntry> entries, double x, double y) {
      int top = listTop(entries);
      if (x < leftPos + 42 || x >= leftPos + 214 || y < top) return -1;
      int row = (int) (y - top) / ROW_HEIGHT;
      int index = firstRow + row;
      return row < rowCount(entries) && index < entries.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
      if (super.mouseClicked(x, y, button)) return true;
      var entries = AbilityClient.studyEntries();
      int index = rowAt(entries, x, y);
      if (index < 0 || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
      selected = entries.get(index).spellId();
      return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      var entries = AbilityClient.studyEntries();
      firstRow =
          Math.clamp(
              firstRow - (int) Math.signum(vertical),
              0,
              Math.max(0, entries.size() - rowCount(entries)));
      return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
      var entries = AbilityClient.studyEntries();
      if (!entries.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
        int next =
            Math.clamp(
                entries.indexOf(current(entries)) + (key == GLFW.GLFW_KEY_UP ? -1 : 1),
                0,
                entries.size() - 1);
        selected = entries.get(next).spellId();
        firstRow = Math.clamp(firstRow, Math.max(0, next + 1 - rowCount(entries)), next);
        return true;
      }
      return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
      repeats = null;
    }
  }
}
