package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.SwitchLinkPearlReforge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "wile.rsgauges.items.SwitchLinkPearlItem", remap = false)
abstract class SwitchLinkPearlTooltipMixin {
  @ModifyExpressionValue(
      method = "appendHoverText",
      at =
          @At(value = "INVOKE", target = "Lwile/rsgauges/ModConfig;max_switch_linking_distance()I"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$manualRange(int nativeLimit, @Local(argsOnly = true) ItemStack pearl) {
    return SwitchLinkPearlReforge.manualDistanceLimit(pearl, nativeLimit);
  }
}
