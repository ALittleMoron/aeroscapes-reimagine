package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.CampfireSootGrowth;
import dev.aeroscapes.weathering.SootSources;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CampfireSootGrowth.class, remap = false)
public abstract class CampfireSootSourcesMixin {
  @WrapMethod(method = "tryGrowing")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Supply the native soot rule with ordinary campfire parameters.
  private void aeroscapes$useCampfireSoot(
      BlockPos position,
      BlockState source,
      ServerLevel level,
      Supplier<Holder<Biome>> biome,
      Operation<Void> original) {
    if (!SootSources.isSource(source)) {
      original.call(position, source, level, biome);
    } else if (SootSources.randomTicks(source)) {
      BlockState ordinaryCampfire =
          Blocks.CAMPFIRE
              .defaultBlockState()
              .setValue(CampfireBlock.LIT, true)
              .setValue(CampfireBlock.SIGNAL_FIRE, false);
      original.call(position, ordinaryCampfire, level, biome);
    }
  }
}
