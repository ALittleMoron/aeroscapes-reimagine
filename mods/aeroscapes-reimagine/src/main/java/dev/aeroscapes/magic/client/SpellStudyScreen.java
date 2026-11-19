package dev.aeroscapes.magic.client;

import dev.aeroscapes.magic.SpellProgression;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Native menu slots remain authoritative; the button requests personal study. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SpellStudyScreen extends AbstractContainerScreen<InscriptionTableMenu> {
  private Button study;
  private String studyState = "";

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
    study.setTooltip(Tooltip.create(AbilityClient.text("study.rules")));
    study.active = false;
    studyState = "";
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
    if (!rankReason.isEmpty()) return rankReason;
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
                  .append(AbilityClient.text("study.rules"))));
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
        AbilityClient.text("study.explanation"),
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
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
    if (mouseX >= leftPos + 176
        && mouseX < leftPos + 250
        && mouseY >= topPos + 16
        && mouseY < topPos + 160) {
      var tooltip = new ArrayList<Component>();
      tooltip.add(AbilityClient.text("study." + studyStatus()));
      tooltip.add(AbilityClient.text("study.rules"));
      var data = scrollSpell();
      var entry =
          data == SpellData.EMPTY ? null : AbilityClient.entry(data.getSpell().getSpellId());
      if (entry != null)
        tooltip.add(AbilityClient.text("rank", entry.level(), entry.maxLevel(), entry.cap()));
      graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }
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
}
