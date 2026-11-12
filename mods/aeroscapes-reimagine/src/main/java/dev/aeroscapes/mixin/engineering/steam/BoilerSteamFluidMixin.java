package dev.aeroscapes.mixin.engineering.steam;

import com.teammoeg.steampowered.content.boiler.BoilerTileEntity;
import dev.aeroscapes.engineering.SteamFluids;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BoilerTileEntity.class, remap = false)
public abstract class BoilerSteamFluidMixin {
  @Shadow public FluidTank output;

  @ModifyArg(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/fluids/FluidStack;<init>(Lnet/minecraft/world/level/material/Fluid;I)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Machine storage and logistics use source fluids.
  private Fluid aeroscapes$sourceSteam(Fluid steam) {
    return SteamFluids.source(steam);
  }

  @Inject(method = "read", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve already stored boiler steam on reload.
  private void aeroscapes$normalizeSteam(
      CompoundTag data, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo info) {
    SteamFluids.normalize(output);
  }
}
