package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "com.natamus.collective_common_neoforge.cmds.CommandCollectiveConfig",
    remap = false)
public abstract class DuskCommandLanguageMixin {
  @WrapOperation(
      method = "showEntryInfo",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/natamus/collective_common_neoforge/functions/MessageFunctions;sendMessage(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;Lnet/minecraft/ChatFormatting;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned display injection.
  private static void aeroscapes$description(
      CommandSourceStack source, String text, ChatFormatting colour, Operation<Void> original) {
    if (source.getPlayer() == null) {
      original.call(source, text, colour);
      return;
    }
    MutableComponent message = Component.empty();
    String remaining = text;
    if (remaining.startsWith(" ")) {
      message.append(" ");
      remaining = remaining.substring(1);
    }
    int range = remaining.indexOf(" min: ");
    int options = remaining.indexOf(" options: ");
    int end = range >= 0 ? range : options >= 0 ? options : remaining.length();
    String description = remaining.substring(0, end);
    message.append(
        Component.translatableWithFallback(
            ConfigLiteralTranslations.key(description), description));
    if (range >= 0) {
      String numeric =
          remaining.substring(
              range + " min: ".length(), options >= 0 ? options : remaining.length());
      int max = numeric.indexOf(", max: ");
      if (max < 0) throw new IllegalStateException("Pinned Dusk range format changed");
      message
          .append(" ")
          .append(
              Component.translatable(
                  "aeroscapes_reimagine.config.dusk.range",
                  numeric.substring(0, max),
                  numeric.substring(max + ", max: ".length())));
    }
    if (options >= 0) {
      // These values are command/parser syntax; retain their spelling in command help.
      message
          .append(" ")
          .append(
              Component.translatable(
                  "aeroscapes_reimagine.config.dusk.options",
                  remaining.substring(options + " options: ".length())));
    }
    message.withStyle(colour);
    source.sendSuccess(() -> message, false);
  }
}
