package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.races.ProfessionRecipeTransfer;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "mezz.jei.common.transfer.BasicRecipeTransferHandlerServer", remap = false)
abstract class ProfessionJeiTransferMixin {
  @WrapMethod(method = "setItems")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$loadIngredients(
      Player player,
      List<?> operations,
      List<Slot> inputs,
      List<Slot> inventory,
      boolean maximum,
      boolean complete,
      Operation<Void> original) {
    var record = ProfessionRecipeTransfer.afterTransfer(player);
    original.call(player, operations, inputs, inventory, maximum, complete);
    record.run();
  }
}
