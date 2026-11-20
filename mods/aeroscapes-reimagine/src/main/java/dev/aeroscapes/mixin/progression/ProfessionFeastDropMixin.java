package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ProfessionEffects;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.block.FeastBlock;

@Mixin(value = FeastBlock.class, remap = false)
abstract class ProfessionFeastDropMixin extends Block {
  protected ProfessionFeastDropMixin(Properties properties) {
    super(properties);
  }

  @Override
  protected List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
    var drops = super.getDrops(state, context);
    int traits = state.getOptionalValue(ProfessionEffects.FEAST_TRAITS).orElse(0);
    if (traits != 0) {
      drops.replaceAll(
          stack -> stack.is(asItem()) ? ProfessionEffects.prepare(stack, traits) : stack);
    }
    return drops;
  }
}
