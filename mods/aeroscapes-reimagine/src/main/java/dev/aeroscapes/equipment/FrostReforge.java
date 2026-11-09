package dev.aeroscapes.equipment;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class FrostReforge {
  private static final ResourceLocation RANK =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "melee_legendary_frost");

  private FrostReforge() {}

  public static boolean matches(ItemStack stack) {
    return RANK.equals(TieredNeoAPI.getTierId(stack));
  }

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    if (ModList.get().isLoaded("tieredneo") && matches(event.getItemStack())) {
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.equipment.frost.description")
                  .withStyle(ChatFormatting.GRAY));
    }
  }
}
