package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 900)
public abstract class HorseMountedMiningScopeMixin {
  @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this native mount callback.
  private void aeroscapes$limitHorseMiningBoost(
      Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> callback) {
    if (!((Object) this instanceof ServerPlayer player)
        || player.getVehicle() instanceof AbstractHorse) return;
    AttributeInstance breakingSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
    if (breakingSpeed != null) {
      // Horseman adds this modifier to every vehicle; retain it only for horse-type mounts.
      breakingSpeed.removeModifier(
          ResourceLocation.fromNamespaceAndPath("horseman", "mounted-break-speed"));
    }
  }
}
