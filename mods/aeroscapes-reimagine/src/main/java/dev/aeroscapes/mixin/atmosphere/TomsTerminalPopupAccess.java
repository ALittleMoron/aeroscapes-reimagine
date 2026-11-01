package dev.aeroscapes.mixin.atmosphere;

import com.tom.storagemod.util.PopupMenuManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.tom.storagemod.screen.AbstractStorageTerminalScreen", remap = false)
public interface TomsTerminalPopupAccess {
  @Accessor("popup")
  PopupMenuManager aeroscapes$getPopup();
}
