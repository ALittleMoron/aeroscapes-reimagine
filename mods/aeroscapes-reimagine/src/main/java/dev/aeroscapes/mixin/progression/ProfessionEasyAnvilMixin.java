package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ProfessionEffects;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModAnvilMenu.class, remap = false)
abstract class ProfessionEasyAnvilMixin extends AnvilMenu {
  protected ProfessionEasyAnvilMixin(int id, Inventory inventory) {
    super(id, inventory);
  }

  @ModifyArg(
      method =
          "createResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)V",
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
      index = 1,
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$materialUnit(double restored) {
    return ProfessionEffects.repairUnit(player, containerId, (int) restored);
  }

  @Inject(method = "createResult()V", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$repairPrice(CallbackInfo callback) {
    ProfessionEffects.finishRepair(this, player);
  }
}
