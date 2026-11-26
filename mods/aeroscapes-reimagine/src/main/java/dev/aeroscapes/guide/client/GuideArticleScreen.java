package dev.aeroscapes.guide.client;

import com.klikli_dev.modonomicon.book.entries.BookContentEntry;
import com.klikli_dev.modonomicon.client.gui.BookGuiManager;
import com.klikli_dev.modonomicon.client.gui.book.entry.BookEntryDoublePageScreen;
import net.minecraft.client.gui.GuiGraphics;

public final class GuideArticleScreen extends BookEntryDoublePageScreen {
  public GuideArticleScreen(FieldGuideScreen parent, BookContentEntry entry) {
    super(parent, entry);
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
