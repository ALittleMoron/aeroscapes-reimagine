package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.ReforgeProfiles;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.creative.littletiles.client.render.overlay.PreviewRenderer;
import team.creative.littletiles.client.tool.LittleTool;

@Mixin(targets = "team.creative.littletiles.client.tool.LittleToolWrench", remap = false)
abstract class BuilderInspectionReforgeMixin {
  @ModifyExpressionValue(
      method = "tickInternal",
      at =
          @At(
              value = "FIELD",
              opcode = Opcodes.GETFIELD,
              target =
                  "Lteam/creative/littletiles/common/config/LittleConfigRendering;wrenchInfoRefresh:D"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$inspectionCadence(double interval, PreviewRenderer renderer) {
    boolean steady = renderer.player().isCrouching();
    double factor =
        switch (ReforgeProfiles.path(((LittleTool) (Object) this).stack)) {
          case "builder_inspect_poor_slow" -> 1.25;
          case "builder_inspect_fine_reading" -> 0.9;
          case "builder_inspect_exceptional_steady" -> steady ? 0.75 : 1.0;
          case "builder_inspect_epic_reading" -> steady ? 0.7 : 0.85;
          case "builder_inspect_legendary_survey" -> steady ? 0.6 : 0.85;
          default -> 1.0;
        };
    if (factor >= 1.0) return interval * factor;
    return Math.max(Math.min(interval, 0.5), interval * factor);
  }
}
