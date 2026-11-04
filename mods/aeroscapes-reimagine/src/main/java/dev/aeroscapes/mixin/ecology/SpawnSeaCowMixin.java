package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import dev.aeroscapes.ecology.SpawnWaterline;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.ninni.spawn.server.entity.mob.mammal.SeaCow", remap = false)
public abstract class SpawnSeaCowMixin {
  @WrapMethod(method = "checkSeaCowSpawnRules")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Flukeshroom's registered predicate also delegates here.
  private static boolean aeroscapes$localWater(
      EntityType<?> type,
      ServerLevelAccessor level,
      MobSpawnType reason,
      BlockPos position,
      RandomSource random,
      Operation<Boolean> original) {
    return ScopedSeaLevel.call(
        SpawnWaterline.at(level, position),
        () -> original.call(type, level, reason, position, random));
  }
}
