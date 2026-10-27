package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ancient cities need one open cavern before their individual templates are placed. */
@Mixin(Beardifier.class)
public abstract class IslandCavernMixin {
  @Unique private List<CityCavern> aeroscapesReimagine$caverns = List.of();

  @Inject(method = "forStructuresInChunk", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native density factory callback.
  private static void aeroscapesReimagine$selectCaverns(
      StructureManager manager, ChunkPos chunk, CallbackInfoReturnable<Beardifier> callback) {
    List<CityCavern> caverns = new ArrayList<>();
    manager
        .startsForStructure(
            chunk,
            structure ->
                structure instanceof SupportedStructure supported && supported.needsCavern())
        .forEach(
            start -> {
              caverns.add(
                  new CityCavern(
                      start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()));
            });
    ((IslandCavernMixin) (Object) callback.getReturnValue()).aeroscapesReimagine$caverns =
        List.copyOf(caverns);
  }

  @Inject(method = "compute", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native Beardifier noise callback.
  private void aeroscapesReimagine$openCavern(
      DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> callback) {
    if (aeroscapesReimagine$caverns.isEmpty()) return;
    double contribution = 0;
    for (CityCavern cavern : aeroscapesReimagine$caverns)
      contribution =
          Math.min(
              contribution,
              cavern.contribution(context.blockX(), context.blockY(), context.blockZ()));
    callback.setReturnValue(callback.getReturnValue() + contribution);
  }
}
