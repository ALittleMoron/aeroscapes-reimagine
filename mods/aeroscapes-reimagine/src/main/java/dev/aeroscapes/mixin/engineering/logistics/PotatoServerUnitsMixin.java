package dev.aeroscapes.mixin.engineering.logistics;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = "com.yision.fluidlogistics.content.logistics.potatoServer.PotatoServerBlockEntity",
    remap = false)
public abstract class PotatoServerUnitsMixin {
  @ModifyArg(
      method = "addToGoggleTooltip",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/foundation/utility/CreateLang;text(Ljava/lang/String;)Lnet/createmod/catnip/lang/LangBuilder;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private String aeroscapes$performanceUnits(String source) {
    for (String suffix : new String[] {" ticks/s", " ms/tick"}) {
      if (source.endsWith(suffix))
        return source.substring(0, source.length() - suffix.length())
            + ConfigLiteralTranslations.translate(suffix, Language.getInstance()::getOrDefault);
    }
    return source;
  }
}
