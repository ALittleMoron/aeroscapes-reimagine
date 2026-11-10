package dev.aeroscapes.survival;

import com.dannyandson.nutritionalbalance.Config;
import com.dannyandson.nutritionalbalance.api.IPlayerNutrient;
import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import dev.aeroscapes.interfaceart.InterfaceArt;
import java.math.BigDecimal;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A shared, live nutrition panel for the standalone screen and lunch box. */
public final class NutritionPanel {
  private NutritionPanel() {}

  public static void render(GuiGraphics graphics, int left, int top) {
    Minecraft client = Minecraft.getInstance();
    if (client.player == null) return;
    Font font = client.font;
    var nutrition =
        PlayerNutritionData.getWorldNutritionData().getNutritionalBalancePlayer(client.player);
    boolean atlas = InterfaceArt.available(InterfaceArt.ATLAS);
    graphics.fill(left, top, left + 250, top + 121, atlas ? 0xff111b29 : 0xff292d30);
    graphics.fill(left + 1, top + 1, left + 249, top + 120, atlas ? 0xffc1ccd7 : 0xffd2cbb8);
    graphics.fill(left + 1, top + 1, left + 249, top + 19, atlas ? 0xff263548 : 0xff3a5754);
    if (atlas) {
      graphics.hLine(left + 225, left + 231, top + 10, 0xffc49b66);
      graphics.vLine(left + 228, top + 7, top + 13, 0xffc49b66);
    }
    graphics.drawCenteredString(
        font,
        Component.translatable("aeroscapes_reimagine.survival.nutrition"),
        left + 125,
        top + 7,
        0xfff1e8d0);
    graphics.drawString(
        font,
        Component.translatable(
            "aeroscapes_reimagine.survival.status."
                + nutrition.getCachedStatus().name().toLowerCase(Locale.ROOT),
            BigDecimal.valueOf(-Config.MALNOURISHED_ATTACK_DAMAGE.get())
                .stripTrailingZeros()
                .toPlainString()),
        left + 9,
        top + 22,
        0xff323d3d,
        false);
    int row = 0;
    for (IPlayerNutrient nutrient : nutrition.getPlayerNutrients()) {
      int y = top + 38 + row * 16;
      int color =
          switch (nutrient.getStatus()) {
            case MALNOURISHED -> 0xffb15c48;
            case ENGORGED -> 0xffb18a48;
            case ON_TARGET -> 0xff4d897b;
            case SAFE -> 0xff6d8886;
          };
      graphics.drawString(
          font,
          Component.translatable("nutritionalbalance.nutrient." + nutrient.getNutrientName()),
          left + 9,
          y,
          0xff252d2d,
          false);
      int barLeft = left + 96;
      int barWidth = 102;
      graphics.fill(barLeft, y + 1, barLeft + barWidth, y + 9, 0xff4b5151);
      int fill =
          Mth.clamp(
              Math.round(nutrient.getValue() / Config.NUTRIENT_MAX.get().floatValue() * barWidth),
              0,
              barWidth);
      graphics.fill(barLeft, y + 1, barLeft + fill, y + 9, color);
      graphics.drawString(
          font,
          Integer.toString(Math.round(nutrient.getValue())),
          left + 208,
          y,
          0xff252d2d,
          false);
      row++;
    }
  }

  public static void renderTooltip(
      GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
    Minecraft client = Minecraft.getInstance();
    if (client.player == null || mouseX < left + 7 || mouseX >= left + 243) return;
    var nutrients =
        PlayerNutritionData.getWorldNutritionData()
            .getNutritionalBalancePlayer(client.player)
            .getPlayerNutrients();
    for (int index = 0; index < nutrients.size(); index++) {
      int y = top + 38 + index * 16;
      if (mouseY < y - 1 || mouseY >= y + 13) continue;
      Component tooltip =
          nutrients.get(index).getNutrientName().equals("sugars")
              ? Component.translatable("aeroscapes_reimagine.survival.sugars_help")
              : Component.translatable(
                  "aeroscapes_reimagine.survival.group_help",
                  Config.NUTRIENT_MALNOURISHED.get().intValue(),
                  Config.NUTRIENT_LOW_TARGET.get().intValue(),
                  Config.NUTRIENT_TARGET_HIGH.get().intValue());
      int width = Math.max(1, Math.min(240, graphics.guiWidth() - 24));
      graphics.renderTooltip(client.font, client.font.split(tooltip, width), mouseX, mouseY);
    }
  }
}
