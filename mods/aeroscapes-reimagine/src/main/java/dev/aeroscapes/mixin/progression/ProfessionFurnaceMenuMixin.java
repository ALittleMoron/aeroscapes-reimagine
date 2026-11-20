package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.races.ProfessionFurnace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
abstract class ProfessionFurnaceMenuMixin {
  @WrapOperation(
      method = "clicked",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$loadFurnace(
      AbstractContainerMenu menu,
      int slot,
      int button,
      ClickType click,
      Player player,
      Operation<Void> original) {
    if (menu instanceof AbstractFurnaceMenu
        && player instanceof ServerPlayer cook
        && menu.getSlot(0).container instanceof AbstractFurnaceBlockEntity furnace) {
      var input = new ProfessionFurnace(furnace);
      var previous = furnace.getItem(0);
      original.call(menu, slot, button, click, player);
      boolean exchanged = slot == 0 && click == ClickType.SWAP && previous != furnace.getItem(0);
      input.afterClick(furnace, cook, exchanged);
    } else original.call(menu, slot, button, click, player);
  }
}
