package dev.aeroscapes.mixin.movement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.createmod.catnip.config.ui.ConfigScreen", remap = false)
public interface CatnipConfigurationAccess {
  @Accessor("modID")
  static String aeroscapes$modId() {
    throw new UnsupportedOperationException("Mixin accessor was not transformed");
  }
}
