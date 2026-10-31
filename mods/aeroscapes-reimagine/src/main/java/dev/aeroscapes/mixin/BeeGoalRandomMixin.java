package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = {
      "net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal",
      "net.minecraft.world.entity.animal.Bee$BeeGoToKnownFlowerGoal"
    })
public abstract class BeeGoalRandomMixin {
  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/world/level/Level;random:Lnet/minecraft/util/RandomSource;",
              opcode = Opcodes.GETFIELD))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native template loading constructs goals on workers.
  private RandomSource aeroscapesReimagine$beeGoalRandom(
      Level level, Operation<RandomSource> original, @Local(argsOnly = true) Bee bee) {
    RandomSource worldRandom = original.call(level);
    return level instanceof ServerLevel serverLevel && !serverLevel.getServer().isSameThread()
        ? bee.getRandom()
        : worldRandom;
  }
}
