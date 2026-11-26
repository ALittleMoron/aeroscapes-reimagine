package dev.aeroscapes.mixin.guide;

import com.klikli_dev.modonomicon.book.Book;
import com.klikli_dev.modonomicon.client.gui.book.BookAddress;
import com.klikli_dev.modonomicon.client.gui.book.bookmarks.BookBookmarksScreen;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.guide.FieldGuide;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BookBookmarksScreen.class, remap = false)
abstract class FieldGuideBookmarksMixin {
  @Shadow
  public abstract Book getBook();

  @ModifyExpressionValue(
      method = "init",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/klikli_dev/modonomicon/bookstate/BookVisualStateManager;getBookmarksFor(Lnet/minecraft/world/entity/player/Player;Lcom/klikli_dev/modonomicon/book/Book;)Ljava/util/List;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Called when the provider populates its bookmark view.
  private List<BookAddress> aeroscapes$knownEntries(List<BookAddress> bookmarks) {
    Book book = getBook();
    if (!FieldGuide.GUIDE.equals(book.getId())) return bookmarks;
    return bookmarks.stream().filter(address -> book.getEntry(address.entryId()) != null).toList();
  }
}
