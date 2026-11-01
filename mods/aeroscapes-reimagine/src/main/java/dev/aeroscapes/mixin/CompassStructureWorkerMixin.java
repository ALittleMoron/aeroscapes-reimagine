package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.config.ConfigHandler;
import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import com.mojang.datafixers.util.Pair;
import dev.aeroscapes.qualityoflife.CompassRandomSpreadBridge;
import dev.aeroscapes.qualityoflife.CompassSearch;
import dev.aeroscapes.qualityoflife.CompassWorkerBridge;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructureSearchWorker.class, remap = false)
public abstract class CompassStructureWorkerMixin implements CompassWorkerBridge {
  @Shadow protected BlockPos startPos;
  @Shadow protected BlockPos currentPos;
  @Shadow protected StructurePlacement placement;
  @Shadow protected int samples;
  @Shadow protected boolean finished;
  @Shadow protected List<BlockPos> prevPos;
  @Unique private CompassSearch.Job aeroscapes$job;
  @Unique private int aeroscapes$phase;

  @Override
  public void aeroscapes$attach(CompassSearch.Job job, int phase) {
    aeroscapes$job = job;
    aeroscapes$phase = phase;
  }

  @Inject(method = "start", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$skipUnusable(CallbackInfo callback) {
    if (aeroscapes$job == null) return;
    if (!CompassSearch.current(aeroscapes$job, aeroscapes$phase) || finished) {
      finished = true;
      CompassSearch.structureFailed(aeroscapes$job, aeroscapes$phase, false);
      callback.cancel();
    }
  }

  @Inject(method = "hasWork", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$completeNearTraversal(CallbackInfoReturnable<Boolean> callback) {
    if (aeroscapes$job == null) return;
    boolean active =
        CompassSearch.current(aeroscapes$job, aeroscapes$phase)
            && !finished
            && samples < ConfigHandler.GENERAL.maxSamples.get()
            && prevPos.size() <= ConfigHandler.GENERAL.maxNextSearches.get();
    if ((Object) this instanceof CompassRandomSpreadBridge spread) {
      long spacingBlocks = (long) spread.aeroscapes$spacing() * 16;
      long rings =
          spacingBlocks > 0
              ? (aeroscapes$job.radius() + spacingBlocks - 1) / spacingBlocks + 2
              : -1;
      active &= spread.aeroscapes$ring() <= rings;
    } else {
      long squareRadius = (long) aeroscapes$job.radius() + 32;
      active &=
          Math.abs((long) currentPos.getX() - startPos.getX()) <= squareRadius
              && Math.abs((long) currentPos.getZ() - startPos.getZ()) <= squareRadius;
    }
    callback.setReturnValue(active);
  }

  @Inject(method = "doWork", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$stopStale(CallbackInfoReturnable<Boolean> callback) {
    if (aeroscapes$job != null && !CompassSearch.current(aeroscapes$job, aeroscapes$phase)) {
      finished = true;
      callback.setReturnValue(false);
    }
  }

  @Inject(method = "getStructureGeneratingAt", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$beforeChunkLoad(
      ChunkPos chunk, CallbackInfoReturnable<Pair<BlockPos, Structure>> callback) {
    if (aeroscapes$job == null) return;
    BlockPos locate = placement.getLocatePos(chunk);
    if (!CompassSearch.permits(aeroscapes$job, aeroscapes$phase, locate.getX(), locate.getZ())) {
      callback.setReturnValue(null);
    }
  }

  @Inject(method = "getStructureGeneratingAt", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$afterLocate(
      ChunkPos chunk, CallbackInfoReturnable<Pair<BlockPos, Structure>> callback) {
    if (aeroscapes$job == null || callback.getReturnValue() == null) return;
    BlockPos locate = callback.getReturnValue().getFirst();
    if (!CompassSearch.permits(aeroscapes$job, aeroscapes$phase, locate.getX(), locate.getZ())) {
      callback.setReturnValue(null);
    }
  }

  @Inject(method = "succeed", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$publish(BlockPos pos, Structure structure, CallbackInfo callback) {
    if (aeroscapes$job == null) return;
    finished = true;
    CompassSearch.structureFound(
        aeroscapes$job, aeroscapes$phase, pos, structure, prevPos, samples);
    callback.cancel();
  }

  @Inject(method = "fail", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$nextWorker(CallbackInfo callback) {
    if (aeroscapes$job == null) return;
    finished = true;
    CompassSearch.structureFailed(
        aeroscapes$job, aeroscapes$phase, samples >= ConfigHandler.GENERAL.maxSamples.get());
    callback.cancel();
  }
}
