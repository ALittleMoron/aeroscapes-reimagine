package dev.aeroscapes.equipment.armor;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.races.ClassProgression;
import harmonised.pmmo.api.APIUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ArmorProficiency {
  static final String TOOLTIP = "tooltip.aeroscapes_reimagine.armor.";
  public static final List<EquipmentSlot> SLOTS =
      List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
  private static final TagKey<Item> STARTER = armorTag("starter");
  private static final TagKey<Item> LIGHT = armorTag("light");
  private static final TagKey<Item> HEAVY = armorTag("heavy");
  private static final TagKey<Item> UTILITY = armorTag("utility");

  private ArmorProficiency() {}

  private static TagKey<Item> armorTag(String path) {
    return TagKey.create(Registries.ITEM, id("armor_handling/" + path));
  }

  static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path);
  }

  private enum Family {
    NONE(null, null, 0, 0),
    STARTER("light_armor", null, 1, 0),
    LIGHT("light_armor", "light_armor_handling", 1, 0),
    HEAVY("heavy_armor", "heavy_armor_handling", -1, -4),
    RANGER("light_armor", "ranger_armor_training", 1, 0),
    ROGUE("light_armor", "rogue_armor_training", 1, 0);

    @Nullable private final String practice;
    @Nullable private final String talent;
    private final int trainedSpeed;
    private final int untrainedSpeed;

    Family(
        @Nullable String practice, @Nullable String talent, int trainedSpeed, int untrainedSpeed) {
      this.practice = practice;
      this.talent = talent;
      this.trainedSpeed = trainedSpeed;
      this.untrainedSpeed = untrainedSpeed;
    }
  }

  private static Family family(ItemStack stack) {
    if (stack.is(UTILITY)) return Family.NONE;
    if (stack.getItem() instanceof SpecialistArmor.Piece piece) {
      return piece.set() == SpecialistArmor.Set.WINDWARD ? Family.RANGER : Family.ROGUE;
    }
    if (stack.is(STARTER)) return Family.STARTER;
    if (stack.is(HEAVY)) return Family.HEAVY;
    if (stack.is(LIGHT)) return Family.LIGHT;
    if (stack.getItem() instanceof ArmorItem armor) {
      return armor.getMaterial().equals(ArmorMaterials.LEATHER) ? Family.STARTER : Family.HEAVY;
    }
    return Family.NONE;
  }

  public static boolean undertrained(@Nullable Player player, ItemStack stack) {
    return undertrained(player, family(stack));
  }

  private static boolean undertrained(@Nullable Player player, Family family) {
    return player != null
        && family.talent != null
        && ModList.get().isLoaded("puffish_skills")
        && !ClassProgression.hasCombatTalent(player, family.talent);
  }

  public static ItemStack activeItem(LivingEntity wearer, EquipmentSlot slot) {
    ItemStack stack = wearer.getItemBySlot(slot);
    return wearer instanceof Player player && undertrained(player, stack) ? ItemStack.EMPTY : stack;
  }

  public static String reforgeProfile(Player player, EquipmentSlot slot) {
    ItemStack stack = player.getItemBySlot(slot);
    String profile = ReforgeProfiles.path(stack);
    return profile.contains("_poor_") || !undertrained(player, stack) ? profile : "";
  }

  static int movementPercent(Player player) {
    int percent = 0;
    for (var slot : SLOTS) {
      var family = family(player.getItemBySlot(slot));
      percent += undertrained(player, family) ? family.untrainedSpeed : family.trainedSpeed;
    }
    return percent;
  }

  @SubscribeEvent
  public static void damageTaken(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !PracticeActions.personal(player)
        || !ModList.get().isLoaded("pmmo")
        || event.getNewDamage() <= 0.0F
        || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)
        || attacker == player) return;

    long light = 0;
    long heavy = 0;
    for (var slot : SLOTS) {
      var family = family(player.getItemBySlot(slot));
      if (family.practice == null) continue;
      if (family == Family.HEAVY) heavy++;
      else light++;
    }
    if (light > 0) APIUtils.addXp(Family.LIGHT.practice, player, light);
    if (heavy > 0) APIUtils.addXp(Family.HEAVY.practice, player, heavy);
  }

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    var family = family(event.getItemStack());
    if (family == Family.NONE) return;
    boolean undertrained = undertrained(event.getEntity(), family);
    String type = family == Family.HEAVY ? "heavy" : "light";
    var tooltip = event.getToolTip();
    tooltip.add(Component.translatable(TOOLTIP + type).withStyle(ChatFormatting.GRAY));
    if (family.talent != null) {
      var talent =
          Component.translatable(
              "puffish_skills.aeroscapes_reimagine.combat." + family.talent + ".title");
      tooltip.add(
          Component.translatable(TOOLTIP + "training", talent)
              .withStyle(undertrained ? ChatFormatting.RED : ChatFormatting.GRAY));
    }
    if (undertrained) {
      tooltip.add(Component.translatable(TOOLTIP + "undertrained").withStyle(ChatFormatting.RED));
    }
  }
}
