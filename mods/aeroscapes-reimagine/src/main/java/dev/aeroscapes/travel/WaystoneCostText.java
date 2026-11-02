package dev.aeroscapes.travel;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Exact point balances, rather than a rounded number of levels to spend. */
public final class WaystoneCostText {
  private WaystoneCostText() {}

  public static void append(Player player, int points, List<Component> tooltip) {
    tooltip.add(Component.translatable("gui.aeroscapes_reimagine.travel.cost_points", points));
    int minimumLevel = WaystoneFees.levelAtPoints(points, points);
    tooltip.add(
        Component.translatable(
            "gui.aeroscapes_reimagine.travel.minimum_balance",
            minimumLevel,
            points - WaystoneFees.cumulativePoints(minimumLevel)));
    if (player.getAbilities().instabuild) {
      tooltip.add(Component.translatable("gui.aeroscapes_reimagine.travel.creative_free"));
      return;
    }
    long available = TravelIntegration.experiencePoints(player);
    if (available < points) {
      tooltip.add(
          Component.translatable(
              "gui.aeroscapes_reimagine.travel.missing_points", points - available));
    } else {
      long remaining = available - points;
      int level = WaystoneFees.levelAtPoints(remaining, player.experienceLevel);
      tooltip.add(
          Component.translatable(
              "gui.aeroscapes_reimagine.travel.after_balance",
              level,
              remaining - WaystoneFees.cumulativePoints(level)));
    }
  }
}
