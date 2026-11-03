package dev.aeroscapes.mixin.movement;

import com.yori3o.yo_hooks.common.client.render.LegacyHookRenderer;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import dev.aeroscapes.movement.HookLineAttachment;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LegacyHookRenderer.class, remap = false)
public abstract class HookLineMixin {
  @Inject(method = "getHandPosition", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep native third-person, VR and absent-capture paths.
  private static void aeroscapes$handleAnchor(
      HookEntity hook,
      Player owner,
      float partialTick,
      EntityRenderDispatcher dispatcher,
      boolean vr,
      CallbackInfoReturnable<Vec3> callback) {
    if (vr) return;
    Vec3 anchor = HookLineAttachment.getWorldAnchor(hook, owner);
    if (anchor != null) callback.setReturnValue(anchor);
  }
}
