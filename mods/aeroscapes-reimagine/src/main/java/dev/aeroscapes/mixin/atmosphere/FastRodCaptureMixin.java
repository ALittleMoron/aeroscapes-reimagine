package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.render.FishingLineAttachment;
import punchy.client.render.PunchyArmRenderer;

/** Capture the real mesh before Flerovium substitutes its empty vanilla-path model. */
@Mixin(
    targets = {
      "com.moepus.flerovium.functions.FastSimpleBakedModelRenderer",
      "com.moepus.flerovium.Iris.IrisSimpleBakedItemRenderer"
    },
    remap = false)
public abstract class FastRodCaptureMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/client/resources/model/SimpleBakedModel;ILnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lnet/minecraft/client/color/item/ItemColors;)V",
      at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at both pinned fast-render entry points.
  private static void aeroscapes$capture(
      CallbackInfo callback,
      @Local(argsOnly = true) SimpleBakedModel model,
      @Local(argsOnly = true) ItemStack stack,
      @Local(argsOnly = true) PoseStack pose) {
    if (stack.is(Items.FISHING_ROD) && PunchyArmRenderer.isCapturingItemRenderer()) {
      FishingLineAttachment.capture(model, stack, pose.last().pose());
    }
  }
}
