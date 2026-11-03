package dev.aeroscapes.mixin.movement;

import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ToggleKeyMapping.class)
public interface HookToggleKeyAccess {
  @Invoker("reset")
  void aeroscapes$reset();
}
