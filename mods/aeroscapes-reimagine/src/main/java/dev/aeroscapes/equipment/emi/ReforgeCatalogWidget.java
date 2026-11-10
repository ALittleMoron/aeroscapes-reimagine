package dev.aeroscapes.equipment.emi;

import com.herobrot.tieredneo.api.ModifierUtils;
import dev.aeroscapes.equipment.ReforgeProfiles.Outcome;
import dev.aeroscapes.equipment.ReforgeProfiles.PaidGrade;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

final class ReforgeCatalogWidget extends Widget {
  static final int WIDTH = 238;
  static final int HEIGHT = 107;
  private static final String KEY = "aeroscapes_reimagine.equipment.reforge.";
  private static final ResourceLocation BUTTON =
      ResourceLocation.withDefaultNamespace("widget/button");
  private static final ResourceLocation HOVERED_BUTTON =
      ResourceLocation.withDefaultNamespace("widget/button_highlighted");
  private static final ResourceLocation DISABLED_BUTTON =
      ResourceLocation.withDefaultNamespace("widget/button_disabled");
  private static final int HEADER_Y = 22;
  private static final int ROWS_Y = 34;
  private static final int GRADE_WIDTH = 103;
  private static final int GRADE_HEIGHT = 12;
  private static final int PREVIEW_X = 111;
  private static final int PREVIEW_HEIGHT = 19;
  private static final int PREVIEW_NAME_WIDTH = WIDTH - PREVIEW_X - 26;
  private static final int PAGE_SIZE = 3;
  private static final int PAGE_Y = ROWS_Y + PREVIEW_HEIGHT * PAGE_SIZE + 1;
  private static final int PAGE_BUTTON_WIDTH = 19;
  private static final Bounds PREVIOUS =
      new Bounds(PREVIEW_X, PAGE_Y, PAGE_BUTTON_WIDTH, GRADE_HEIGHT);
  private static final Bounds NEXT =
      new Bounds(WIDTH - PAGE_BUTTON_WIDTH, PAGE_Y, PAGE_BUTTON_WIDTH, GRADE_HEIGHT);
  private static final Bounds BOUNDS = new Bounds(0, HEADER_Y, WIDTH, HEIGHT - HEADER_Y);
  private static final int TEXT = 0xFFFFFFFF;
  private static final int SELECTED = 0xFFFFDA85;

  private final List<GradeOption> grades;
  private int gradeIndex;
  private int page;

  ReforgeCatalogWidget(Item item, List<PaidGrade> paidGrades) {
    var locale = Minecraft.getInstance().getLanguageManager().getSelected().replace('_', '-');
    var percent = NumberFormat.getPercentInstance(Locale.forLanguageTag(locale));
    percent.setMaximumFractionDigits(1);
    int totalWeight = paidGrades.stream().mapToInt(PaidGrade::weight).sum();
    grades =
        paidGrades.stream()
            .map(
                grade -> {
                  String name = Component.translatable(KEY + "grade." + grade.key()).getString();
                  return new GradeOption(
                      name,
                      percent.format((double) grade.weight() / totalWeight),
                      grade.outcomes().stream()
                          .map(outcome -> preview(item, name, outcome))
                          .toList());
                })
            .toList();
  }

  private static Preview preview(Item item, String grade, Outcome outcome) {
    var stack = item.getDefaultInstance();
    ModifierUtils.applyTierToStack(stack, outcome.id(), outcome.profile());
    var catalogKey = KEY + "outcome." + outcome.id().getPath();
    String label =
        Language.getInstance().has(catalogKey)
            ? Component.translatable(catalogKey).getString()
            : Component.translatable(outcome.id() + ".label").getString();
    if (label.endsWith(" ·")) label = label.substring(0, label.length() - 2);
    String prefix = grade + " · ";
    if (label.startsWith(prefix)) label = label.substring(prefix.length());
    var lines = Minecraft.getInstance().font.split(Component.literal(label), PREVIEW_NAME_WIDTH);
    return new Preview(label, lines, EmiStack.of(stack));
  }

