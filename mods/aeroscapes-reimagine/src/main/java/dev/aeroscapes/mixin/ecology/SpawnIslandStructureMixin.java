package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import com.ninni.spawn.server.level.structure.BaseIslandPiece;
import com.ninni.spawn.server.level.structure.BaseIslandStructure;
import com.ninni.spawn.server.level.structure.DodoIslandStructure;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import dev.aeroscapes.ecology.SpawnIslandSites;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BaseIslandStructure.class)
public abstract class SpawnIslandStructureMixin {
  @WrapMethod(method = "findGenerationPoint")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain the provider's native pieces, RNG and serialized origin.
  private Optional<Structure.GenerationStub> aeroscapes$basinIsland(
      Structure.GenerationContext context, Operation<Optional<Structure.GenerationStub>> original) {
    if (IslandTerrain.from(context.randomState()) == null) return original.call(context);
    int sea =
        SpawnIslandSites.seaLevel(
            context.randomState(),
            context.chunkPos().getMiddleBlockX(),
            context.chunkPos().getMiddleBlockZ(),
            Integer.MIN_VALUE);
    if (sea == Integer.MIN_VALUE) return Optional.empty();
    Optional<Structure.GenerationStub> result =
        ScopedSeaLevel.call(sea, () -> original.call(context));
    if (result.isEmpty()) return result;
    var stub = result.orElseThrow();
    if (!context
        .validBiome()
        .test(
            context
                .biomeSource()
                .getNoiseBiome(
                    stub.position().getX() >> 2,
                    stub.position().getY() >> 2,
                    stub.position().getZ() >> 2,
                    context.randomState().sampler()))) return Optional.empty();
    var builder = stub.getPiecesBuilder();
    var pieces = builder.build();
    if (pieces.isEmpty() || !(pieces.pieces().getFirst() instanceof BaseIslandPiece piece))
      return Optional.empty();
    int radius = (Object) this instanceof DodoIslandStructure ? 51 : piece.getRadius();
    if (!SpawnIslandSites.accepts(
        context.chunkGenerator(),
        context.randomState(),
        context.heightAccessor(),
        piece.getOrigin().getX(),
        piece.getOrigin().getZ(),
        radius)) return Optional.empty();
    return Optional.of(new Structure.GenerationStub(stub.position(), Either.right(builder)));
  }
}
