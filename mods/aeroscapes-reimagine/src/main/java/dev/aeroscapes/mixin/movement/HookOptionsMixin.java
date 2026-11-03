package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.HookControlsMigration;
import dev.aeroscapes.movement.HookOptionsAccess;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class HookOptionsMixin implements HookOptionsAccess {
  @Unique private int aeroscapes$hookControlsVersion;
  @Unique private boolean aeroscapes$hookMappingsLoaded;

  @Inject(method = "processOptions", at = @At("TAIL"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native load/save share this FieldAccess serializer.
  private void aeroscapes$controlsVersion(Options.FieldAccess access, CallbackInfo callback) {
    aeroscapes$hookControlsVersion =
        access.process(HookControlsMigration.OPTIONS_KEY, aeroscapes$hookControlsVersion);
  }

  @Inject(method = "load(Z)V", at = @At("RETURN"), require = 1, remap = false)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // NeoForge reloads mod bindings after registration.
  private void aeroscapes$bindingsLoaded(boolean limited, CallbackInfo callback) {
    if (limited) aeroscapes$hookMappingsLoaded = true;
  }

  @Override
  public int aeroscapes$hookControlsVersion() {
    return aeroscapes$hookControlsVersion;
  }

  @Override
  public void aeroscapes$hookControlsVersion(int version) {
    aeroscapes$hookControlsVersion = version;
  }

  @Override
  public boolean aeroscapes$hookMappingsLoaded() {
    return aeroscapes$hookMappingsLoaded;
  }
}
