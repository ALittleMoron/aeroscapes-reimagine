package dev.aeroscapes.mixin.ecology;

import com.ninni.spawn.server.level.structure.DodoIslandStructure;
import dev.aeroscapes.ecology.SpawnIslandSites;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DodoIslandStructure.class)
public abstract class SpawnDodoCenterpieceMixin {
  @ModifyVariable(method = "placeCenterpiece", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native 75 is sea level 63 plus twelve blocks of scan clearance.
  private BlockPos aeroscapes$localCenterpiece(BlockPos position, WorldGenLevel level) {
    int sea =
        SpawnIslandSites.seaLevel(
            level.getLevel().getChunkSource().randomState(),
            position.getX(),
            position.getZ(),
            Integer.MIN_VALUE);
    return sea == Integer.MIN_VALUE ? position : position.atY(sea + 12);
  }
}
