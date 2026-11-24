package dev.aeroscapes.enchanting;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record LibraryStatus(
    int menuId, int mastery, int savings, Map<ResourceLocation, Integer> ceilings) {
  public static final LibraryStatus EMPTY = new LibraryStatus(-1, 0, 0, Map.of());

  public LibraryStatus {
    ceilings = Map.copyOf(ceilings);
  }

  public interface Menu {
    LibraryStatus aeroscapes$libraryStatus();

    void aeroscapes$libraryStatus(LibraryStatus status);
  }
}
