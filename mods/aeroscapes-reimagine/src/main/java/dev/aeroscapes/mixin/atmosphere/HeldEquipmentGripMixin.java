package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.HeldEquipmentGrip;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.render.PunchyArmRenderer;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class HeldEquipmentGripMixin {
  @Shadow
  private static boolean isSlim(net.minecraft.client.player.AbstractClientPlayer player) {
    throw new AssertionError();
  }

  @Inject(method = "renderItemInHand", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Shared only within this render invocation.
  private static void aeroscapes$handRoot(
      ItemInHandRenderer renderer,
      PlayerModel<?> model,
      LocalPlayer player,
      HumanoidArm arm,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Matrix4f world,
      net.minecraft.client.model.geom.ModelPart geoArm,
      net.minecraft.client.model.geom.ModelPart geoItem,
      net.minecraft.client.model.geom.ModelPart geoGrip,
      float partialTick,
      CallbackInfo callback,
      @Share("aeroscapesHandRoot") LocalRef<Matrix4f> root) {
    root.set(new Matrix4f(pose.last().pose()));
  }

  @WrapOperation(
      method = "renderItemInHand",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve the caller pose and all unselected items.
  private static void aeroscapes$holdAtPalm(
      ItemInHandRenderer renderer,
      LivingEntity entity,
      ItemStack stack,
      ItemDisplayContext context,
      boolean left,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      Operation<Void> original,
      @Local(argsOnly = true) LocalPlayer player,
      @Share("aeroscapesHandRoot") LocalRef<Matrix4f> root) {
    boolean selected = HeldEquipmentGrip.usesPalm(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    var arm =
        selected
            ? PunchyArmRenderer.getCachedArmWorld(
                player, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT)
            : null;
    PoseStack held =
        arm == null ? pose : HeldEquipmentGrip.palm(root.get(), arm, left, isSlim(player));
    var heldContext =
        arm == null
            ? context
            : left
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    original.call(renderer, entity, stack, heldContext, left, held, buffers, light);
  }
}
