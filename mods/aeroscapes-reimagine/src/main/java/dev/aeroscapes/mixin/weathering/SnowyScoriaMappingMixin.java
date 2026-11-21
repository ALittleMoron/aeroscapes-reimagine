package dev.aeroscapes.mixin.weathering;

import com.google.common.collect.BiMap;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.ordana.immersive_weathering.blocks.snowy.Snowy;
import dev.aeroscapes.weathering.SnowyScoriaBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Snowy.class, remap = false)
public interface SnowyScoriaMappingMixin {
  @ModifyReturnValue(method = "lambda$static$0", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Before the native memoized map and inverse are published.
  private static BiMap<Block, Block> aeroscapes$addScoria(BiMap<Block, Block> original) {
    return SnowyScoriaBlock.extendConversions(original);
  }
}
