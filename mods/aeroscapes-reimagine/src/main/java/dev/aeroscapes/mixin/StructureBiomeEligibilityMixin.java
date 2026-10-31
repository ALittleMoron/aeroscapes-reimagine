package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.StructureBiomeCompatibility;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changes only the scoped structure prefit sampler, retaining the actual outer source. */
@Mixin(Structure.class)
public abstract class StructureBiomeEligibilityMixin {
  @WrapOperation(
      method = "isValidBiome",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/biome/BiomeSource;getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native private validation supplies the scoped context.
  private static Holder<Biome> aeroscapesReimagine$preserveStructureClimate(
      BiomeSource source,
      int x,
      int y,
      int z,
      Climate.Sampler sampler,
      Operation<Holder<Biome>> original,
      @Local(argsOnly = true) Structure.GenerationContext context) {
    return original.call(
        source, x, y, z, StructureBiomeCompatibility.prefitSampler(context, sampler));
  }
}
