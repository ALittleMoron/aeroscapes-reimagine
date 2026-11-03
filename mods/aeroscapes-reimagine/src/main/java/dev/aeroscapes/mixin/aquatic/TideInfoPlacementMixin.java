package dev.aeroscapes.mixin.aquatic;

import java.util.Locale;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.li64.tide.config.TideClientConfig$General$InfoPlacement", remap = false)
public abstract class TideInfoPlacementMixin implements SelectionListEntry.Translatable {
  @Override
  public String getKey() {
    return "aeroscapes_reimagine.aquatic.info_placement."
        + ((Enum<?>) (Object) this).name().toLowerCase(Locale.ROOT);
  }
}
