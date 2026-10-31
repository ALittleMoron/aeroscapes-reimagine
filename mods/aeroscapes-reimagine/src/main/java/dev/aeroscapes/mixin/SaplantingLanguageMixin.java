package dev.aeroscapes.mixin;

import java.util.HashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the supplied Russian dictionary selectable through Saplanting's native command. */
@Pseudo
@Mixin(targets = "cool.muyucloud.saplanting.util.Config", remap = false)
public abstract class SaplantingLanguageMixin {
  @Shadow @Final private HashSet<String> langs;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked after the pinned native constructor.
  private void aeroscapesReimagine$russianLanguage(CallbackInfo callback) {
    langs.add("ru_ru");
  }
}
