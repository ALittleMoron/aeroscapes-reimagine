package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionMessages;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "team.creative.littletiles.LittleTiles", remap = false)
public abstract class LittleCommandLanguageMixin {
  @Redirect(
      method = {
        "lambda$serverStarting$1(Ljava/util/List;)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$2(ILjava/util/List;I)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$3(I)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$7(Lcom/mojang/brigadier/context/CommandContext;)I",
        "lambda$serverStarting$8(Ljava/util/List;)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$9(ILjava/util/List;)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$10(I)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$11(I)Lnet/minecraft/network/chat/Component;",
        "lambda$serverStarting$12(IILjava/util/List;I)Lnet/minecraft/network/chat/Component;"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      require = 9)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native command components are sent to players.
  private static MutableComponent aeroscapes$translateCommand(String source) {
    return ConstructionMessages.literal(source);
  }
}
