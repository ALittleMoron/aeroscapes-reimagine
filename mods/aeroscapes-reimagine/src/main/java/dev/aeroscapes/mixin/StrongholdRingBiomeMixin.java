package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import dev.aeroscapes.worldgen.StructureBiomeCompatibility;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native rings choose preferred climate at Y0 before underground structures are relocated. */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class StrongholdRingBiomeMixin {
  @WrapOperation(
      method =
          "lambda$generateRingPositions$5(IILnet/minecraft/core/HolderSet;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/ChunkPos;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/biome/BiomeSource;findBiomeHorizontal(IIIILjava/util/function/Predicate;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/biome/Climate$Sampler;)Lcom/mojang/datafixers/util/Pair;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Exact pinned native lambda; traversal and RNG stay native.
  private Pair<BlockPos, Holder<Biome>> aeroscapesReimagine$preservePreferredClimate(
      BiomeSource source,
      int x,
      int y,
      int z,
      int radius,
      Predicate<Holder<Biome>> predicate,
      RandomSource random,
      Climate.Sampler sampler,
      Operation<Pair<BlockPos, Holder<Biome>>> original) {
    return original.call(
        source,
        x,
        y,
        z,
        radius,
        predicate,
        random,
        StructureBiomeCompatibility.deferredSampler(sampler));
  }
}
