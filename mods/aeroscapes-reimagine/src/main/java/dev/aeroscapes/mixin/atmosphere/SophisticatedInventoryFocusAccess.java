package dev.aeroscapes.mixin.atmosphere;

import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public interface SophisticatedInventoryFocusAccess {
  @Invoker("isTextBoxFocused")
  boolean aeroscapes$isTextBoxFocused();

  @Accessor("modalOverlay")
  WidgetBase aeroscapes$getModalOverlay();
}
