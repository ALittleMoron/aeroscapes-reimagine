package dev.aeroscapes.mixin.atmosphere;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper.Type;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.OrientedBoundingBox", remap = false)
abstract class BetterCombatShipAimMixin {
  @Shadow public Vec3 axisX;
  @Shadow public Vec3 axisY;
  @Shadow public Vec3 axisZ;

  @Inject(
      method = "<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FF)V",
      at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Better Combat constructs this signature only for the local
  // attack volume.
  private void aeroscapes$inheritShipOrientation(CallbackInfo callback) {
    var player = Minecraft.getInstance().player;
    if (player == null) return;
    var orientation =
        EntitySubLevelRotationHelper.getEntityOrientation(
            player,
            player.level() instanceof LevelPoseProviderExtension poses
                ? poses::sable$getPose
                : SubLevel::logicalPose,
            0,
            Type.CAMERA);
    if (orientation == null) return;
    axisX = JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(axisX)));
    axisY = JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(axisY)));
    axisZ = JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(axisZ)));
  }
}
