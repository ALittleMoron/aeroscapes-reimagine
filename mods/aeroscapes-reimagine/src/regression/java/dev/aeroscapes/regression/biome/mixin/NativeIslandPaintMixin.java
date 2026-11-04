package dev.aeroscapes.regression.biome.mixin;

import com.ninni.spawn.server.level.structure.BaseIslandPiece;
import dev.aeroscapes.regression.biome.NativeIslandPaintEvidence;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.ninni.spawn.server.level.structure.BaseIslandPiece", remap = false)
public abstract class NativeIslandPaintMixin {
  @Inject(method = "setIslandBiomeForChunk", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Observe exact native paint arguments after the write.
  private void aeroscapes$paint(
      WorldGenLevel level,
      int chunkX,
      int chunkZ,
      Holder<Biome> biome,
      int bottom,
      int top,
      CallbackInfo callback) {
    NativeIslandPaintEvidence.record(
        level,
        chunkX,
        chunkZ,
        (BaseIslandPiece) (Object) this,
        biome.unwrapKey().orElseThrow().location().toString(),
        bottom,
        top);
  }
}
