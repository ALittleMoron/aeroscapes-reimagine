package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.LanternGrip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchy.client.render.PunchyArmRenderer;
import punchy.client.render.ToolTuning;

@Mixin(value = PunchyArmRenderer.class, remap = false)
public abstract class LanternGripMixin {
  @WrapOperation(
      method = "renderItemInHand",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/render/PunchyArmRenderer;applyItemTranslation(Lcom/mojang/blaze3d/vertex/PoseStack;Lpunchy/client/render/ToolTuning$Profile;Z)V"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Covers the visible item and native proxy pose.
  private static void aeroscapes$attachLantern(
      PoseStack pose,
      ToolTuning.Profile profile,
      boolean left,
      Operation<Void> original,
      @Local(argsOnly = true) LocalPlayer player,
      @Local(index = 14) ItemStack stack) {
    if (!stack.is(Items.LANTERN) && !stack.is(Items.SOUL_LANTERN)) {
      original.call(pose, profile, left);
      return;
    }
    String name = stack.is(Items.LANTERN) ? "lantern" : "soul_lantern";
    var resource =
        Minecraft.getInstance()
            .getResourceManager()
            .getResource(ResourceLocation.withDefaultNamespace("models/item/" + name + ".json"));
    if (profile == null
        || resource.isEmpty()
        || !resource.get().sourcePackId().equals("file/§eExtended_Illumina_NEA_1.21x.zip")) {
      original.call(pose, profile, left);
      return;
    }
    var context =
        left
            ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    var display = PunchyArmRenderer.resolveBaselineUseTransform(stack, context, player);
    if (display == null) {
      original.call(pose, profile, left);
      return;
    }
    PoseStack nativeDisplay = new PoseStack();
    display.apply(left, nativeDisplay);
    var translation =
        LanternGrip.translation(
            nativeDisplay.last().pose(), left, profile.offX, profile.offY, profile.offZ);
    pose.translate(translation.x, translation.y, translation.z);
  }
}
