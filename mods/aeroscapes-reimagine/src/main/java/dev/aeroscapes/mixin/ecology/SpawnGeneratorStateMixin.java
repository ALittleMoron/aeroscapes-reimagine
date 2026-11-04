package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.SpawnIslandGenerator;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class SpawnGeneratorStateMixin {
  @Inject(method = "createState", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Bind Dodo's state-only search to the actual generator.
  private void aeroscapes$spawnGenerator(
      CallbackInfoReturnable<ChunkGeneratorStructureState> callback) {
    ((SpawnIslandGenerator) callback.getReturnValue())
        .aeroscapes$spawnGenerator((ChunkGenerator) (Object) this);
  }
}
