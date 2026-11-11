package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.network.ServerboundUseWelderPacket", remap = false)
abstract class CannonWelderReforgeMixin {
  @WrapOperation(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$weldWear(
      ItemStack welder, int wear, LivingEntity user, EquipmentSlot slot, Operation<Void> original) {
    if (wear > 0
        && user instanceof ServerPlayer player
        && !(player instanceof FakePlayer)
        && !player.hasInfiniteMaterials()
        && welder.isDamageableItem()) {
      wear = aeroscapes$scaleWeldWear(welder, player, wear);
    }
    original.call(welder, wear, user, slot);
  }

  @Unique
  private static int aeroscapes$scaleWeldWear(ItemStack welder, ServerPlayer player, int wear) {
    int percent =
        switch (ReforgeProfiles.basePath(welder)) {
          case "cannon_welding_poor" -> 115;
          case "cannon_welding_fine" -> 90;
          case "cannon_welding_exceptional" -> 80;
          case "cannon_welding_epic" -> 70;
          case "cannon_welding_legendary" -> 60;
          default -> 100;
        };
    long scaled = (long) wear * percent;
    long whole = scaled / 100;
    long remainder = scaled % 100;
    if (remainder != 0 && player.getRandom().nextInt(100) < remainder) whole++;
    return (int) Math.min(whole, Integer.MAX_VALUE);
  }
}
