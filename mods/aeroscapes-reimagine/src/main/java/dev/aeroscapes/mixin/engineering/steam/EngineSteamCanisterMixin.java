package dev.aeroscapes.mixin.engineering.steam;

import com.teammoeg.steampowered.content.engine.SteamEngineBlock;
import com.teammoeg.steampowered.registrate.SPFluids;
import com.teammoeg.steampowered.registrate.SPItems;
import dev.aeroscapes.engineering.SteamFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SteamEngineBlock.class, remap = false)
public abstract class EngineSteamCanisterMixin {
  @ModifyArg(
      method = "useItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/fluids/FluidStack;<init>(Lnet/minecraft/world/level/material/Fluid;I)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Canisters and piped steam must fill the same tank.
  private Fluid aeroscapes$sourceSteam(Fluid steam) {
    return SteamFluids.source(steam);
  }

  @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native interaction consumes even rejected fills.
  private void aeroscapes$requireFullCanisterCapacity(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<ItemInteractionResult> result) {
    if (!stack.is(SPItems.PRESSURIZED_STEAM_CONTAINER.get())) return;
    if (level.isClientSide) {
      result.setReturnValue(ItemInteractionResult.SUCCESS);
      return;
    }
    IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    FluidStack steam = new FluidStack(SteamFluids.source(SPFluids.STEAM.get()), 1000);
    if (tank == null || tank.fill(steam, IFluidHandler.FluidAction.SIMULATE) != 1000)
      result.setReturnValue(ItemInteractionResult.FAIL);
  }
}
