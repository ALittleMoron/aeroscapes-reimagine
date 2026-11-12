package dev.aeroscapes.mixin.engineering.fuel;

import com.forsteri.createliquidfuel.core.IHasStomach;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlazeBurnerBlockEntity.class, priority = 900, remap = false)
public abstract class BurnerTankPersistenceMixin {
  @Unique private boolean aeroscapes$fluidChanged;

  @Inject(method = "addBehaviours", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$trackTank(List<BlockEntityBehaviour> behaviours, CallbackInfo callback) {
    var burner = (BlazeBurnerBlockEntity) (Object) this;
    var tank = ((IHasStomach) burner).getCapability();
    if (tank != null)
      ((SmartFluidTankCallbackAccess) tank)
          .aeroscapes$setCallback(
              contents -> {
                var level = burner.getLevel();
                if (level == null || level.isClientSide) return;
                burner.setChanged();
                aeroscapes$fluidChanged = true;
              });
  }

  @Inject(method = "tick", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$syncTank(CallbackInfo callback) {
    var burner = (BlazeBurnerBlockEntity) (Object) this;
    var level = burner.getLevel();
    if (level != null
        && !level.isClientSide
        && aeroscapes$fluidChanged
        && level.getGameTime() % 20 == 0) {
      aeroscapes$fluidChanged = false;
      burner.sendData();
    }
  }
}
