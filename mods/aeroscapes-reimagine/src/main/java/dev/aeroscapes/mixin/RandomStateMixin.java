package dev.aeroscapes.mixin;

import com.google.common.base.Suppliers;
import dev.aeroscapes.worldgen.BiomeTerrainMask;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.RetainedIslandTerrain;
import dev.aeroscapes.worldgen.c2me.C2meIslandCompiler;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.fml.ModList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the native, seeded root independently of optional compiler wrapper classes. */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements RetainedIslandTerrain {
  @Shadow @Final private NoiseRouter router;
  @Unique private IslandTerrain aeroscapesReimagine$islandTerrain;
  @Unique private BiomeTerrainMask aeroscapesReimagine$biomeMask;

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

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Attaches the pre-compiler terrain to native queries.
  private void aeroscapesReimagine$attachBiomeTerrain(
      NoiseGeneratorSettings settings,
      HolderGetter<NormalNoise.NoiseParameters> noises,
      long seed,
      CallbackInfo callback) {
    IslandTerrain terrain = aeroscapesReimagine$islandTerrain;
    if (terrain == null) return;
    var biomeTerrain =
        Suppliers.memoize(
            () ->
                ModList.get().isLoaded("c2me_opts_dfc")
                    ? (IslandTerrain) C2meIslandCompiler.node(terrain).getDelegate()
                    : terrain);
    var noise = settings.noiseSettings();
    aeroscapesReimagine$biomeMask =
        new BiomeTerrainMask(
            (x, y, z) ->
                biomeTerrain.get().biomeDensity(new DensityFunction.SinglePointContext(x, y, z)) > 0
                    || terrain.column(x, z).water(y),
            noise.minY(),
            noise.minY() + noise.height(),
            noise.getCellWidth(),
            noise.getCellHeight());
  }

  @Inject(method = "sampler", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // C2ME replaces the sampler after constructor hooks.
  private void aeroscapesReimagine$attachCurrentSampler(
      CallbackInfoReturnable<Climate.Sampler> callback) {
    ((IslandBiomeSampler) (Object) callback.getReturnValue())
        .aeroscapesReimagine$terrainMask(aeroscapesReimagine$biomeMask);
  }
}
