package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import java.util.ArrayList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BacktankUtil.class, remap = false)
abstract class BacktankTrainingMixin {
  @WrapOperation(
      method = "lambda$static$0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;getArmorSlots()Ljava/lang/Iterable;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Iterable<ItemStack> aeroscapes$trainedBacktanks(
      LivingEntity wearer, Operation<Iterable<ItemStack>> original) {
    var slots = original.call(wearer);
    if (!(wearer instanceof Player player)) return slots;
    var active = new ArrayList<ItemStack>();
    for (var stack : slots) {
      if (!ArmorProficiency.undertrained(player, stack)) active.add(stack);
    }
    return active;
  }
}
