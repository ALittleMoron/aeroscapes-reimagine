package dev.aeroscapes.mixin.weathering;

import dev.aeroscapes.weathering.MasonryWeathering;
import dev.aeroscapes.weathering.PinkSandstoneErosion;
import dev.aeroscapes.weathering.SootSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WeatheringRandomTicksMixin {
  @Shadow private boolean isRandomlyTicking;

  @Shadow
  protected abstract BlockState asState();

  @Inject(method = "initCache", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Let chunk sections count these otherwise inert states.
  private void aeroscapes$allowWeatheringTicks(CallbackInfo callback) {
    if (!isRandomlyTicking) {
      BlockState state = asState();
      isRandomlyTicking =
          MasonryWeathering.randomTicks(state)
              || PinkSandstoneErosion.randomTicks(state)
              || SootSources.randomTicks(state);
    }
  }

  @Inject(method = "randomTick", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$weatherMasonry(
      ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
    MasonryWeathering.tick(asState(), level, pos, random);
  }
}
