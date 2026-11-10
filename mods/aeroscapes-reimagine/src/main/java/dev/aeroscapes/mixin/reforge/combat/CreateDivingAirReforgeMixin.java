package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.armor.DivingHelmetItem;
import dev.aeroscapes.equipment.combat.ArmorSpecialties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DivingHelmetItem.class, remap = false)
abstract class CreateDivingAirReforgeMixin {
  @WrapOperation(
      method = "breatheUnderwater",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/equipment/armor/BacktankUtil;consumeAir(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;I)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$divingAirCost(
      LivingEntity entity, ItemStack tank, int cost, Operation<Void> original) {
    int adjusted =
        entity instanceof ServerPlayer player ? ArmorSpecialties.tankAirCost(player, cost) : cost;
    if (adjusted > 0) original.call(entity, tank, adjusted);
  }
}
