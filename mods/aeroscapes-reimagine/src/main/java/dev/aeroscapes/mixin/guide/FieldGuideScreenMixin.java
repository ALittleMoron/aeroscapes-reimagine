package dev.aeroscapes.mixin.guide;

import com.klikli_dev.modonomicon.book.Book;
import com.klikli_dev.modonomicon.book.BookCategory;
import com.klikli_dev.modonomicon.book.entries.BookContentEntry;
import com.klikli_dev.modonomicon.client.gui.BookGuiManager;
import com.klikli_dev.modonomicon.client.gui.book.BookParentScreen;
import com.klikli_dev.modonomicon.client.gui.book.entry.BookEntryDoublePageScreen;
import com.klikli_dev.modonomicon.client.gui.book.entry.BookEntryScreen;
import com.klikli_dev.modonomicon.client.gui.book.node.BookCategoryNodeScreen;
import com.klikli_dev.modonomicon.client.gui.book.node.BookParentNodeScreen;
import com.klikli_dev.modonomicon.platform.services.GuiHelper;
import dev.aeroscapes.guide.FieldGuide;
import dev.aeroscapes.guide.client.FieldGuideChapter;
import dev.aeroscapes.guide.client.FieldGuideScreen;
import dev.aeroscapes.guide.client.GuideArticleScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BookGuiManager.class, remap = false)
@SuppressWarnings("PMD.UnusedPrivateMethod") // The provider calls these through Mixin.
abstract class FieldGuideScreenMixin {
  @Shadow public BookParentScreen openBookParentScreen;

  @Redirect(
      method = "openBookInNodeMode",
      at =
          @At(
              value = "NEW",
              target = "com/klikli_dev/modonomicon/client/gui/book/node/BookParentNodeScreen"))
  private BookParentNodeScreen aeroscapes$bookScreen(Book book) {
    return FieldGuide.GUIDE.equals(book.getId())
        ? new FieldGuideScreen(book)
        : new BookParentNodeScreen(book);
  }

  @Redirect(
      method = "openCategoryInNodeMode",
      at =
          @At(
              value = "NEW",
              target = "com/klikli_dev/modonomicon/client/gui/book/node/BookCategoryNodeScreen"))
  private BookCategoryNodeScreen aeroscapes$chapter(
      BookParentNodeScreen screen, BookCategory chapter) {
    if (screen instanceof FieldGuideScreen guide) {
      return new FieldGuideChapter(guide, chapter);
    }
    return new BookCategoryNodeScreen(screen, chapter);
  }

  @Redirect(
      method = "openContentEntry",
      at =
          @At(
              value = "NEW",
              target =
                  "com/klikli_dev/modonomicon/client/gui/book/entry/BookEntryDoublePageScreen"))
  private BookEntryDoublePageScreen aeroscapes$article(
      BookParentScreen screen, BookContentEntry entry) {
    if (screen instanceof FieldGuideScreen guide) {
      return new GuideArticleScreen(guide, entry);
    }
    return new BookEntryDoublePageScreen(screen, entry);
  }

  @Redirect(
      method = "openContentEntry",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/klikli_dev/modonomicon/platform/services/GuiHelper;pushGuiLayer(Lnet/minecraft/client/gui/screens/Screen;)V"))
  private void aeroscapes$showArticle(GuiHelper helper, Screen screen) {
    if (openBookParentScreen instanceof FieldGuideScreen guide
        && screen instanceof GuideArticleScreen article) {
      guide.showArticle(article);
    } else {
      helper.pushGuiLayer(screen);
    }
  }

  @Inject(
      method =
          "closeEntryScreen(Lcom/klikli_dev/modonomicon/client/gui/book/entry/BookEntryScreen;Z)V",
      at = @At("HEAD"))
  private void aeroscapes$closeArticle(
      BookEntryScreen screen, boolean savePage, CallbackInfo info) {
    if (openBookParentScreen instanceof FieldGuideScreen guide) guide.hideArticle(screen);
  }
}
