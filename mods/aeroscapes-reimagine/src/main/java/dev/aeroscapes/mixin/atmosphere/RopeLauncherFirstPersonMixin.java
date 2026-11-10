package dev.aeroscapes.mixin.atmosphere;

import com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.sprocketgames.create_aeronautics_throwable_rope_connector.client.RopeConnectorLauncherRenderHandler;
import net.sprocketgames.create_aeronautics_throwable_rope_connector.item.RopeConnectorLauncherItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.animation.PunchyAnimationManager;
import punchy.client.render.PunchyArmRenderer;
import punchy.client.state.HandEquipStateMachine;
import punchy.config.PunchyConfig;

@Mixin(value = ShootableGadgetRenderHandler.class, remap = false)
abstract class RopeLauncherFirstPersonMixin {
  @Inject(method = "onRenderPlayerHand", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$skipDuplicateLauncher(RenderHandEvent event, CallbackInfo callback) {
    if (!((Object) this instanceof RopeConnectorLauncherRenderHandler)
        || !(event.getItemStack().getItem() instanceof RopeConnectorLauncherItem)
        || !PunchyConfig.isModEnabled()) return;

    var client = Minecraft.getInstance();
    if (client.player == null || !client.options.getCameraType().isFirstPerson()) return;

    // The NeoForge event precedes the vanilla hand pass that Punchy normally suppresses.
    var hand = event.getHand();
    if (PunchyArmRenderer.shouldHideAboveInterface(client)
        || (!HandEquipStateMachine.shouldAllowVanillaHandOut(hand)
            && (!PunchyConfig.isHandBlacklisted(client.player, hand)
                || PunchyAnimationManager.shouldKeepBlacklistedOppositeHandForDualHanded(
                    client, hand)))) {
      event.setCanceled(true);
      callback.cancel();
    }
  }
}