  @Override
  public Bounds getBounds() {
    return BOUNDS;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    var font = Minecraft.getInstance().font;
    graphics.fill(0, HEADER_Y, GRADE_WIDTH, HEADER_Y + 10, 0xFF45413E);
    graphics.fill(PREVIEW_X, HEADER_Y, WIDTH, HEADER_Y + 10, 0xFF45413E);
    graphics.drawString(font, Component.translatable(KEY + "grade_odds"), 3, HEADER_Y + 1, TEXT);
    graphics.drawString(
        font, Component.translatable(KEY + "outcomes"), PREVIEW_X + 3, HEADER_Y + 1, TEXT);

    for (int index = 0; index < grades.size(); index++) {
      var grade = grades.get(index);
      var bounds = gradeBounds(index);
      boolean selected = index == gradeIndex;
      drawButton(graphics, bounds, true, selected || bounds.contains(mouseX, mouseY));
      int color = selected ? SELECTED : TEXT;
      int chanceX = bounds.right() - font.width(grade.chance()) - 4;
      drawLabel(graphics, font, grade.name(), 4, bounds.y() + 2, chanceX - 7, color);
      graphics.drawString(font, grade.chance(), chanceX, bounds.y() + 2, color);
    }

    var previews = visiblePreviews();
    for (int index = 0; index < previews.size(); index++) {
      var preview = previews.get(index);
      var bounds = previewBounds(index);
      int background = bounds.contains(mouseX, mouseY) ? 0xFF56504A : 0xFF45413E;
      graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), background);
      EmiTexture.SLOT.render(graphics, bounds.x(), bounds.y(), delta);
      preview
          .stack()
          .render(graphics, bounds.x() + 1, bounds.y() + 1, delta, EmiIngredient.RENDER_ICON);
      int labelX = bounds.x() + 22;
      var lines = preview.lines();
      if (!lines.isEmpty()) {
        int labelY = bounds.y() + (lines.size() == 1 ? 5 : 1);
        graphics.drawString(font, lines.getFirst(), labelX, labelY, TEXT);
        if (lines.size() > 1) {
          graphics.drawString(font, lines.get(1), labelX, labelY + 9, TEXT);
        }
      }
    }

    if (pageCount() > 1) {
      graphics.fill(PREVIOUS.right(), PAGE_Y + 1, NEXT.x(), PAGE_Y + GRADE_HEIGHT - 1, 0xFF45413E);
      drawButton(graphics, PREVIOUS, page > 0, PREVIOUS.contains(mouseX, mouseY));
      drawButton(graphics, NEXT, page + 1 < pageCount(), NEXT.contains(mouseX, mouseY));
      drawCentered(graphics, font, "‹", PREVIOUS.x() + PAGE_BUTTON_WIDTH / 2, PAGE_Y + 2, TEXT);
      drawCentered(graphics, font, "›", NEXT.x() + PAGE_BUTTON_WIDTH / 2, PAGE_Y + 2, TEXT);
      drawCentered(
          graphics,
          font,
          (page + 1) + " / " + pageCount(),
          (PREVIEW_X + WIDTH) / 2,
          PAGE_Y + 2,
          TEXT);
    }
  }

  @Override
  public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
    if (mouseX < GRADE_WIDTH && mouseY < ROWS_Y + GRADE_HEIGHT * grades.size()) {
      return hint("select_grade");
    }
    var previews = visiblePreviews();
    for (int index = 0; index < previews.size(); index++) {
      if (previewBounds(index).contains(mouseX, mouseY)) {
        var preview = previews.get(index);
        var tooltip = new ArrayList<ClientTooltipComponent>();
        tooltip.add(EmiTooltipComponents.of(Component.literal(preview.name())));
        tooltip.addAll(preview.stack().getTooltip());
        return tooltip;
      }
    }
    if (pageCount() > 1) {
      if (PREVIOUS.contains(mouseX, mouseY)) return hint("previous_page");
      if (NEXT.contains(mouseX, mouseY)) return hint("next_page");
    }
    return List.of();
  }

  @Override
  public boolean mouseClicked(int mouseX, int mouseY, int button) {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
    for (int index = 0; index < grades.size(); index++) {
      if (gradeBounds(index).contains(mouseX, mouseY)) {
        selectGrade(index);
        return true;
      }
    }
    if (pageCount() > 1 && (PREVIOUS.contains(mouseX, mouseY) || NEXT.contains(mouseX, mouseY))) {
      selectPage(page + (PREVIOUS.contains(mouseX, mouseY) ? -1 : 1));
      return true;
    }
    return false;
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (modifiers != 0) return false;
    return switch (keyCode) {
      case GLFW.GLFW_KEY_UP -> selectGrade(gradeIndex - 1);
      case GLFW.GLFW_KEY_DOWN -> selectGrade(gradeIndex + 1);
      case GLFW.GLFW_KEY_LEFT -> pageCount() > 1 && selectPage(page - 1);
      case GLFW.GLFW_KEY_RIGHT -> pageCount() > 1 && selectPage(page + 1);
      default -> false;
    };
  }

  private boolean selectGrade(int index) {
    if (index < 0 || index >= grades.size() || index == gradeIndex) return false;
    gradeIndex = index;
    page = 0;
    playClick();
    return true;
  }

  private boolean selectPage(int index) {
    if (index < 0 || index >= pageCount() || index == page) return false;
    page = index;
    playClick();
    return true;
  }

  private int pageCount() {
    return Math.ceilDiv(grades.get(gradeIndex).previews().size(), PAGE_SIZE);
  }

  private List<Preview> visiblePreviews() {
    var previews = grades.get(gradeIndex).previews();
    int start = page * PAGE_SIZE;
    return previews.subList(start, Math.min(start + PAGE_SIZE, previews.size()));
  }

  private static Bounds gradeBounds(int index) {
    return new Bounds(0, ROWS_Y + GRADE_HEIGHT * index, GRADE_WIDTH, GRADE_HEIGHT);
  }

  private static Bounds previewBounds(int index) {
    return new Bounds(PREVIEW_X, ROWS_Y + PREVIEW_HEIGHT * index, WIDTH - PREVIEW_X, 18);
  }

  private static void drawButton(
      GuiGraphics graphics, Bounds bounds, boolean active, boolean hovered) {
    var sprite = active ? (hovered ? HOVERED_BUTTON : BUTTON) : DISABLED_BUTTON;
    graphics.blitSprite(sprite, bounds.x(), bounds.y(), bounds.width(), bounds.height());
  }

  private static void drawLabel(
      GuiGraphics graphics, Font font, String label, int x, int y, int width, int color) {
    if (font.width(label) > width) {
      label = font.plainSubstrByWidth(label, width - font.width("…")) + "…";
    }
    graphics.drawString(font, label, x, y, color);
  }

  private static void drawCentered(
      GuiGraphics graphics, Font font, String label, int x, int y, int color) {
    graphics.drawString(font, label, x - font.width(label) / 2, y, color, false);
  }

  private static List<ClientTooltipComponent> hint(String key) {
    return List.of(EmiTooltipComponents.of(Component.translatable(KEY + key)));
  }

  private static void playClick() {
    Minecraft.getInstance()
        .getSoundManager()
        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
  }

  private record GradeOption(String name, String chance, List<Preview> previews) {}

  private record Preview(String name, List<FormattedCharSequence> lines, EmiStack stack) {}
}
