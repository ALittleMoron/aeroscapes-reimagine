package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.RodReforges;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingRodItem.class)
abstract class VanillaRodCastReforgeMixin {
  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$recordCast(
      Level level,
      Entity entity,
      Operation<Boolean> original,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) InteractionHand hand) {
    String profile = ReforgeProfiles.path(player.getItemInHand(hand));
    if (entity instanceof RodReforges.VanillaCast cast
        && (profile.contains("_with_") || profile.equals("rod_legendary_salvager"))) {
      cast.aeroscapes$rememberRodProfile(profile);
    }
    return original.call(level, entity);
  }

  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$skipRodWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original) {
    if (ReforgeProfiles.has(stack, "rod_exceptional_bite_with_conservation"))
      amount = ToolReforges.modifyWear(user, amount, 15);
    original.call(stack, amount, user, slot);
  }
}
