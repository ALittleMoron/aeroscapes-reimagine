package dev.aeroscapes.travel;

import net.blay09.mods.waystones.api.client.WaystonesClientAPI;
import net.blay09.mods.waystones.client.requirement.RequirementRenderer;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WaystoneCostDisplay implements RequirementRenderer<ExperiencePointsRequirement> {
  private WaystoneCostDisplay() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    if (ModList.get().isLoaded("waystones")) {
      event.enqueueWork(
          () ->
              WaystonesClientAPI.registerRequirementRenderer(
                  ExperiencePointsRequirement.class, new WaystoneCostDisplay()));
    }
  }

  @Override
  public void renderWidget(
      Player player,
      ExperiencePointsRequirement requirement,
      GuiGraphics graphics,
      int mouseX,
      int mouseY,
      float partialTicks,
      int x,
      int y) {
    var font = Minecraft.getInstance().font;
    int color =
        player.getAbilities().instabuild || requirement.canAfford(player) ? 0xff80ff20 : 0xffff6060;
    graphics.drawString(font, Integer.toString(requirement.getPoints()), x, y - 1, color, false);
    graphics.drawString(
        font,
        Component.translatable("gui.aeroscapes_reimagine.travel.xp_short"),
        x,
        y + 8,
        color,
        false);
  }

  @Override
  public int getWidth(Player player, ExperiencePointsRequirement requirement) {
    var font = Minecraft.getInstance().font;
    return Math.max(
        font.width(Integer.toString(requirement.getPoints())),
        font.width(Component.translatable("gui.aeroscapes_reimagine.travel.xp_short")));
  }
}
