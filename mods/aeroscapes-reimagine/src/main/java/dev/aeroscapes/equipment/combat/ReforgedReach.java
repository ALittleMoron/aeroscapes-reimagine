package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class ReforgedReach {
  private static final List<ResourceLocation> ROLL_IDS =
      List.of(
          ResourceLocation.parse("aeroscapes_reimagine:melee_epic_reach_mainhand"),
          ResourceLocation.parse("aeroscapes_reimagine:melee_epic_reach_with_tempo_mainhand"));

  private ReforgedReach() {}

  public static double bonus(ItemStack stack) {
    if (!ReforgeProfiles.basePath(stack).equals("melee_epic_reach")) return 0;
    double bonus = 0;
    for (var entry : stack.getAttributeModifiers().modifiers()) {
      if (isReachRoll(entry)) bonus += entry.modifier().amount();
    }
    return bonus;
  }

  public static double equippedBonus(Player player) {
    var attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
    if (attribute == null) return 0;
    double bonus = 0;
    for (var id : ROLL_IDS) {
      var modifier = attribute.getModifier(id);
      if (modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
        bonus += modifier.amount();
    }
    return bonus;
  }

  public static ItemStack withoutBonus(ItemStack stack) {
    var modifiers =
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    var remaining = modifiers.modifiers().stream().filter(entry -> !isReachRoll(entry)).toList();
    if (remaining.size() == modifiers.modifiers().size()) return stack;
    var copy = stack.copy();
    copy.set(
        DataComponents.ATTRIBUTE_MODIFIERS,
        new ItemAttributeModifiers(remaining, modifiers.showInTooltip()));
    return copy;
  }

  private static boolean isReachRoll(ItemAttributeModifiers.Entry entry) {
    var modifier = entry.modifier();
    return entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)
        && modifier.operation() == AttributeModifier.Operation.ADD_VALUE
        && ROLL_IDS.contains(modifier.id());
  }
}
