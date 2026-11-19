package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SpellBook.class, remap = false)
abstract class BookFocusTooltipMixin {
  @WrapOperation(
      method = "appendHoverText",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;isSpellContainer(Lnet/minecraft/world/item/ItemStack;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$personalPreparation(ItemStack stack, Operation<Boolean> original) {
    // The item's old slot count and inscribed list no longer describe personal knowledge.
    return false;
  }
}
