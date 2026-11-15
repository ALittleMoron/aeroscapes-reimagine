package dev.aeroscapes.mixin.agriculture;

import com.agricraft.agricraft.common.item.crafting.MagnifyingHelmetRecipe;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MagnifyingHelmetRecipe.class, remap = false)
public abstract class MagnifyingHelmetRecipeMixin {
  @Inject(
      method =
          "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
      at = @At("RETURN"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native matching ignores unrelated occupied slots.
  private void aeroscapes$preserveExtraIngredients(
      CraftingInput input, Level level, CallbackInfoReturnable<Boolean> callback) {
    if (!callback.getReturnValueZ()) return;
    int helmets = 0;
    int magnifiers = 0;
    for (int index = 0; index < input.size(); index++) {
      var stack = input.getItem(index);
      if (stack.isEmpty()) continue;
      if (stack.is(ModItems.MAGNIFYING_GLASS.get())) {
        magnifiers++;
      } else if (stack.getItem() instanceof ArmorItem armor
          && armor.getEquipmentSlot() == EquipmentSlot.HEAD) {
        helmets++;
      } else {
        callback.setReturnValue(false);
        return;
      }
    }
    if (helmets != 1 || magnifiers != 1) callback.setReturnValue(false);
  }
}
