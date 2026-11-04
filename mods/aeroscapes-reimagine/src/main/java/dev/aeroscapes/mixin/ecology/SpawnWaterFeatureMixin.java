package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import dev.aeroscapes.ecology.SpawnWaterline;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(
    targets = {
      "com.ninni.spawn.server.level.feature.DeepReefRockFeature",
      "com.ninni.spawn.server.level.feature.DragonReedsFeature",
      "com.ninni.spawn.server.level.feature.StrandedSpawnFeature",
      "com.ninni.spawn.server.level.feature.WindsweptGrassFeature"
    },
    remap = false)
public abstract class SpawnWaterFeatureMixin {
  @WrapMethod(method = "place")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Includes each native feature's private decoration calls.
  private boolean aeroscapes$localWater(
      FeaturePlaceContext<?> context, Operation<Boolean> original) {
    return ScopedSeaLevel.call(
        SpawnWaterline.at(context.level(), context.origin()), () -> original.call(context));
  }
}
