package dev.aeroscapes.equipment.armor;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class ArmorAttributes {
  private ArmorAttributes() {}

  public static void penalizedModifiers(
      ItemStack stack,
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> accept) {
    stack
        .getAttributeModifiers()
        .forEach(
            slot,
            (attribute, modifier) -> {
              var adjusted = undertrainedModifier(attribute, modifier);
              if (adjusted != null) accept.accept(attribute, adjusted);
            });
    EnchantmentHelper.forEachModifier(stack, slot, accept);
  }

  public static void refresh(
      Player player, ItemStack stack, EquipmentSlot slot, boolean undertrained) {
    Set<ModifierKey> changed = new HashSet<>();
    stack
        .getAttributeModifiers()
        .forEach(
            slot,
            (attribute, modifier) -> {
              var adjusted = undertrainedModifier(attribute, modifier);
              if (adjusted == modifier) return;
              replace(player, attribute, modifier.id(), undertrained ? adjusted : modifier);
              changed.add(new ModifierKey(attribute, modifier.id()));
            });
    if (changed.isEmpty()) return;
    // Preserve vanilla precedence if an enchantment uses the same modifier ID.
    EnchantmentHelper.forEachModifier(
        stack,
        slot,
        (attribute, modifier) -> {
          if (changed.contains(new ModifierKey(attribute, modifier.id()))) {
            replace(player, attribute, modifier.id(), modifier);
          }
        });
  }

  @Nullable
  static AttributeModifier undertrainedModifier(
      Holder<Attribute> attribute, AttributeModifier modifier) {
    if (modifier.amount() == 0.0
        || attribute.value().getStyle(modifier.amount() > 0) != ChatFormatting.BLUE)
      return modifier;

    if ((attribute.equals(Attributes.ARMOR) || attribute.equals(Attributes.ARMOR_TOUGHNESS))
        && !reforgeDefense(modifier.id())) {
      return new AttributeModifier(modifier.id(), 0.10 * modifier.amount(), modifier.operation());
    }
    return null;
  }

  private static boolean reforgeDefense(ResourceLocation id) {
    if (!id.getNamespace().equals("aeroscapes_reimagine")) return false;
    String path = id.getPath();
    return path.startsWith("armor_")
        || path.startsWith("diving_")
        || path.startsWith("magic_armor_");
  }

  private static void replace(
      Player player,
      Holder<Attribute> attribute,
      ResourceLocation id,
      @Nullable AttributeModifier modifier) {
    var instance = player.getAttribute(attribute);
    if (instance == null) return;
    instance.removeModifier(id);
    if (modifier != null) instance.addTransientModifier(modifier);
  }

  private record ModifierKey(Holder<Attribute> attribute, ResourceLocation id) {}
}
