package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.createmod.catnip.config.ui.entries.ValueEntry", remap = false)
public abstract class CatnipConfigValueMixin {
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
              ordinal = 0),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve the later diagnostic mod-id/path literal.
  private String aeroscapes$translateTooltipLabel(String source) {
    return ConfigLiteralTranslations.translate(source, Language.getInstance()::getOrDefault);
  }

  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;",
              ordinal = 1))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native metadata parsing and range filtering ran first.
  private Stream<MutableComponent> aeroscapes$translateComment(
      Stream<String> lines,
      Function<String, MutableComponent> mapper,
      Operation<Stream<MutableComponent>> original,
      @Local(argsOnly = true) ModConfigSpec.ValueSpec specification) {
    String sourceBlock = specification.getComment();
    return original.call(
        lines,
        (Function<String, MutableComponent>)
            line ->
                mapper.apply(
                    ConfigLiteralTranslations.translateCommentLine(
                        line, sourceBlock, Language.getInstance()::getOrDefault)));
  }
}
