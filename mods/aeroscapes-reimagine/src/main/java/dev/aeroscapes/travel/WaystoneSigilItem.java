package dev.aeroscapes.travel;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class WaystoneSigilItem extends Item {
  public WaystoneSigilItem(Properties properties) {
    super(properties);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("tooltip.aeroscapes_reimagine.waystone_sigil"));
  }
}
