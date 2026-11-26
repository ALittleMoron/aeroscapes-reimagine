package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.tools.ToolReforges;
import dev.aeroscapes.races.ClassProgression;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class WeaponProficiency {
  private static final String SHIELD = "shield_handling";
  private static final int MELEE_DELAY_PERCENT = 30;
  private static final int SHIELD_RAISE_TICKS = 3;

  private WeaponProficiency() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(WeaponProficiency::tooltip);
  }

  public static float recoveryTicks(Player player, float normal) {
    if (!trainingApplies(player)) return normal;
    ItemStack mainHand = player.getMainHandItem();
    ItemStack offHand = player.getOffhandItem();
    String training = meleeTraining(mainHand);
    if (!offHand.isEmpty()
        && !Objects.equals(training, meleeTraining(offHand))
        && ModList.get().isLoaded("bettercombat")) {
      training = meleeTraining(BetterCombat.selectedWeapon(player, mainHand));
    }
    return training != null && !ClassProgression.hasCombatTalent(player, training)
        ? normal * (1.0F + MELEE_DELAY_PERCENT / 100.0F)
        : normal;
  }

  static boolean supportsOffhandAttack(Player player) {
    return ModList.get().isLoaded("bettercombat") && BetterCombat.isDualWielding(player);
  }

  public static int shieldRaiseTicks(LivingEntity user, int normal) {
    return user instanceof Player player
            && isShield(player.getUseItem())
            && trainingApplies(player)
            && !ClassProgression.hasCombatTalent(player, SHIELD)
        ? normal + SHIELD_RAISE_TICKS
        : normal;
  }

  @Nullable
  private static String meleeTraining(ItemStack stack) {
    if (stack.is(ItemTags.AXES) && !stack.is(Items.WOODEN_AXE) && !stack.is(Items.STONE_AXE)) {
      return "combat_axe_handling";
    }
    return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
      case "minecraft:golden_sword", "minecraft:iron_sword", "overgeared:copper_sword" ->
          "sword_handling";
      case "minecraft:diamond_sword",
          "minecraft:netherite_sword",
          "overgeared:steel_sword",
          "hybrid_aquatic:coral_blade",
          "irons_spellbooks:magehunter",
          "tide:blazing_swordfish" ->
          "advanced_sword_handling";
      case "minecraft:mace",
          "createdieselgenerators:hammer",
          "farmersdelight:skillet",
          "irons_spellbooks:artificer_cane",
          "irons_spellbooks:blood_staff",
          "irons_spellbooks:boreal_blade",
          "irons_spellbooks:claymore",
          "irons_spellbooks:graybeard_staff",
          "irons_spellbooks:ice_staff",
          "irons_spellbooks:keeper_flamberge",
          "irons_spellbooks:legionnaire_flamberge",
          "irons_spellbooks:lightning_rod",
          "irons_spellbooks:pyrium_staff",
          "irons_spellbooks:spellbreaker",
          "irons_spellbooks:staff_of_the_nines" ->
          "heavy_weapon_handling";
      case "minecraft:trident",
          "hybrid_aquatic:seashell_spear",
          "irons_spellbooks:decrepit_scythe",
          "irons_spellbooks:hellrazor",
          "irons_spellbooks:twilight_gale",
          "tide:sailfish" ->
          "polearm_handling";
      case "farmersdelight:iron_knife",
          "farmersdelight:golden_knife",
          "farmersdelight:diamond_knife",
          "farmersdelight:netherite_knife",
          "immersive_weathering:ice_sickle",
          "irons_spellbooks:amethyst_rapier",
          "irons_spellbooks:misery",
          "wetland_whimsy:dagger" ->
          "light_weapon_handling";
      default -> null;
    };
  }

  private static boolean isShield(ItemStack stack) {
    return stack.canPerformAction(ItemAbilities.SHIELD_BLOCK);
  }

  private static boolean trainingApplies(Player player) {
    return ToolReforges.isSurvivalPlayer(player)
        && ModList.get().isLoaded("pmmo")
        && ModList.get().isLoaded("puffish_skills");
  }

  private static void tooltip(ItemTooltipEvent event) {
    ItemStack stack = event.getItemStack();
    boolean shield = isShield(stack);
    String node = shield ? SHIELD : meleeTraining(stack);
    if (node == null) return;
    Player player = event.getEntity();
    boolean undertrained =
        player != null
            && trainingApplies(player)
            && !ClassProgression.hasCombatTalent(player, node);
    String prefix = "tooltip.aeroscapes_reimagine.weapon_proficiency.";
    var talent =
        Component.translatable("puffish_skills.aeroscapes_reimagine.combat." + node + ".title");
    event
        .getToolTip()
        .add(
            Component.translatable(prefix + "talent", talent)
                .withStyle(undertrained ? ChatFormatting.RED : ChatFormatting.GRAY));
    if (undertrained) {
      event
          .getToolTip()
          .add(
              Component.translatable(
                      prefix + (shield ? "shield_delay" : "melee_delay"),
                      shield ? SHIELD_RAISE_TICKS : MELEE_DELAY_PERCENT)
                  .withStyle(ChatFormatting.GRAY));
    }
  }

  private static final class BetterCombat {
    private static final Method CURRENT_ATTACK =
        method("net.bettercombat.api.EntityPlayer_BetterCombat", "getCurrentAttack");
    private static final Method ATTACK_STACK =
        method("net.bettercombat.api.AttackHand", "itemStack");
    private static final Method DUAL_WIELDING =
        method("net.bettercombat.logic.PlayerAttackHelper", "isDualWielding", Player.class);

    private BetterCombat() {}

    private static ItemStack selectedWeapon(Player player, ItemStack mainHand) {
      try {
        // Both sides set the combo index before asking Minecraft for the swing's delay.
        Object attack = CURRENT_ATTACK.invoke(player);
        return attack == null ? mainHand : (ItemStack) ATTACK_STACK.invoke(attack);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(
            "Could not read the selected Better Combat weapon", failure);
      }
    }

    private static boolean isDualWielding(Player player) {
      try {
        return (boolean) DUAL_WIELDING.invoke(null, player);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Could not check Better Combat dual wielding", failure);
      }
    }

    private static Method method(String type, String name, Class<?>... parameters) {
      try {
        return Class.forName(type).getMethod(name, parameters);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(
            "Could not attach to the Better Combat attack API", failure);
      }
    }
  }
}
