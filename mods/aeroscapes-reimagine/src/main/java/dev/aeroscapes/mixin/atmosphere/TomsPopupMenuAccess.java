package dev.aeroscapes.mixin.atmosphere;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.tom.storagemod.util.PopupMenuManager", remap = false)
public interface TomsPopupMenuAccess {
  @Accessor("menu")
  List<?> aeroscapes$getMenu();
}
