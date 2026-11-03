package dev.aeroscapes.mixin.aquatic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchy.client.render.FishingLineAttachment;

@Mixin(
    targets = "com.li64.tide.registries.entities.misc.fishing.TideFishingHookRenderer",
    remap = false)
public abstract class TideRodLineMixin {
  @Inject(method = "getPlayerHandPos", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by Tide's separate line renderer.
  private void aeroscapes$renderedTip(
      Player player, float animation, float partialTick, CallbackInfoReturnable<Vec3> callback) {
    if (!(player instanceof LocalPlayer local)
        || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
    HumanoidArm arm =
        player.getMainHandItem().getItem() instanceof FishingRodItem
            ? player.getMainArm()
            : player.getMainArm().getOpposite();
    Vec3 anchor = FishingLineAttachment.getWorldAnchor(local, arm);
    if (anchor != null) callback.setReturnValue(anchor);
  }
}
