package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.enchanting.AnvilEnchantments;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
abstract class ProfessionAnvilMixin extends ItemCombinerMenu {
  protected ProfessionAnvilMixin(
      MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
    super(type, id, inventory, access);
  }

  @ModifyArg(
      method = "createResult",
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"),
      index = 1,
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$repairUnit(int restored) {
    return ProfessionEffects.repairUnit(player, containerId, restored);
  }

  @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$preventBookMerge(CallbackInfo callback) {
    AnvilMenu menu = (AnvilMenu) (Object) this;
    if (menu.getClass() == AnvilMenu.class && AnvilEnchantments.rejectBookMerge(menu))
      callback.cancel();
  }

  @Inject(method = "createResult", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$repairPrice(CallbackInfo callback) {
    AnvilMenu menu = (AnvilMenu) (Object) this;
    if (menu.getClass() == AnvilMenu.class) {
      AnvilEnchantments.enforceProgression(menu, player);
      ProfessionEffects.finishRepair(menu, player);
    }
  }

  @Inject(
      method = "createResult",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/inventory/AnvilMenu;broadcastChanges()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$limitAnvilPreview(CallbackInfo callback) {
    AnvilMenu menu = (AnvilMenu) (Object) this;
    if (menu.containerId >= 0) AnvilEnchantments.enforceProgression(menu, player);
  }

  @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$checkAnvilKnowledge(
      Player taker, boolean hasResult, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()
        && !AnvilEnchantments.allowsOutput((AnvilMenu) (Object) this, taker))
      callback.setReturnValue(false);
  }
}
