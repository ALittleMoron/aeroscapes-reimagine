package dev.aeroscapes.mixin;

import com.chaosthedude.naturescompass.util.BiomeSearchWorker;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import dev.aeroscapes.qualityoflife.CompassSearch;
import dev.aeroscapes.qualityoflife.CompassWorkerBridge;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BiomeSearchWorker.class, remap = false)
public abstract class CompassBiomeWorkerMixin implements CompassWorkerBridge {
  @Shadow @Final private int sampleSpace;
  @Shadow @Final private int maxSamples;
  @Shadow private ResourceLocation biomeKey;
  @Shadow private BlockPos startPos;
  @Shadow private int samples;
  @Shadow private int x;
  @Shadow private int z;
  @Shadow private int nextLength;
  @Shadow private int length;
  @Shadow private Direction direction;
  @Shadow private boolean finished;
  @Shadow private List<BlockPos> prevPos;
  @Unique private CompassSearch.Job aeroscapes$job;
  @Unique private int aeroscapes$phase;

  @Override
  public void aeroscapes$attach(CompassSearch.Job job, int phase) {
    aeroscapes$job = job;
    aeroscapes$phase = phase;
    if (phase == 1 && sampleSpace > 0) {
      // Native legs grow on every turn; a full cycle spans two sample cells per side.
      int cycles =
          Math.max(
              0,
              (int) Math.floor(CompassSearch.FREE_RADIUS / (2.0 * Math.sqrt(2.0) * sampleSpace))
                  - 1);
      if (cycles > 0) {
        int offset = 2 * cycles * sampleSpace;
        x = startPos.getX() - offset;
        z = startPos.getZ() + offset;
        nextLength = (4 * cycles + 1) * sampleSpace;
        length = 0;
        direction = Direction.NORTH;
      }
    }
  }

  @Inject(method = "hasWork", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$boundedWork(CallbackInfoReturnable<Boolean> callback) {
    if (aeroscapes$job == null) return;
    boolean active = CompassSearch.current(aeroscapes$job, aeroscapes$phase);
    long padding = (long) sampleSpace * 2;
    long squareRadius = aeroscapes$job.radius() + padding;
    callback.setReturnValue(
        active
            && biomeKey != null
            && !finished
            && samples <= maxSamples
            && Math.abs((long) x - startPos.getX()) <= squareRadius
            && Math.abs((long) z - startPos.getZ()) <= squareRadius);
  }

  @Inject(method = "doWork", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$stopStale(CallbackInfoReturnable<Boolean> callback) {
    if (aeroscapes$job != null && !CompassSearch.current(aeroscapes$job, aeroscapes$phase)) {
      finished = true;
      callback.setReturnValue(false);
    }
  }

  @Redirect(
      method = "doWork",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/chaosthedude/naturescompass/util/BiomeUtils;getKeyForBiome(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/biome/Biome;)Ljava/util/Optional;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private Optional<ResourceLocation> aeroscapes$ignoreOutsideCircle(Level level, Biome biome) {
    if (aeroscapes$job != null && !CompassSearch.permits(aeroscapes$job, aeroscapes$phase, x, z))
      return Optional.empty();
    return BiomeUtils.getKeyForBiome(level, biome);
  }

  @Inject(method = "succeed", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$publish(CallbackInfo callback) {
    if (aeroscapes$job == null) return;
    finished = true;
    CompassSearch.biomeFound(aeroscapes$job, aeroscapes$phase, x, z, prevPos, samples);
    callback.cancel();
  }

  @Inject(method = "fail", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$nextPhase(CallbackInfo callback) {
    if (aeroscapes$job == null) return;
    finished = true;
    CompassSearch.biomeFailed(aeroscapes$job, aeroscapes$phase, samples >= maxSamples);
    callback.cancel();
  }
}
