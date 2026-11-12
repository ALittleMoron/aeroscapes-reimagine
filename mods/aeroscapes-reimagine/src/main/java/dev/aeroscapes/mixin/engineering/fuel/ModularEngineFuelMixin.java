package dev.aeroscapes.mixin.engineering.fuel;

import com.jesz.createdieselgenerators.content.diesel_engine.modular.ModularDieselEngineBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModularDieselEngineBlockEntity.class, remap = false)
public abstract class ModularEngineFuelMixin {
  @Redirect(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/jesz/createdieselgenerators/content/diesel_engine/modular/ModularDieselEngineBlockEntity;getFuelThrottle()F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$chargeOnlyRunningServerEngine(ModularDieselEngineBlockEntity engine) {
    var level = engine.getLevel();
    return level != null && !level.isClientSide && engine.enabled() ? engine.getFuelThrottle() : 0;
  }
}
