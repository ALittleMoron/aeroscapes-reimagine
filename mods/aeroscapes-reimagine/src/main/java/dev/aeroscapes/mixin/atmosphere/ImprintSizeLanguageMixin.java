package dev.aeroscapes.mixin.atmosphere;

import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SelectionListEntry.class, remap = false)
public abstract class ImprintSizeLanguageMixin {
  @Inject(method = "lambda$new$2", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Cloth Config turns this enum into a bare key.
  private static void aeroscapes$sizeName(Object value, CallbackInfoReturnable<Component> result) {
    if (value instanceof Enum<?> size
        && size.getDeclaringClass()
            .getName()
            .equals("org.tastytrash.imprint.config.ImprintConfig$FootprintSizes")) {
      result.setReturnValue(
          Component.translatableWithFallback(
              "aeroscapes.imprint.size." + size.name(), size.name()));
    }
  }
}
