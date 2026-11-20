package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CampfireBlockEntity.class)
abstract class ProfessionCampfireMixin {
  @Inject(
      method = "placeFood",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/entity/CampfireBlockEntity;markUpdated()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$placedFood(
      LivingEntity actor,
      ItemStack food,
      int ticks,
      CallbackInfoReturnable<Boolean> callback,
      @Local(index = 4) int slot) {
    ProfessionEffects.startCooking((CampfireBlockEntity) (Object) this, slot, actor, 1);
  }

  @ModifyArg(
      method = "cookTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$finishedFood(
      ItemStack result,
      @Local(argsOnly = true) CampfireBlockEntity fire,
      @Local(index = 5) int slot) {
    var meal = ProfessionEffects.cookedServing(fire, slot, result);
    ProfessionEffects.cookingSlotChanged(fire, slot, 0);
    return meal;
  }
}
