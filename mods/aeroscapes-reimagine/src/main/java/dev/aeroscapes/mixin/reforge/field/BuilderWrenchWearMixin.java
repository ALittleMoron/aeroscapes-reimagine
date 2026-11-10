package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.items.WrenchItem", remap = false)
abstract class BuilderWrenchWearMixin {
  @ModifyArg(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$turningWear(int wear, @Local(argsOnly = true) UseOnContext context) {
    var player = context.getPlayer();
    if (player == null) return wear;
    int chance =
        switch (ReforgeProfiles.path(context.getItemInHand())) {
          case "builder_fine_ready" -> 15;
          case "builder_epic_repeat", "builder_legendary_layout" -> 25;
          default -> 0;
        };
    return ToolReforges.modifyWear(player, wear, chance);
  }
}
