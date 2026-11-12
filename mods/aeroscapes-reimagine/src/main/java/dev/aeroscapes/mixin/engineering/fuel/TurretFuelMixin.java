package dev.aeroscapes.mixin.engineering.fuel;

import com.jesz.createdieselgenerators.content.turret.ChemicalTurretBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChemicalTurretBlockEntity.class, remap = false)
public abstract class TurretFuelMixin {
  @Inject(method = "shootFluids", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$requireWholeShot(CallbackInfo callback) {
    var turret = (ChemicalTurretBlockEntity) (Object) this;
    if (turret.tank.getPrimaryHandler().getFluidAmount() < 3) callback.cancel();
  }

  @Redirect(
      method = "shootFluids",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$payForShot(Level level, Entity projectile) {
    boolean spawned = level.addFreshEntity(projectile);
    if (spawned) {
      var turret = (ChemicalTurretBlockEntity) (Object) this;
      turret.tank.getPrimaryHandler().drain(3, IFluidHandler.FluidAction.EXECUTE);
      turret.setChanged();
    }
    return spawned;
  }

  @Redirect(
      method = "shootFluids",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/foundation/fluid/SmartFluidTank;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private FluidStack aeroscapes$skipPeriodicDebit(
      SmartFluidTank tank, int amount, IFluidHandler.FluidAction action) {
    return FluidStack.EMPTY;
  }
}
