package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.BasinWaterlines;
import dev.aeroscapes.ecology.ClientWaterlineAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ClientLevel.class, priority = 900)
public abstract class SpawnClientTintMixin implements ClientWaterlineAccess {
  @Unique private final BasinWaterlines aeroscapes$waterlines = new BasinWaterlines();

  @Override
  public BasinWaterlines aeroscapes$waterlines() {
    return aeroscapes$waterlines;
  }
}
