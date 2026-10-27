package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.CompleteStrongholdPiece;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps native stronghold walls when connected rooms extend beyond an island's slope. */
@Mixin(StructurePiece.class)
public abstract class StrongholdShellMixin implements CompleteStrongholdPiece {
  @Unique private boolean aeroscapesReimagine$completeShell;

  @Override
  public void aeroscapesReimagine$completeShell() {
    if ((Object) this instanceof StrongholdPieces.StrongholdPiece) {
      aeroscapesReimagine$completeShell = true;
    }
  }

  @Inject(method = "createTag", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin injection above.
  private void aeroscapesReimagine$saveShell(
      StructurePieceSerializationContext context, CallbackInfoReturnable<CompoundTag> callback) {
    if (aeroscapesReimagine$completeShell) {
      callback.getReturnValue().putBoolean("aeroscapes_reimagine:complete_shell", true);
    }
  }

  @Inject(
      method =
          "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceType;Lnet/minecraft/nbt/CompoundTag;)V",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned constructor injection above.
  private void aeroscapesReimagine$loadShell(
      StructurePieceType type, CompoundTag tag, CallbackInfo callback) {
    aeroscapesReimagine$completeShell =
        (Object) this instanceof StrongholdPieces.StrongholdPiece
            && tag.getBoolean("aeroscapes_reimagine:complete_shell");
  }

  @ModifyVariable(
      method =
          "generateBox(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;IIIIIIZLnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/StructurePiece$BlockSelector;)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned argument injection above.
  private boolean aeroscapesReimagine$buildCompleteShell(boolean existingOnly) {
    return existingOnly && !aeroscapesReimagine$completeShell;
  }
}
