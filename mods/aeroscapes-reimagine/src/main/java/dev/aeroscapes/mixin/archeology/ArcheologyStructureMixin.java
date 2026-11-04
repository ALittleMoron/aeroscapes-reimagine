package dev.aeroscapes.mixin.archeology;

import dev.aeroscapes.archeology.ArcheologyProjection;
import dev.aeroscapes.worldgen.IslandBiomeSources;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.Pandarix.world.structure.ArcheologyStructures", remap = false)
public abstract class ArcheologyStructureMixin implements ArcheologyProjection {
  @Shadow @Final private Optional<Heightmap.Types> projectStartToHeightmap;

  @Override
  public Optional<Heightmap.Types> aeroscapes$heightmap() {
    return projectStartToHeightmap;
  }

  @Inject(method = "extraSpawningChecks", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Island support replaces the native Y150 ceiling.
  private static void aeroscapes$islandHeight(
      Structure.GenerationContext context, CallbackInfoReturnable<Boolean> callback) {
    if (IslandBiomeSources.find(context.chunkGenerator().getBiomeSource()) != null)
      callback.setReturnValue(true);
  }
}
