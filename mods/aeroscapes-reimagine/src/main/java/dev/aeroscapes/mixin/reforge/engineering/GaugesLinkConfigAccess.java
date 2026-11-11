package dev.aeroscapes.mixin.reforge.engineering;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "wile.rsgauges.ModConfig", remap = false)
public interface GaugesLinkConfigAccess {
  @Invoker("max_switch_linking_distance")
  static int aeroscapes$maxLinkDistance() {
    throw new AssertionError();
  }
}
