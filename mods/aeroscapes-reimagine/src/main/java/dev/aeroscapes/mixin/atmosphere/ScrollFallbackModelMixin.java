package dev.aeroscapes.mixin.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import io.redspace.ironsspellbooks.render.NBTOverrideItemModel;
import io.redspace.ironsspellbooks.render.ScrollModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = NBTOverrideItemModel.class, remap = false)
abstract class ScrollFallbackModelMixin implements BakedModel {
  @Shadow @Final private BakedModel original;

  @Override
  public BakedModel applyTransform(
      ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
    // School overrides already return their own model; this is the unassigned scroll path.
    if ((Object) this instanceof ScrollModel) {
      return original.applyTransform(context, poseStack, leftHand);
    }
    return BakedModel.super.applyTransform(context, poseStack, leftHand);
  }
}
