package dev.aeroscapes.regression.noise.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.regression.BeeConstructionRegression;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = {
      "net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal",
      "net.minecraft.world.entity.animal.Bee$BeeGoToKnownFlowerGoal"
    })
public abstract class BeeConstructorProbeMixin {
  @WrapOperation(
      method = "<init>",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin observes both actual native goal constructors.
  private int aeroscapesRegression$beeConstructorRandom(
      RandomSource receiver,
      int bound,
      Operation<Integer> original,
      @Local(argsOnly = true) Bee bee) {
    var call = BeeConstructionRegression.before(bee, getClass().getName(), receiver, bound);
    try {
      int value = original.call(receiver, bound);
      BeeConstructionRegression.returned(call, value);
      return value;
    } catch (RuntimeException | Error failure) {
      BeeConstructionRegression.failed(call, failure);
      throw failure;
    }
  }
}
