package dev.aeroscapes.mixin.survival;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "com.dannyandson.nutritionalbalance.Config", remap = false)
public abstract class NutritionConfigRangeMixin {
  @ModifyArgs(
      method = "<clinit>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/common/ModConfigSpec$Builder;defineInRange(Ljava/lang/String;DDD)Lnet/neoforged/neoforge/common/ModConfigSpec$DoubleValue;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$attackRange(Args arguments) {
    // The provider's configured flat damage penalty otherwise bottoms out at two.
    if ("malnourished_attack_damage".equals(arguments.get(0))) arguments.set(2, -4.0);
  }
}
