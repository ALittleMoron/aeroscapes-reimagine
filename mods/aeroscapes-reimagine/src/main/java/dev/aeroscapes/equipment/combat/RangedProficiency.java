package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.tools.ToolReforges;
import dev.aeroscapes.races.ClassProgression;
import harmonised.pmmo.api.APIUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class RangedProficiency {
  private static final int UNDERTRAINED_DRAW_PERCENT = 45;
  private static final Training CROSSBOW = new Training("crossbow_handling", 2);
  private static final Training STARLIGHT_BOW = new Training("starlight_bow_handling", 6);
  private static final Training AUTOLOADER = new Training("autoloader_handling", 10);
  private static final List<Training> TALENTS = List.of(CROSSBOW, STARLIGHT_BOW, AUTOLOADER);

  private RangedProficiency() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(RangedProficiency::tooltip);
  }

  public static float drawTime(ItemStack stack, LivingEntity user) {
    var training = training(stack);
    return training != null && undertrained(user, training)
        ? 1.0F + UNDERTRAINED_DRAW_PERCENT / 100.0F
        : 1.0F;
  }

  @Nullable
  private static Training training(ItemStack stack) {
    return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
      case "minecraft:crossbow" -> CROSSBOW;
      case "tide:starlight_bow" -> STARLIGHT_BOW;
      case "irons_spellbooks:autoloader_crossbow" -> AUTOLOADER;
      default -> null;
    };
  }

  public static int missingPracticeLevel(@Nullable Player player, String node) {
    for (var training : TALENTS) {
      if (training.node().equals(node)
          && (player == null
              || !ModList.get().isLoaded("pmmo")
              || APIUtils.getLevel("archery", player) < training.level())) return training.level();
    }
    return 0;
  }

  public static Component practiceRequired(int level) {
    return Component.translatable("aeroscapes_reimagine.progression.ranged_required", level);
  }

  private static boolean undertrained(LivingEntity user, Training training) {
    return user instanceof Player player
        && ToolReforges.isSurvivalPlayer(player)
        && ModList.get().isLoaded("pmmo")
        && ModList.get().isLoaded("puffish_skills")
        && !ClassProgression.hasCombatTalent(player, training.node());
  }

  private static void tooltip(ItemTooltipEvent event) {
    var training = training(event.getItemStack());
    if (training == null) return;
    boolean undertrained = undertrained(event.getEntity(), training);
    var talent = ClassProgression.talentTitle(training.node());
    event
        .getToolTip()
        .add(
            Component.translatable("tooltip.aeroscapes_reimagine.ranged_training", talent)
                .withStyle(undertrained ? ChatFormatting.RED : ChatFormatting.GRAY));
    if (undertrained) {
      event
          .getToolTip()
          .add(
              Component.translatable(
                      "tooltip.aeroscapes_reimagine.ranged_undertrained",
                      UNDERTRAINED_DRAW_PERCENT,
                      training.level())
                  .withStyle(ChatFormatting.GRAY));
    }
  }

  private record Training(String node, int level) {}
}
