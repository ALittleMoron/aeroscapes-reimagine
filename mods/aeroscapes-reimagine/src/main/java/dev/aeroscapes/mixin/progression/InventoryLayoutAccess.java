package dev.aeroscapes.mixin.progression;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface InventoryLayoutAccess {
  @Accessor("leftPos")
  void aeroscapes$leftPos(int left);
}
