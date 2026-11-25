package dev.aeroscapes.equipment.tools;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ProfessionProgression;
import harmonised.pmmo.api.APIUtils;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class ToolProficiency {
  public static final long PRACTICE_XP = 4;
  private static final float UNDERTRAINED_SPEED = 0.5F;
  private static final int UNDERTRAINED_USE_DELAY = 8;

  private ToolProficiency() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ToolProficiency::practiceBreaking);
    NeoForge.EVENT_BUS.addListener(ToolProficiency::tooltip);
  }

  private enum Family {
    PICKAXE(ProfessionDiscipline.PICKAXE_HANDLING, ItemTags.PICKAXES),
    AXE(ProfessionDiscipline.AXE_HANDLING, ItemTags.AXES),
    SHOVEL(ProfessionDiscipline.SHOVEL_HANDLING, ItemTags.SHOVELS),
    HOE(ProfessionDiscipline.HOE_HANDLING, ItemTags.HOES),
    SHEARS(ProfessionDiscipline.SHEARS_HANDLING, null);

    private final ProfessionDiscipline discipline;
    @Nullable private final TagKey<Item> tag;

    Family(ProfessionDiscipline discipline, @Nullable TagKey<Item> tag) {
      this.discipline = discipline;
      this.tag = tag;
    }
  }

  @Nullable
  private static Family family(Item item) {
    if (item instanceof ShearsItem) return Family.SHEARS;
    var holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
    for (var family : Family.values()) {
      if (family.tag != null && holder.is(family.tag)) return family;
    }
    return null;
  }

  @Nullable
  private static String requiredTalent(Family family, Item item) {
    if (family == Family.SHEARS) return "cutting";
    if (item instanceof TieredItem tool) {
      var tier = tool.getTier();
      if (tier == Tiers.IRON || tier == Tiers.GOLD) return "iron";
      if (tier == Tiers.DIAMOND) return "diamond";
      if (tier == Tiers.NETHERITE) return "netherite";
    }
    var id = BuiltInRegistries.ITEM.getKey(item);
    if (id.getNamespace().equals("overgeared")) {
      if (id.getPath().startsWith("copper_")) return "copper";
      if (id.getPath().startsWith("steel_")) return "steel";
    }
    if (id.getNamespace().equals("hybrid_aquatic")) {
      if (id.getPath().startsWith("seashell_")) return "copper";
      if (id.getPath().startsWith("coral_")) return "iron";
    }
    return null;
  }

  private static boolean undertrained(Player player, Family family, String talent) {
    return ToolReforges.isSurvivalPlayer(player)
        && !ProfessionProgression.hasTalent(player, family.discipline, talent);
  }

  public static boolean isTracked(Item item) {
    return family(item) != null;
  }

  public static float adjustBaseSpeed(Player player, float speed) {
    if (speed <= 1) return speed;
    var item = player.getMainHandItem().getItem();
    var family = family(item);
    if (family == null) return speed;
    String talent = requiredTalent(family, item);
    if (talent == null || !undertrained(player, family, talent)) return speed;
    // Vanilla tests this same value to decide whether to add the Efficiency bonus.
    return Math.max(Math.nextUp(1.0F), speed * UNDERTRAINED_SPEED);
  }

  private static void practiceBreaking(BlockDropsEvent event) {
    if (!(event.getBreaker() instanceof ServerPlayer player)
        || !ToolReforges.isSurvivalPlayer(player)
        || event.getTool().getDestroySpeed(event.getState()) <= 1) return;
    var family = family(event.getTool().getItem());
    if (family != null) APIUtils.addXp(family.discipline.skill(), player, PRACTICE_XP);
  }

  public static void completeToolUse(ServerPlayer player, Item item) {
    if (!ToolReforges.isSurvivalPlayer(player)) return;
    var family = family(item);
    if (family == null) return;
    APIUtils.addXp(family.discipline.skill(), player, PRACTICE_XP);
    String talent = requiredTalent(family, item);
    if (talent != null && undertrained(player, family, talent))
      player.getCooldowns().addCooldown(item, UNDERTRAINED_USE_DELAY);
  }

  private static void tooltip(ItemTooltipEvent event) {
    var item = event.getItemStack().getItem();
    var family = family(item);
    if (family == null) return;
    String talent = requiredTalent(family, item);
    if (talent == null) return;
    var title =
        Component.translatable(
            "puffish_skills.aeroscapes_reimagine."
                + family.discipline.skill()
                + "."
                + talent
                + ".title");
    var player = event.getEntity();
    boolean undertrained = player != null && undertrained(player, family, talent);
    event
        .getToolTip()
        .add(
            Component.translatable("tooltip.aeroscapes_reimagine.tool_handling", title)
                .withStyle(undertrained ? ChatFormatting.RED : ChatFormatting.GRAY));
    if (undertrained) {
      event
          .getToolTip()
          .add(
              Component.translatable("tooltip.aeroscapes_reimagine.tool_undertrained")
                  .withStyle(ChatFormatting.GRAY));
      event
          .getToolTip()
          .add(
              Component.translatable(
                      "tooltip.aeroscapes_reimagine.tool_training_menu",
                      Component.translatable(
                          "puffish_skills.aeroscapes_reimagine."
                              + family.discipline.section().skill()
                              + ".title"),
                      Component.translatable(
                          "aeroscapes_reimagine.progression.page."
                              + family.discipline.skill()
                              + "_short"))
                  .withStyle(ChatFormatting.GRAY));
    }
  }
}
