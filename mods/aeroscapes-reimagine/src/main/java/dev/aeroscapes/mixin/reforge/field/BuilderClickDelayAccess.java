package dev.aeroscapes.mixin.reforge.field;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface BuilderClickDelayAccess {
  @Accessor("rightClickDelay")
  void aeroscapes$setBuilderClickDelay(int delay);
}
