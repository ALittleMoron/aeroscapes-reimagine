package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;

/** Preserves the pack's biome sampling order while retaining native palette writes. */
@Mixin(LevelChunkSection.class)
public abstract class BiomeFillOrderMixin {
  @WrapMethod(method = "fillBiomesFromNoise")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps native section biome filling.
  private void aeroscapesReimagine$preserveBiomeOrder(
      BiomeResolver resolver,
      Climate.Sampler sampler,
      int originQuartX,
      int originQuartY,
      int originQuartZ,
      Operation<Void> original) {
    // Biolith's cached nearest-neighbour ties can depend on the preceding sample.
    // The previous pack used Y/Z/X order; changing it changes a few existing biome cells.
    List<Holder<Biome>> sampled = new ArrayList<>(64);
    for (int localY = 0; localY < 4; localY++) {
      for (int localZ = 0; localZ < 4; localZ++) {
        for (int localX = 0; localX < 4; localX++) {
          sampled.add(
              resolver.getNoiseBiome(
                  originQuartX + localX, originQuartY + localY, originQuartZ + localZ, sampler));
        }
      }
    }
    BiomeResolver cached =
        (quartX, quartY, quartZ, ignoredSampler) ->
            sampled.get(
                (quartY - originQuartY) * 16 + (quartZ - originQuartZ) * 4 + quartX - originQuartX);
    original.call(cached, sampler, originQuartX, originQuartY, originQuartZ);
  }
}
