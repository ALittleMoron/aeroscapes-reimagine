package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.PickupRequests;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.itemphysic.common.packet.PickupPacket;

@Mixin(value = PickupPacket.class, remap = false)
public abstract class PickupPacketMixin {
  @Shadow public UUID uuid;

  @Inject(method = "executeServer", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native packet otherwise trusts any loaded UUID.
  private void aeroscapes$validatePickup(ServerPlayer player, CallbackInfo callback) {
    if (uuid == null
        || !(player.serverLevel().getEntity(uuid) instanceof ItemEntity item)
        || !PickupRequests.canPickup(player, item)) callback.cancel();
  }
}
