package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import harmonised.pmmo.client.events.TooltipHandler;
import harmonised.pmmo.setup.ClientSetup;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TooltipHandler.class, remap = false)
abstract class ProjectMmoTooltipMixin {
  // PMMO polls the raw key from a tooltip render, outside the focused text field's events.
  @WrapOperation(
      method = "onTooltip",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$itemGlossary(
      long window, int key, Operation<Boolean> original) {
    var binding = ClientSetup.OPEN_MENU;
    var modifier = binding.getKeyModifier();
    return !binding.isUnbound()
        && Screen.hasControlDown()
        && (modifier == KeyModifier.NONE || modifier.isActive(binding.getKeyConflictContext()))
        && original.call(window, key);
  }
}
