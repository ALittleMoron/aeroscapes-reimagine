package dev.aeroscapes.regression;

import fuzs.easymagic.EasyMagic;
import fuzs.easymagic.config.ServerConfig;
import fuzs.easymagic.util.PlayerExperienceHelper;
import fuzs.easymagic.world.inventory.ModEnchantmentMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Native point charges, stale cached totals and repeated affordable rerolls. */
public final class EasyMagicExperienceRegression {
  private static final Logger LOGGER = LoggerFactory.getLogger(EasyMagicExperienceRegression.class);

  private EasyMagicExperienceRegression() {}

  public static void verify(ServerPlayer player, ModEnchantmentMenu menu, Container inventory) {
    var settings = EasyMagic.CONFIG.get(ServerConfig.class);
    require(
        settings.rerollExperiencePointsCost == 5
            && settings.rerollCatalystCost == 1
            && !settings.rerollingTakesEnchantmentLevels,
        "Actual native config must charge one lapis plus five points");
    player.setExperienceLevels(0);
    player.setExperiencePoints(0);
    player.totalExperience = 1000;
    inventory.setItem(1, new ItemStack(Items.LAPIS_LAZULI, 3));
    require(
        !menu.clickMenuButton(player, ModEnchantmentMenu.REROLL_DATA_SLOT),
        "Stale totalExperience cannot purchase a reroll");
    require(inventory.getItem(1).getCount() == 3, "Rejected reroll preserves lapis");

    player.giveExperiencePoints(7);
    diagnose("before-7-point-boundary", player, menu, inventory);
    require(visiblePoints(player) == 7, "Fixture starts with seven visible points");
    require(
        menu.clickMenuButton(player, ModEnchantmentMenu.REROLL_DATA_SLOT),
        "Seven visible points can fund one native reroll");
    diagnose("after-7-minus-5", player, menu, inventory);
    require(
        visiblePoints(player) == 2 && player.experienceLevel == 0,
        "Native reroll spends five visible points across the level boundary");
    require(inventory.getItem(1).getCount() == 2, "Native reroll spends one lapis");
    int boundaryProviderPoints = PlayerExperienceHelper.getTotalExperience(player);
    require(
        !menu.clickMenuButton(player, ModEnchantmentMenu.REROLL_DATA_SLOT)
            && inventory.getItem(1).getCount() == 2,
        "Two remaining points cannot fund another reroll");

    player.setExperienceLevels(0);
    player.setExperiencePoints(0);
    player.totalExperience = 0;
    player.giveExperiencePoints(15);
    inventory.setItem(1, new ItemStack(Items.LAPIS_LAZULI, 4));
    require(visiblePoints(player) == 15, "Repeated-reroll fixture starts with fifteen points");
    boolean providerCountsExact = true;
    for (int attempt = 0; attempt < 3; attempt++) {
      diagnose("before-repeat-" + attempt, player, menu, inventory);
      int before = visiblePoints(player);
      require(before >= 5, "Repeat fixture has at least five visible points");
      require(
          menu.canUseReroll(player),
          "Affordable native reroll rejected by provider XP conversion: " + state(player));
      require(
          menu.clickMenuButton(player, ModEnchantmentMenu.REROLL_DATA_SLOT),
          "Every affordable repeated reroll must succeed");
      diagnose("after-repeat-" + attempt, player, menu, inventory);
      require(
          visiblePoints(player) == before - 5, "Each repeated reroll spends five visible points");
      providerCountsExact &=
          PlayerExperienceHelper.getTotalExperience(player) == visiblePoints(player);
      require(
          inventory.getItem(1).getCount() == 3 - attempt,
          "Each repeated reroll spends exactly one lapis");
    }
    require(
        visiblePoints(player) == 0 && !menu.canUseReroll(player),
        "Three rerolls exhaust fifteen points without permitting a free fourth roll");
    require(
        boundaryProviderPoints == 2,
        "Provider must report two points after the seven-point boundary case");
    require(providerCountsExact, "Provider XP conversion must equal every integral visible budget");
    player.setExperienceLevels(0);
    player.experienceProgress = 4.75F / player.getXpNeededForNextLevel();
    require(
        PlayerExperienceHelper.getTotalExperience(player) == 4
            && !menu.clickMenuButton(player, ModEnchantmentMenu.REROLL_DATA_SLOT)
            && inventory.getItem(1).getCount() == 1,
        "A genuinely fractional budget below five points cannot buy a reroll");
    player.setExperiencePoints(0);
  }

  private static int visiblePoints(ServerPlayer player) {
    float partial = player.getXpNeededForNextLevel() * player.experienceProgress;
    require(
        Math.abs(partial - Math.round(partial)) < 0.0001F,
        "Fixture XP bar remains within float tolerance of an integral point budget");
    return PlayerExperienceHelper.getExperienceFromLevels(player.experienceLevel)
        + Math.round(partial);
  }

  private static String state(ServerPlayer player) {
    return "level="
        + player.experienceLevel
        + ", progress="
        + player.experienceProgress
        + ", needed="
        + player.getXpNeededForNextLevel()
        + ", partialFloat="
        + (player.getXpNeededForNextLevel() * player.experienceProgress)
        + ", provider="
        + PlayerExperienceHelper.getTotalExperience(player)
        + ", visible="
        + visiblePoints(player)
        + ", cachedTotal="
        + player.totalExperience;
  }

  private static void diagnose(
      String phase, ServerPlayer player, ModEnchantmentMenu menu, Container inventory) {
    LOGGER.info(
        "Easy Magic XP {}: {}, lapis={}, affordable={}",
        phase,
        state(player),
        inventory.getItem(1).getCount(),
        menu.canUseReroll(player));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
