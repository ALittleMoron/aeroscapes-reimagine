package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ninni.spawn.server.level.structure.BaseIslandStructure;
import dev.aeroscapes.ecology.SpawnIslandSites;
import dev.aeroscapes.worldgen.IslandBiomeSources;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Structure.class)
public abstract class SpawnStructureEligibilityMixin {
  @WrapMethod(method = "findValidGenerationPoint")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native validation also runs after the provider returns its stub.
  private Optional<Structure.GenerationStub> aeroscapes$oceanFamily(
      Structure.GenerationContext context, Operation<Optional<Structure.GenerationStub>> original) {
    if (!((Object) this instanceof BaseIslandStructure)) return original.call(context);
    var source = IslandBiomeSources.find(context.chunkGenerator().getBiomeSource());
    if (source == null) return original.call(context);
    var ocean =
        source.basinOcean(
            context.chunkPos().getMiddleBlockX(),
            context.chunkPos().getMiddleBlockZ(),
            context.randomState().sampler());
    if (ocean == null || !context.validBiome().test(ocean)) return original.call(context);
    int sea =
        SpawnIslandSites.seaLevel(
            context.randomState(),
            context.chunkPos().getMiddleBlockX(),
            context.chunkPos().getMiddleBlockZ(),
            Integer.MIN_VALUE);
    if (sea == Integer.MIN_VALUE) return original.call(context);
    var selected =
        context
            .biomeSource()
            .getNoiseBiome(
                context.chunkPos().getMiddleBlockX() >> 2,
                sea >> 2,
                context.chunkPos().getMiddleBlockZ() >> 2,
                context.randomState().sampler());
    var eligible =
        new Structure.GenerationContext(
            context.registryAccess(),
            context.chunkGenerator(),
            context.biomeSource(),
            context.randomState(),
            context.structureTemplateManager(),
            context.random(),
            context.seed(),
            context.chunkPos(),
            context.heightAccessor(),
            biome -> context.validBiome().test(biome) || biome.equals(selected));
    return original.call(eligible);
  }
}
