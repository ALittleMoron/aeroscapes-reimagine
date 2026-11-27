package dev.aeroscapes.progression.client;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.client.TalentClient.ClassTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.client.data.ClientSkillScreenData;

final class PointOverviewScreen extends Screen {
  private static final int GOLD = 0xffe9c084;
  private static final int TEXT = 0xffd6e0eb;
  private static final int MUTED = 0xff92a9c3;
  private static final int DEFICIT = 0xffe9a498;
  private static final int LINE = 0xff627b96;
  private static final int ROW_HEIGHT = 11;
  private static final int FIRST_ROW = 66;
  private final TalentScreen parent;
  private final ClientSkillScreenData data;

  PointOverviewScreen(TalentScreen parent, ClientSkillScreenData data) {
    super(TalentScreen.text("point_overview"));
    this.parent = parent;
    this.data = data;
  }

  @Override
  protected void init() {
    addRenderableWidget(
        Button.builder(Component.literal("×"), button -> onClose())
            .bounds(width - 33, 13, 17, 18)
            .build());
    if (width >= 320 && height >= 220) {
      addRenderableWidget(
          Button.builder(Component.translatable("gui.back"), button -> onClose())
              .bounds(width - 92, height - 36, 72, 20)
              .build());
    }
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.renderBackground(graphics, mouseX, mouseY, partialTick);
    AtlasProgressionWidgets.panel(graphics, 8, 8, width - 16, height - 16);
    graphics.drawString(font, title, 20, 19, GOLD, false);
    if (width < 320 || height < 220) {
      graphics.drawWordWrap(
          font, TalentScreen.text("small_window"), 20, 60, Math.max(30, width - 40), TEXT);
      return;
    }

    int classRight = 20 + (width - 44) / 2 - 9;
    int professionLeft = classRight + 18;
    int professionRight = width - 20;
    drawHeading(graphics, TalentScreen.text("page.classes"), 20, classRight);
    drawHeading(graphics, TalentScreen.text("page.profession"), professionLeft, professionRight);
    for (var tree : ClassTree.values()) {
      drawBalance(
          graphics,
          tree.title(),
          tree.category(),
          20,
          classRight,
          FIRST_ROW + tree.ordinal() * ROW_HEIGHT);
    }

    int y = FIRST_ROW;
    for (var section : TalentScreen.PROFESSION_SECTIONS) {
      for (var tree : ProfessionDiscipline.values()) {
        if (tree.section() != section) continue;
        drawBalance(
            graphics,
            TalentScreen.text("page." + tree.skill() + "_short"),
            tree.category(),
            professionLeft + (tree.toolHandling() ? 9 : 0),
            professionRight,
            y);
        y += ROW_HEIGHT;
      }
    }
  }

  private void drawHeading(GuiGraphics graphics, Component label, int left, int right) {
    graphics.drawString(font, label, left, 48, GOLD, false);
    graphics.fill(left, 60, right, 61, LINE);
  }

  private void drawBalance(
      GuiGraphics graphics,
      Component label,
      ResourceLocation category,
      int left,
      int right,
      int y) {
    var balance = data.getCategory(category).map(TalentClient::pointBalance);
    int color = balance.map(PointOverviewScreen::balanceColor).orElse(MUTED);
    var value = balance.map(String::valueOf).orElse("…");
    graphics.drawString(font, label, left, y, color, false);
    graphics.drawString(font, value, right - font.width(value), y, color, false);
  }

  static int balanceColor(int balance) {
    return balance < 0 ? DEFICIT : balance == 0 ? MUTED : TEXT;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }

  @Override
  public boolean isPauseScreen() {
    return parent.isPauseScreen();
  }
}
