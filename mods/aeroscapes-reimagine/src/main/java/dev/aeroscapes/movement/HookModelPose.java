package dev.aeroscapes.movement;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

/** Native baseline to restore before each lazy animation and at the end of the render. */
final class HookModelPose {
  private final List<SavedPart> parts;

  HookModelPose(List<ModelPart> modelParts) {
    parts = modelParts.stream().map(SavedPart::new).toList();
  }

  void restore() {
    for (SavedPart part : parts) part.restore();
  }

  private record SavedPart(ModelPart part, PartPose pose, float x, float y, float z) {
    private SavedPart(ModelPart part) {
      this(part, part.storePose(), part.xScale, part.yScale, part.zScale);
    }

    private void restore() {
      part.loadPose(pose);
      part.xScale = x;
      part.yScale = y;
      part.zScale = z;
    }
  }
}
