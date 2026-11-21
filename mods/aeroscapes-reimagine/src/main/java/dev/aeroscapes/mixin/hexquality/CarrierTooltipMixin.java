package dev.aeroscapes.mixin.hexquality;

import at.petrak.hexcasting.common.items.magic.ItemMediaHolder;
import dev.aeroscapes.equipment.hex.HexCastingReports;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemMediaHolder.class, remap = false)
abstract class CarrierTooltipMixin {
  @Inject(method = "appendHoverText", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$charge(
      ItemStack stack,
      Item.TooltipContext context,
      List<Component> lines,
      TooltipFlag flag,
      CallbackInfo ci) {
    HexCastingReports.carrierTooltip(stack, lines, context.registries());
  }
}
