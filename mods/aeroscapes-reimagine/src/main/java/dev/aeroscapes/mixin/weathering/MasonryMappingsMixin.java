package dev.aeroscapes.mixin.weathering;

import com.google.common.collect.ImmutableBiMap;
import com.ordana.immersive_weathering.IWPlatformStuff;
import dev.aeroscapes.weathering.MasonryWeathering;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IWPlatformStuff.class, remap = false)
public abstract class MasonryMappingsMixin {
  @Inject(method = "addExtraMossyBlocks", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // These are the port's extension points.
  private static void aeroscapes$extendMoss(
      ImmutableBiMap.Builder<Block, Block> pairs, CallbackInfo callback) {
    MasonryWeathering.extendMoss(pairs);
  }

  @Inject(method = "addExtraCrackedBlocks", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$extendCracks(
      ImmutableBiMap.Builder<Block, Block> pairs, CallbackInfo callback) {
    MasonryWeathering.extendCracks(pairs);
  }
}
