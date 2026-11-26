package dev.aeroscapes.guide.client;

import com.klikli_dev.modonomicon.book.BookCategory;
import com.klikli_dev.modonomicon.book.BookEntryParent;
import com.klikli_dev.modonomicon.book.conditions.BookOrCondition;
import com.klikli_dev.modonomicon.book.entries.BookEntry;
import com.klikli_dev.modonomicon.bookstate.BookUnlockStateManager;
import com.klikli_dev.modonomicon.client.gui.BookGuiManager;
import com.klikli_dev.modonomicon.client.gui.book.BookAddress;
import com.klikli_dev.modonomicon.client.gui.book.node.BookCategoryNodeScreen;
import dev.aeroscapes.interfaceart.InterfaceArt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class FieldGuideChapter extends BookCategoryNodeScreen {
  private static final String TEXT = "book.aeroscapes_reimagine.field_guide.ui.";
  private static final int GOLD = 0xffe9c084;
  private static final int BORDER = 0xff627b96;
  private static final int PANEL = 0xff1b2b3d;
  private static final int SHADOW = 0xff111b29;
  private static final int HEADER = 26;
  static final int NODE_SIZE = 26;
  private static final int MAP_UNIT = 10;
  private static final int GUTTER = 8;

  private final FieldGuideScreen parent;
  private final List<BookEntry> entries;
  private final GuideViewport viewport;
  private final GuidePointer<BookEntry> pointer = new GuidePointer<>();

  public FieldGuideChapter(FieldGuideScreen parent, BookCategory category) {
    super(parent, category);
    this.parent = parent;
    entries = List.copyOf(category.getEntries().values());
    int left = entries.stream().mapToInt(FieldGuideChapter::nodeX).min().orElse(0);
    int right = entries.stream().mapToInt(FieldGuideChapter::nodeX).max().orElse(0);
    int bottom = entries.stream().mapToInt(FieldGuideChapter::nodeY).max().orElse(0);
    viewport =
        new GuideViewport(left - GUTTER, right + NODE_SIZE + GUTTER, bottom + NODE_SIZE + GUTTER);
  }

  @Override
  public void renderBackground(GuiGraphics graphics) {
    int left = parent.getInnerX();
    int top = parent.getInnerY();
    int width = parent.getInnerWidth();
    int height = parent.getInnerHeight();
    graphics.fill(left, top, left + width, top + height, SHADOW);
    if (InterfaceArt.available(InterfaceArt.ATLAS)) {
      graphics.enableScissor(left, top, left + width, top + height);
      graphics.pose().pushPose();
      graphics.pose().translate(left, top, 0);
      InterfaceArt.drawAtlas(graphics, width, height, 1);
      graphics.pose().popPose();
      graphics.disableScissor();
    }
    graphics.fill(left, top, left + width, top + HEADER, SHADOW);
    graphics.hLine(left, left + width - 1, top + HEADER - 1, BORDER);
    graphics.drawCenteredString(
        Minecraft.getInstance().font,
        Component.translatable(getCategory().getName()),
        left + width / 2,
        top + 9,
        GOLD);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    if (!showsMap()) return;
    if (pointer.isDragging()
        && GLFW.glfwGetMouseButton(
                Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
            == GLFW.GLFW_RELEASE) {
      pointer.cancel();
    }
    int left = parent.getInnerX();
    int top = canvasTop();
    int width = parent.getInnerWidth();
    int height = parent.getInnerHeight() - HEADER;
    if (width <= 0 || height <= 0) return;
    viewport.resize(width, height);
    BookEntry hovered = entryAt(mouseX, mouseY);
    graphics.enableScissor(left, top, left + width, top + height);
    graphics.pose().pushPose();
    graphics.pose().translate(left + viewport.x(0), top + viewport.y(0), 0);
    float scale = (float) viewport.zoom();
    graphics.pose().scale(scale, scale, 1);
    for (BookEntry entry : entries) {
      for (BookEntryParent link : entry.getParents()) {
        BookEntry previous = link.getEntry();
        if (link.isLineEnabled() && previous.getCategory() == getCategory()) {
          boolean traversable = entry.getParents().size() == 1 && ready(entry);
          boolean alternative = entry.getCondition() instanceof BookOrCondition;
          renderLink(graphics, previous, entry, traversable ? GOLD : BORDER, alternative);
        }
      }
    }
    for (BookEntry entry : entries) {
      renderNode(graphics, entry, hovered == entry, ready(entry));
    }
    graphics.pose().popPose();
    graphics.disableScissor();
  }

  private static void renderLink(
      GuiGraphics graphics, BookEntry previous, BookEntry next, int color, boolean alternative) {
    int x1 = nodeX(previous) + NODE_SIZE / 2;
    int x2 = nodeX(next) + NODE_SIZE / 2;
    int y1 = nodeY(previous) + NODE_SIZE / 2;
    int y2 = nodeY(next) + NODE_SIZE / 2;
    boolean vertical = x1 == x2;
    if (vertical || y1 == y2) {
      renderSegment(graphics, x1, y1, x2, y2, color, alternative);
    } else {
      int bend = (x1 + x2) / 2;
      renderSegment(graphics, x1, y1, bend, y1, color, alternative);
      renderSegment(graphics, bend, y1, bend, y2, color, alternative);
      renderSegment(graphics, bend, y2, x2, y2, color, alternative);
    }
    int direction = vertical ? Integer.signum(y2 - y1) : Integer.signum(x2 - x1);
    for (int wing = 0; wing <= 3; wing++) {
      int tail = direction * (NODE_SIZE / 2 + 3 + wing);
      if (vertical) {
        graphics.fill(x2 - wing, y2 - tail, x2 - wing + 1, y2 - tail + 1, color);
        graphics.fill(x2 + wing, y2 - tail, x2 + wing + 1, y2 - tail + 1, color);
      } else {
        graphics.fill(x2 - tail, y2 - wing, x2 - tail + 1, y2 - wing + 1, color);
        graphics.fill(x2 - tail, y2 + wing, x2 - tail + 1, y2 + wing + 1, color);
      }
    }
  }

  private static void renderSegment(
      GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, boolean dashed) {
    boolean vertical = x1 == x2;
    int begin = vertical ? Math.min(y1, y2) : Math.min(x1, x2);
    int end = vertical ? Math.max(y1, y2) : Math.max(x1, x2);
    int length = dashed ? 4 : end - begin + 1;
    for (int start = begin; start <= end; start += length + 2) {
      int stop = Math.min(start + length, end + 1);
      if (vertical) graphics.fill(x1, start, x1 + 1, stop, color);
      else graphics.fill(start, y1, stop, y1 + 1, color);
    }
  }

  private static void renderNode(
      GuiGraphics graphics, BookEntry entry, boolean hovered, boolean ready) {
    int x = nodeX(entry);
    int y = nodeY(entry);
    graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, hovered || ready ? GOLD : BORDER);
    graphics.fill(x + 1, y + 1, x + NODE_SIZE - 1, y + NODE_SIZE - 1, PANEL);
    graphics.fill(x + 3, y + 3, x + NODE_SIZE - 3, y + NODE_SIZE - 3, SHADOW);
    entry.getIcon().render(graphics, x + 5, y + 5);
  }

  @Override
  public void renderEntryTooltips(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    BookEntry entry = showsMap() && !pointer.isDragging() ? entryAt(mouseX, mouseY) : null;
    if (entry == null) return;
    var minecraft = Minecraft.getInstance();
    List<Component> tooltip = new ArrayList<>();
    tooltip.add(Component.translatable(entry.getName()).withStyle(ChatFormatting.GOLD));
    boolean alternative = entry.getCondition() instanceof BookOrCondition;
    if (!ready(entry) || alternative) {
      var requirements = Component.empty();
      for (BookEntryParent link : entry.getParents()) {
        if (!requirements.getSiblings().isEmpty()) {
          requirements.append(
              alternative ? Component.translatable(TEXT + "or") : Component.literal(", "));
        }
        BookEntry previous = link.getEntry();
        if (previous.getCategory() != getCategory()) {
          requirements
              .append(Component.translatable(previous.getCategory().getName()))
              .append(" / ");
        }
        requirements.append(Component.translatable(previous.getName()));
      }
      tooltip.add(
          (requirements.getSiblings().isEmpty()
                  ? Component.translatable(TEXT + "ahead")
                  : Component.translatable(
                      TEXT + (alternative ? "alternatives" : "prerequisites"), requirements))
              .withStyle(ChatFormatting.GRAY));
    }
    int maxWidth = Math.min(230, Math.max(100, graphics.guiWidth() - 34));
    List<FormattedCharSequence> lines = new ArrayList<>();
    for (Component line : tooltip) {
      lines.addAll(minecraft.font.split(line, maxWidth));
    }
    graphics.renderTooltip(minecraft.font, lines, mouseX, mouseY);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !showsMap() || !inside(x, y)) return false;
    pointer.press(x, y, entryAt(x, y));
    return true;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        && showsMap()
        && pointer.drag(x, y, dx, dy, viewport);
  }

  public boolean release(double x, double y, int button) {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !pointer.isPressed()) return false;
    BookEntry selected = pointer.release(x, y, entryAt(x, y));
    if (selected == null) return true;
    BookAddress address = BookAddress.defaultFor(selected);
    if (ready(selected)) {
      BookGuiManager.get().openEntry(selected, address);
    } else {
      // Preview the native pages without sending the first-read packet.
      selected.openEntry(address);
    }
    return true;
  }

  public boolean zoomAt(double x, double y, double delta) {
    if (!showsMap() || !inside(x, y)) return false;
    viewport.zoomAt(x - parent.getInnerX(), y - canvasTop(), delta);
    return true;
  }

  private boolean ready(BookEntry entry) {
    var player = Minecraft.getInstance().player;
    return player != null && BookUnlockStateManager.get().isUnlockedFor(player, entry);
  }

  private boolean showsMap() {
    return !parent.isReading() && !FieldGuideScreen.isWelcome(getCategory());
  }

  private boolean inside(double x, double y) {
    return x >= parent.getInnerX()
        && x < parent.getInnerX() + parent.getInnerWidth()
        && y >= canvasTop()
        && y < parent.getInnerY() + parent.getInnerHeight();
  }

  private BookEntry entryAt(double mouseX, double mouseY) {
    if (!inside(mouseX, mouseY)) return null;
    double x = mouseX - parent.getInnerX();
    double y = mouseY - canvasTop();
    for (BookEntry entry : entries) {
      int left = nodeX(entry);
      int top = nodeY(entry);
      if (viewport.hitsNode(x, y, left, top, NODE_SIZE)) return entry;
    }
    return null;
  }

  private int canvasTop() {
    return parent.getInnerY() + HEADER;
  }

  private static int nodeX(BookEntry entry) {
    return entry.getX() * MAP_UNIT;
  }

  private static int nodeY(BookEntry entry) {
    return GUTTER + entry.getY() * MAP_UNIT;
  }
}
