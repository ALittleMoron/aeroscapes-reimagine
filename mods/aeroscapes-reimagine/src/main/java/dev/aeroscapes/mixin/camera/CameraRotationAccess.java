package dev.aeroscapes.mixin.camera;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Camera.class, priority = 900)
public interface CameraRotationAccess {
  @Invoker("setRotation")
  void aeroscapes$setRotation(float yaw, float pitch);
}
