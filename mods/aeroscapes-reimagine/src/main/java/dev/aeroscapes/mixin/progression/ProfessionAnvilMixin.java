package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

  @Inject(method = "createResult", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$repairPrice(CallbackInfo callback) {
    AnvilMenu menu = (AnvilMenu) (Object) this;
    if (menu.getClass() == AnvilMenu.class) ProfessionEffects.finishRepair(menu, player);
  }
}
