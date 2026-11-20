package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.races.ProfessionRecipeTransfer;
import dev.emi.emi.network.FillRecipeC2SPacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FillRecipeC2SPacket.class, remap = false)
abstract class ProfessionEmiTransferMixin {
  @WrapMethod(method = "apply")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$loadIngredients(Player player, Operation<Void> original) {
    var record = ProfessionRecipeTransfer.afterTransfer(player);
    original.call(player);
    record.run();
  }
}
