package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.reg.ModWaxables;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DataMapHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModWaxables.class, remap = false)
public abstract class WeatheringWaxMixin {
  @Inject(method = "getUnWaxed", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // NeoForge's data map contains iron and vanilla copper.
  private static void aeroscapesReimagine$nativeWaxMap(
      BlockState state, CallbackInfoReturnable<Optional<BlockState>> callback) {
    callback.setReturnValue(
        Optional.ofNullable(DataMapHooks.getBlockUnwaxed(state.getBlock()))
            .map(block -> block.withPropertiesOf(state)));
  }
}
