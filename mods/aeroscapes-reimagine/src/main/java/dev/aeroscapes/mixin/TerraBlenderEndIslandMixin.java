package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import terrablender.api.EndBiomeRegistry;
import terrablender.core.TerraBlender;
import terrablender.worldgen.noise.LayeredNoiseUtil;

/** Restores the island-biome input in TerraBlender 4.1.0.8. */
@Mixin(value = LayeredNoiseUtil.class, remap = false)
public abstract class TerraBlenderEndIslandMixin {
  @ModifyArg(
      method =
          "biomeArea(Lnet/minecraft/core/RegistryAccess;JILjava/util/List;)Lterrablender/worldgen/noise/Area;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lterrablender/worldgen/noise/BiomeInitialLayer;<init>(Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)V"),
      index = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin argument injection above.
  private static List<WeightedEntry.Wrapper<ResourceKey<Biome>>> aeroscapesReimagine$islandBiomes(
      List<WeightedEntry.Wrapper<ResourceKey<Biome>>> original, @Local(argsOnly = true) int size) {
    // The pinned distinct sizes identify the island call on every source initialization.
    if (size == TerraBlender.CONFIG.endIslandBiomeSize
        && size != TerraBlender.CONFIG.endEdgeBiomeSize
        && original.equals(EndBiomeRegistry.getEdgeBiomes())) {
      return EndBiomeRegistry.getIslandBiomes();
    }
    return original;
  }
}
