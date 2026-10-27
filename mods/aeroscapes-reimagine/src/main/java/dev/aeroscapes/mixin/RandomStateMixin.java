package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.RetainedIslandTerrain;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the native, seeded root independently of optional compiler wrapper classes. */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements RetainedIslandTerrain {
  @Shadow @Final private NoiseRouter router;
  @Unique private IslandTerrain aeroscapesReimagine$islandTerrain;

  @Inject(
      method = "<init>",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/world/level/levelgen/RandomState;router:Lnet/minecraft/world/level/levelgen/NoiseRouter;",
              opcode = Opcodes.PUTFIELD,
              ordinal = 0,
              shift = At.Shift.AFTER))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Runs after native noise seeding, before C2ME's RETURN hook.
  private void aeroscapesReimagine$retainIslandTerrain(CallbackInfo callback) {
    DensityFunction density = router.finalDensity();
    while (density instanceof DensityFunctions.HolderHolder holder)
      density = holder.function().value();
    if (density instanceof IslandTerrain terrain) aeroscapesReimagine$islandTerrain = terrain;
  }

  @Override
  public IslandTerrain aeroscapesReimagine$islandTerrain() {
    return aeroscapesReimagine$islandTerrain;
  }
}
