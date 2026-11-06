package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.LittleSablePayloadBody;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.ServerSubLevel", remap = false)
public abstract class LittleSableBodyPayloadMixin implements LittleSablePayloadBody {
  @Unique private final Set<BlockPos> aeroscapes$changedTiles = new HashSet<>();

  @Override
  public void aeroscapes$payloadChanged(BlockPos position) {
    aeroscapes$changedTiles.add(position.immutable());
  }

  @Override
  public void aeroscapes$flushPayloads() {
    if (aeroscapes$changedTiles.isEmpty()) return;
    Set<BlockPos> changed = Set.copyOf(aeroscapes$changedTiles);
    aeroscapes$changedTiles.clear();
    ServerSubLevel body = (ServerSubLevel) (Object) this;
    if (body.isRemoved()) return;
    var level = body.getLevel();
    var pipeline = SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
    Pose3d previous = new Pose3d(body.logicalPose());
    body.buildMassTracker();
    for (BlockPos position : changed) {
      var chunk = level.getChunkAt(position);
      var section = chunk.getSection(level.getSectionIndex(position.getY()));
      var state = level.getBlockState(position);
      pipeline.handleBlockChange(
          SectionPos.of(position),
          section,
          position.getX() & 15,
          position.getY() & 15,
          position.getZ() & 15,
          state,
          state);
    }
    if (body.getSelfMassTracker().getCenterOfMass() != null) {
      body.updateMergedMassData(1);
      Vector3d center =
          previous.transformPosition(new Vector3d(body.logicalPose().rotationPoint()));
      body.logicalPose().position().set(center);
      pipeline.teleport(body, center, previous.orientation());
      body.updateBoundingBox();
      pipeline.onStatsChanged(body);
    }
  }

  @Inject(method = "tick()V", at = @At("HEAD"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Flush once before native empty-body/splitting decisions.
  private void aeroscapes$flushBeforeTick(CallbackInfo callback) {
    aeroscapes$flushPayloads();
  }
}
