package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.SoundCaptions;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.sounds.SoundManager$Preparations")
public abstract class SoundCaptionMixin {
  @ModifyArg(
      method = "handleRegistration",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/sounds/WeighedSoundEvents;<init>(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Only the absent subtitle changes; audio is native.
  private String aeroscapes$caption(
      String original, @Local(argsOnly = true) ResourceLocation event) {
    return SoundCaptions.subtitle(event, original);
  }
}
