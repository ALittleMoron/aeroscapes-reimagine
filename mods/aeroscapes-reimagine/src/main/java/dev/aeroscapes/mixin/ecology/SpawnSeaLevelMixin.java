package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Level.class, NoiseBasedChunkGenerator.class})
public abstract class SpawnSeaLevelMixin {
  @ModifyReturnValue(method = "getSeaLevel", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Outside a Spawn operation the native value is unchanged.
  private int aeroscapes$scopedSeaLevel(int original) {
    return ScopedSeaLevel.resolve(original);
  }
}
