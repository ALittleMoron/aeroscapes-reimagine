package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.movement.MovementLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.createmod.catnip.config.ui.SubMenuConfigScreen", remap = false)
public abstract class ParagliderForeignConfigurationTitleMixin {
  @ModifyExpressionValue(
      method = "renderWindow",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/createmod/catnip/config/ui/SubMenuConfigScreen;title:Ljava/lang/String;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The stored title and config path remain native.
  private String aeroscapes$translateMovementTitle(String source) {
    return MovementLanguage.foreignState(CatnipConfigurationAccess.aeroscapes$modId(), source);
  }
}
