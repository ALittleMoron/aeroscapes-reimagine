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
import net.minecraft.world.entity.player.Player;

/** A shared, live nutrition panel for the standalone screen and lunch box. */
public final class NutritionPanel {
  private static final int NUTRIENT_Y = 49;
  private static final int NUTRIENT_STEP = 13;

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
    if (!renderPenalties(graphics, font, client.player, left, top)) {
      Component status;
      int deficient = NutritionDeficits.count(nutrition.getPlayerNutrients());
      var provider = nutrition.getCachedStatus();
      if (deficient > 0) status = text("status.malnourished", deficient);
      else {
        if (provider == IPlayerNutrient.NutrientStatus.MALNOURISHED)
          provider = IPlayerNutrient.NutrientStatus.SAFE;
        status = text("status." + provider.name().toLowerCase(Locale.ROOT));
      }
      graphics.drawString(font, status, left + 9, top + 29, 0xff323d3d, false);
    }
    int row = 0;
    for (IPlayerNutrient nutrient : nutrition.getPlayerNutrients()) {
      int y = top + NUTRIENT_Y + row * NUTRIENT_STEP;
      var groupStatus = nutrient.getStatus();
      int color =
          switch (groupStatus) {
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
      int rounded = Math.round(nutrient.getValue());
      float threshold = Config.NUTRIENT_MALNOURISHED.get().floatValue();
      String display =
          groupStatus == IPlayerNutrient.NutrientStatus.MALNOURISHED && rounded >= threshold
              ? "<"
                  + BigDecimal.valueOf(Config.NUTRIENT_MALNOURISHED.get())
                      .stripTrailingZeros()
                      .toPlainString()
              : Integer.toString(rounded);
      graphics.drawString(font, display, left + 208, y, 0xff252d2d, false);
      row++;
    }
  }

  public static void renderTooltip(
      GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
    Minecraft client = Minecraft.getInstance();
    if (client.player == null || mouseX < left + 7 || mouseX >= left + 243) return;
    int width = Math.max(1, Math.min(240, graphics.guiWidth() - 24));
    if (mouseY >= top + 20 && mouseY < top + 46) {
      var thresholds =
          text(
              "penalty_thresholds",
              attackReduction(),
              NutritionDeficits.MOVEMENT_TIER,
              NutritionDeficits.MOVEMENT_PERCENT,
              NutritionDeficits.MINING_TIER,
              NutritionDeficits.MINING_PERCENT);
      graphics.renderTooltip(client.font, client.font.split(thresholds, width), mouseX, mouseY);
      return;
    }
    var nutrients =
        PlayerNutritionData.getWorldNutritionData()
            .getNutritionalBalancePlayer(client.player)
            .getPlayerNutrients();
    for (int index = 0; index < nutrients.size(); index++) {
      int y = top + NUTRIENT_Y + index * NUTRIENT_STEP;
      if (mouseY < y - 1 || mouseY >= y + NUTRIENT_STEP - 1) continue;
      Component tooltip =
          text(
              "group_help",
              Config.NUTRIENT_MALNOURISHED.get().intValue(),
              Config.NUTRIENT_LOW_TARGET.get().intValue(),
              Config.NUTRIENT_TARGET_HIGH.get().intValue());
      graphics.renderTooltip(client.font, client.font.split(tooltip, width), mouseX, mouseY);
    }
  }

  private static boolean renderPenalties(
      GuiGraphics graphics, Font font, Player player, int left, int top) {
    int first = left + 9;
    int next = first;
    if (player.hasEffect(NutritionDeficits.attackEffect())
        && Config.MALNOURISHED_ATTACK_DAMAGE.get() < 0) {
      drawPenalty(graphics, font, next, top, "attack", "−" + attackReduction());
      next += 79;
    }
    if (player.hasEffect(NutritionDeficits.MOVEMENT)) {
      drawPenalty(
          graphics, font, next, top, "movement", "−" + NutritionDeficits.MOVEMENT_PERCENT + "%");
      next += 79;
    }
    if (player.hasEffect(NutritionDeficits.MINING)) {
      drawPenalty(
          graphics, font, next, top, "mining", "−" + NutritionDeficits.MINING_PERCENT + "%");
      return true;
    }
    return next != first;
  }

  private static void drawPenalty(
      GuiGraphics graphics, Font font, int left, int top, String key, String value) {
    var name = text("penalty." + key);
    int center = left + 37;
    graphics.fill(left, top + 21, left + 74, top + 45, 0xffebdad1);
    graphics.drawString(font, name, center - font.width(name) / 2, top + 23, 0xff532e28, false);
    graphics.drawString(font, value, center - font.width(value) / 2, top + 34, 0xff783524, false);
  }

  static String attackReduction() {
    return BigDecimal.valueOf(-Config.MALNOURISHED_ATTACK_DAMAGE.get())
        .stripTrailingZeros()
        .toPlainString();
  }

  private static Component text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.survival." + key, arguments);
  }
}
