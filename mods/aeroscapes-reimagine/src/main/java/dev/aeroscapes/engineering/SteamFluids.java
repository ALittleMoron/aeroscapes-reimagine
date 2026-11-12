package dev.aeroscapes.engineering;

import com.teammoeg.steampowered.registrate.SPFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class SteamFluids {
  private SteamFluids() {}

  public static Fluid source(Fluid fluid) {
    return fluid == SPFluids.STEAM.get() ? SPFluids.STEAM.get().getSource() : fluid;
  }

  public static void normalize(FluidTank tank) {
    FluidStack stored = tank.getFluid();
    Fluid source = source(stored.getFluid());
    if (stored.isEmpty() || source == stored.getFluid()) return;
    FluidStack normalized = new FluidStack(source, stored.getAmount());
    normalized.applyComponents(stored.getComponentsPatch());
    tank.setFluid(normalized);
  }
}
