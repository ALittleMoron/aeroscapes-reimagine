package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.util.ArrayList;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
public abstract class IslandDungeonReferencesMixin {
  @WrapOperation(
      method = "createReferences",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/structure/StructureStart;getBoundingBox()Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep terrain protection referencable without widening spawn
  // bounds.
  private BoundingBox aeroscapes$terrainProtectionReferences(
      StructureStart start, Operation<BoundingBox> original) {
    var bounds = original.call(start);
    if (!(start.getStructure() instanceof SupportedStructure supported)) return bounds;
    if (supported.protectsDiscoveryFootings())
      return bounds.inflatedBy(SupportedStructure.DISCOVERY_APPROACH_LENGTH);
    if (!supported.protectsArena()) return bounds;
    var envelopes = new ArrayList<BoundingBox>();
    envelopes.add(bounds);
    for (var piece : start.getPieces()) {
      if (SupportedStructure.isSaltArena(piece)) {
        envelopes.add(piece.getBoundingBox().inflatedBy(SupportedStructure.ARENA_SHELL_DEPTH));
      }
    }
    return BoundingBox.encapsulatingBoxes(envelopes).orElseThrow();
  }
}
