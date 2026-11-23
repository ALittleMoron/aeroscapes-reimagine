package dev.aeroscapes.mixin.atmosphere;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.hash.Hashing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.include.IncludeGraph", remap = false)
public abstract class MakeUpBlockLightMixin {
  private static final String MAKEUP_LIGHT_INCLUDE =
      "/MakeUp-UltraFast-9.6.zip!/shaders/src/light_vertex.glsl";
  private static final String SOURCE_HASH =
      "1e3a5340d9abd96a384716f5673913fffcb4b49251a6ec9a4eb62f212d9924ff";

  @ModifyReturnValue(
      method = "readFile(Ljava/nio/file/Path;)Ljava/lang/String;",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Iris reads this include before expanding it.
  private static String aeroscapes$weakBlockLight(String source, Path file) {
    if (!"jar".equals(file.getFileSystem().provider().getScheme())
        || !file.toUri().getSchemeSpecificPart().endsWith(MAKEUP_LIGHT_INCLUDE)
        || !SOURCE_HASH.equals(Hashing.sha256().hashString(source, UTF_8).toString())) {
      return source;
    }
    return source.replace(
        "#ifdef DYN_HAND_LIGHT",
        """
        // Subtract Iris's lightmap texel-center offset in MakeUp's coordinate scale.
        float weakBlockLight = max(illumination.x - (0.03125 * 1.0323886639676114), 0.0);
        candleColor += CANDLE_BASELIGHT * (0.95 * weakBlockLight * max(1.0 - 2.25 * weakBlockLight, 0.0));

        #ifdef DYN_HAND_LIGHT\
        """);
  }
}
