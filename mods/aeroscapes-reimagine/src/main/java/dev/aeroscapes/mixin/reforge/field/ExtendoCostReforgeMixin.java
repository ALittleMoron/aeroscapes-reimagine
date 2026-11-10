package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.field.ExtendoReforges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem", remap = false)
abstract class ExtendoCostReforgeMixin {
  @WrapOperation(
      method = "consumeDurabilityOnPlace",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/equipment/extendoGrip/ExtendoGripItem;findAndDamageExtendoGrip(Lnet/minecraft/world/entity/player/Player;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$placementCost(
      Player player,
      Operation<Void> original,
      @Local(argsOnly = true) BlockEvent.EntityPlaceEvent event) {
    int uses =
        player instanceof ServerPlayer serverPlayer
            ? ExtendoReforges.cost(serverPlayer, event.getPlacedBlock())
            : 1;
    for (int index = 0; index < uses; index++) original.call(player);
  }
}
