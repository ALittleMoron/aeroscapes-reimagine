package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import dev.aeroscapes.movement.DeferredRenderOrder;
import dev.aeroscapes.movement.HookLineAttachment;
import java.util.Iterator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1000)
public abstract class HookRenderOrderMixin {
  @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Outside Iris' priority-999 entity grouping.
  private Iterator<Entity> aeroscapes$ownersBeforeHooks(
      Iterable<Entity> entities, Operation<Iterator<Entity>> original) {
    HookLineAttachment.worldEntities(true);
    return DeferredRenderOrder.last(
        original.call(entities), entity -> entity instanceof HookEntity);
  }

  @Inject(method = "renderLevel", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Exclude inventory previews and later UI renders.
  private void aeroscapes$endEntities(CallbackInfo callback) {
    HookLineAttachment.worldEntities(false);
  }
}
