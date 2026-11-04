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

@Mixin(
    targets = {
      "com.ninni.spawn.server.entity.mob.fictional.Barbed",
      "com.ninni.spawn.server.entity.mob.arthropod.SpiderCrab",
      "net.minecraft.world.entity.monster.Drowned"
    })
public abstract class SpawnHostileMixin {
  @WrapMethod(
      method = {"checkBarbedSpawnRules", "checkSpiderCrabSpawnRules", "checkDrownedSpawnRules"})
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Includes Spawn's injected drowned land predicate and native
  // deep-water branch.
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
