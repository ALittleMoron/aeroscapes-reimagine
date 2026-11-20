package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.races.ProfessionPot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

@Mixin(AbstractContainerMenu.class)
abstract class ProfessionPotMenuMixin {
  @WrapOperation(
      method = "clicked",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$potIngredients(
      AbstractContainerMenu menu,
      int slot,
      int button,
      ClickType click,
      Player player,
      Operation<Void> original) {
    if (menu instanceof CookingPotMenu pot
        && player instanceof ServerPlayer cook
        && !(cook instanceof FakePlayer)) {
      var ingredients = new ProfessionPot(pot.blockEntity);
      boolean swappingIngredient = slot >= 0 && slot < 6 && click == ClickType.SWAP;
      var previous = swappingIngredient ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
      original.call(menu, slot, button, click, player);
      int exchanged = swappingIngredient && previous != menu.getSlot(slot).getItem() ? slot : -1;
      ingredients.afterClick(pot.blockEntity, cook, exchanged);
    } else original.call(menu, slot, button, click, player);
  }
}
