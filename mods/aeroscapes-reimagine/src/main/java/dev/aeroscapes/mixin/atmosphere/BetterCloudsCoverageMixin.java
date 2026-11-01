package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.CloudCoverageShader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.clouds.shaders.Shader", remap = false)
public abstract class BetterCloudsCoverageMixin {
  @ModifyExpressionValue(
      method =
          "compileShader(ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/ResourceManager;)I",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked before native define substitutions.
  private String aeroscapes$coverageSource(
      String source, @Local(argsOnly = true) ResourceLocation resource) {
    return CloudCoverageShader.adapt(resource.toString(), source);
  }
}
