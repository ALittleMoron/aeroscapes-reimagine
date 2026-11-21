package dev.aeroscapes.mixin.hexquality;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.hex.HexCastingReports;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CastingVM.class, remap = false)
abstract class StaffExecutionMixin {
  @WrapOperation(
      method = "queueExecuteAndWrapIotas",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;performSideEffects(Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM$TempControllerInfo;Ljava/util/List;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$afterResult(
      CastingVM vm,
      CastingVM.TempControllerInfo info,
      List<OperatorSideEffect> effects,
      Operation<Void> original,
      @Local CastResult result) {
    original.call(vm, info, effects);
    if (vm.getEnv() instanceof StaffCastEnv staff)
      HexCastingReports.finishStep(staff, result, info.getEarlyExit());
  }
}
