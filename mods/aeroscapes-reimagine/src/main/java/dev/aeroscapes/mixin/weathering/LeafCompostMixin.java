package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ordana.immersive_weathering.blocks.LeafPileBlock;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LeafPileBlock.class, remap = false)
public abstract class LeafCompostMixin {
  @WrapOperation(
      method = "randomTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z",
              ordinal = 0))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Bare dirt can still become loam or podzol.
  private boolean aeroscapes$compostBareDirt(
      BlockState ground, TagKey<Block> soilTag, Operation<Boolean> original) {
    return ground.is(Blocks.DIRT) && original.call(ground, soilTag);
  }
}
