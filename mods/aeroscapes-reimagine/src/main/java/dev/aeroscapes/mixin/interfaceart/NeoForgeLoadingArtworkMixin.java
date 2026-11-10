package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@SuppressWarnings("PMD.UnusedPrivateMethod")
@Mixin(NeoForgeLoadingOverlay.class)
public abstract class NeoForgeLoadingArtworkMixin {
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"),
      index = 0)
  private ResourceLocation aeroscapes$neoForgeLogo(ResourceLocation original) {
    return InterfaceArt.available(InterfaceArt.SPLASH) ? InterfaceArt.SPLASH : original;
  }
}
