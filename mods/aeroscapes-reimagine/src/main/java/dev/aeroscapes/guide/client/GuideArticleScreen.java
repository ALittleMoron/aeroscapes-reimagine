package dev.aeroscapes.guide.client;

import com.klikli_dev.modonomicon.book.entries.BookContentEntry;
import com.klikli_dev.modonomicon.client.gui.BookGuiManager;
import com.klikli_dev.modonomicon.client.gui.book.entry.BookEntryDoublePageScreen;
import dev.aeroscapes.magic.client.AbilityClient;
import dev.aeroscapes.progression.client.ScrollViewport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class GuideArticleScreen extends BookEntryDoublePageScreen {
  private static final int STUDY_ROW_HEIGHT = 52;
  private final ScrollViewport studyScroll = new ScrollViewport();

  public GuideArticleScreen(FieldGuideScreen parent, BookContentEntry entry) {
    super(parent, entry);
  }

  @Override
  protected void init() {
    super.init();
    if (firstStudy()) AbilityClient.request();
  }

  private boolean firstStudy() {
    return getEntry().getId().getPath().equals("magic/first_study");
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    if (!firstStudy() || openPagesIndex != 0) return;
    int left = bookLeft + RIGHT_PAGE_X;
    int top = bookTop + TOP_PADDING;
    int right = left + PAGE_WIDTH;
    int listTop = top + 15;
    int bottom = top + PAGE_HEIGHT;
    var entries = AbilityClient.studyEntries();
    var guideFont = getFont();
    graphics.drawString(
        guideFont,
        Component.translatable("book.aeroscapes_reimagine.field_guide.magic.progress"),
        left,
        top,
        0xff162331,
        false);
    studyScroll.resize(entries.size() * STUDY_ROW_HEIGHT, bottom - listTop);
    graphics.enableScissor(left, listTop, right, bottom);
    if (entries.isEmpty()) {
      graphics.drawWordWrap(
          guideFont, AbilityClient.text("study.waiting"), left, listTop, PAGE_WIDTH, 0xff35444b);
    }
    int offset = (int) studyScroll.offset();
    for (int index = offset / STUDY_ROW_HEIGHT; index < entries.size(); index++) {
      int y = listTop + index * STUDY_ROW_HEIGHT - offset;
      if (y >= bottom) break;
      var entry = entries.get(index);
      var name = AbilityClient.name(entry.spellId());
      graphics.drawString(
          guideFont,
          guideFont.plainSubstrByWidth(name.getString(), PAGE_WIDTH - 4),
          left,
          y,
          0xff162331,
          false);
      graphics.drawString(
          guideFont,
          AbilityClient.studyProgress(entry),
          left,
          y + 10,
          AbilityClient.studyKnown(entry) || entry.reason().isEmpty() ? 0xff305c27 : 0xff35444b,
          false);
      if (!AbilityClient.studyKnown(entry) && entry.requiredMastery() > 0)
        graphics.drawWordWrap(
            guideFont, AbilityClient.studyMastery(entry), left, y + 20, PAGE_WIDTH - 4, 0xff35444b);
    }
    graphics.disableScissor();
    if (studyScroll.maximum() > 0) {
      graphics.fill(right - 2, listTop, right, bottom, 0xffb7aa86);
      int thumb = listTop + studyScroll.thumbPosition();
      graphics.fill(right - 2, thumb, right, thumb + studyScroll.thumbSize(), 0xff635c4b);
    }
    if (mouseX >= left && mouseX < right && mouseY >= listTop && mouseY < bottom) {
      int index = (mouseY - listTop + offset) / STUDY_ROW_HEIGHT;
      if (index < entries.size()) {
        var entry = entries.get(index);
        if (!entry.reason().isEmpty() && !AbilityClient.studyKnown(entry))
          graphics.renderComponentTooltip(
              guideFont,
              java.util.List.of(
                  AbilityClient.name(entry.spellId()), AbilityClient.reason(entry.reason())),
              mouseX,
              mouseY);
      }
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    int left = bookLeft + RIGHT_PAGE_X;
    int top = bookTop + TOP_PADDING;
    if (firstStudy()
        && openPagesIndex == 0
        && x >= left
        && x < left + PAGE_WIDTH
        && y >= top + 15
        && y < top + PAGE_HEIGHT) {
      studyScroll.wheel(vertical);
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    // The parent already draws the Atlas backdrop and chapter frame.
  }

  boolean containsControlOrPage(double x, double y) {
    return (x >= bookLeft && x < bookLeft + FULL_WIDTH && y >= bookTop && y < bookTop + FULL_HEIGHT)
        || isOverControl(x, y);
  }

  private boolean isOverControl(double x, double y) {
    return children().stream().anyMatch(widget -> widget.isMouseOver(x, y));
  }

  @Override
  protected boolean isClickOutsideEntry(double x, double y) {
    return !FieldGuideScreen.isWelcome(getEntry().getCategory())
        && !isOverControl(x, y)
        && super.isClickOutsideEntry(x, y);
  }

  @Override
  public void onClose() {
    if (FieldGuideScreen.isWelcome(getEntry().getCategory())) {
      BookGuiManager.get().closeScreenStack(this);
    } else {
      super.onClose();
    }
  }
}
