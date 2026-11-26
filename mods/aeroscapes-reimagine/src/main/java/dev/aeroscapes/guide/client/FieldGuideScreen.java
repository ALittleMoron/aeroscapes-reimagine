package dev.aeroscapes.guide.client;

import com.klikli_dev.modonomicon.book.Book;
import com.klikli_dev.modonomicon.book.BookCategory;
import com.klikli_dev.modonomicon.client.gui.BookGuiManager;
import com.klikli_dev.modonomicon.client.gui.book.button.CategoryButton;
import com.klikli_dev.modonomicon.client.gui.book.entry.BookEntryScreen;
import com.klikli_dev.modonomicon.client.gui.book.node.BookParentNodeScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public final class FieldGuideScreen extends BookParentNodeScreen {
  private GuideArticleScreen article;

  public FieldGuideScreen(Book book) {
    super(book);
  }

  public static boolean isWelcome(BookCategory chapter) {
    return "welcome".equals(chapter.getId().getPath());
  }

  public boolean isReading() {
    return article != null;
  }

  public void showArticle(GuideArticleScreen next) {
    article = next;
    next.init(minecraft, width, height);
  }

  public void hideArticle(BookEntryScreen closed) {
    if (article == closed) article = null;
  }

  @Override
  protected void init() {
    super.init();
    if (article != null) article.resize(minecraft, width, height);
  }

  @Override
  protected void onBookCategoryButtonClick(CategoryButton button) {
    if (article != null) BookGuiManager.get().closeEntryScreen(article);
    super.onBookCategoryButtonClick(button);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    if (article != null) {
      graphics.flush();
      article.render(graphics, mouseX, mouseY, partialTick);
    }
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (article != null && article.containsControlOrPage(x, y)) {
      return article.mouseClicked(x, y, button);
    }
    if (getCurrentCategoryScreen() instanceof FieldGuideChapter chapter
        && chapter.mouseClicked(x, y, button)) return true;
    if (super.mouseClicked(x, y, button)) return true;
    return article != null && article.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (article != null) {
      setDragging(false);
      return article.mouseReleased(x, y, button);
    }
    if (getCurrentCategoryScreen() instanceof FieldGuideChapter chapter
        && chapter.release(x, y, button)) return true;
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (article != null) return article.mouseScrolled(x, y, horizontal, vertical);
    if (getCurrentCategoryScreen() instanceof FieldGuideChapter chapter) {
      return chapter.zoomAt(x, y, vertical);
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public boolean keyPressed(int key, int scanCode, int modifiers) {
    if (article != null) {
      if (key == GLFW.GLFW_KEY_ESCAPE) {
        article.onClose();
        return true;
      }
      if (article.keyPressed(key, scanCode, modifiers)) return true;
    }
    return super.keyPressed(key, scanCode, modifiers);
  }

  @Override
  public void tick() {
    super.tick();
    if (article != null) article.tick();
  }
}
