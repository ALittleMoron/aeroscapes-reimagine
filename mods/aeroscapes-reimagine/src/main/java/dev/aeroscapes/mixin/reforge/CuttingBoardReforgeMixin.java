package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

@Mixin(value = CuttingBoardBlockEntity.class, remap = false)
abstract class CuttingBoardReforgeMixin {
  @WrapOperation(
      method = "lambda$processStoredItemUsingTool$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$saveKnifeWear(
      ItemStack knife,
      int wear,
      ServerLevel level,
      LivingEntity user,
      Consumer<Item> onBreak,
      Operation<Void> original) {
    float chance =
        switch (ReforgeProfiles.path(knife)) {
          case "knife_exceptional_saving" -> 0.12F;
          case "knife_epic", "knife_legendary_batch" -> 0.10F;
          default -> 0;
        };
    if (user == null || chance == 0 || user.getRandom().nextFloat() >= chance)
      original.call(knife, wear, level, user, onBreak);
  }
}
