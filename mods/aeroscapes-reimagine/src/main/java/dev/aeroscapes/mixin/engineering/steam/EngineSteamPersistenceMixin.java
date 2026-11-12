package dev.aeroscapes.mixin.engineering.steam;

import com.teammoeg.steampowered.content.boiler.BoilerTileEntity;
import com.teammoeg.steampowered.content.engine.SteamEngineTileEntity;
import com.teammoeg.steampowered.oldcreatestuff.OldEngineBlock;
import dev.aeroscapes.engineering.SteamFluids;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SteamEngineTileEntity.class, remap = false)
public abstract class EngineSteamPersistenceMixin {
  @Shadow private FluidTank tank;

  @Inject(method = "read", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Normalize steam supplied by earlier native canisters.
  private void aeroscapes$normalizeSteam(
      CompoundTag data, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo info) {
    SteamFluids.normalize(tank);
  }

  @Redirect(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native plain tanks do not dirty their owners.
  private FluidStack aeroscapes$persistSteamDebit(
      FluidTank storage, int amount, IFluidHandler.FluidAction action) {
    FluidStack drained = storage.drain(amount, action);
    if (action == IFluidHandler.FluidAction.EXECUTE && !drained.isEmpty()) {
      var engine = (SteamEngineTileEntity) (Object) this;
      if (storage == tank) {
        engine.setChanged();
      } else if (engine.getLevel() != null) {
        var source =
            engine
                .getBlockPos()
                .relative(engine.getBlockState().getValue(OldEngineBlock.FACING), -1);
        if (engine.getLevel().getBlockEntity(source) instanceof BoilerTileEntity boiler
            && boiler.output == storage) boiler.setChanged();
      }
    }
    return drained;
  }
}
