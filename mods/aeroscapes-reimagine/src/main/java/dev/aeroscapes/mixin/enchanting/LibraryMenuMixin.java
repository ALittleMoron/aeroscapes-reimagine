package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryStatus;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EnchantingTableMenu.class, remap = false)
abstract class LibraryMenuMixin implements LibraryStatus.Menu {
  @Unique private LibraryStatus aeroscapes$library = LibraryStatus.EMPTY;

  @Override
  public LibraryStatus aeroscapes$libraryStatus() {
    return aeroscapes$library;
  }

  @Override
  public void aeroscapes$libraryStatus(LibraryStatus status) {
    aeroscapes$library = status;
  }
}
