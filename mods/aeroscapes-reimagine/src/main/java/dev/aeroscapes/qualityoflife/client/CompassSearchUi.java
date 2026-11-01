package dev.aeroscapes.qualityoflife.client;

import dev.aeroscapes.qualityoflife.CompassSearchNetwork;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CompassSearchUi {
  private CompassSearchUi() {}

  public static Component rangeLabel(boolean extended) {
    return Component.translatable(
        "aeroscapes_reimagine.compass." + (extended ? "range_far" : "range_free"));
  }

  public static Button rangeButton(boolean biome, boolean extended, Consumer<Boolean> changed) {
    boolean[] selected = {extended};
    Button button =
        Button.builder(
                rangeLabel(extended),
                pressed -> {
                  selected[0] = !selected[0];
                  changed.accept(selected[0]);
                  pressed.setMessage(rangeLabel(selected[0]));
                })
            .bounds(10, biome ? 130 : 155, 110, 20)
            .build();
    Component help = Component.translatable("aeroscapes_reimagine.compass.range_help");
    if (biome)
      help =
          help.copy()
              .append("\n\n")
              .append(Component.translatable("aeroscapes_reimagine.compass.biome_help"));
    button.setTooltip(Tooltip.create(help));
    return button;
  }

  public static void renderPrices(GuiGraphics graphics, Font font, boolean biome) {
    int top = biome ? 154 : 179;
    graphics.drawString(
        font, Component.translatable("aeroscapes_reimagine.compass.price_near"), 10, top, 0xdddddd);
    graphics.drawString(
        font,
        Component.translatable("aeroscapes_reimagine.compass.price_far"),
        10,
        top + 10,
        0xdddddd);
    graphics.drawString(
        font,
        Component.translatable("aeroscapes_reimagine.compass.price_failure"),
        10,
        top + 20,
        0xdddddd);
  }

  public static void send(
      boolean biome, ResourceLocation target, boolean group, boolean next, boolean extended) {
    Minecraft client = Minecraft.getInstance();
    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    PacketDistributor.sendToServer(
        new CompassSearchNetwork.Request(biome, target, group, next, extended));
    client.setScreen(null);
  }
}
