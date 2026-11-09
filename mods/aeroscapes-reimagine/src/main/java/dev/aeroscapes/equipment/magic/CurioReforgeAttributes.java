package dev.aeroscapes.equipment.magic;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import io.redspace.ironsspellbooks.compat.Curios;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

final class CurioReforgeAttributes {
  private CurioReforgeAttributes() {}

  static void append(CurioAttributeModifierEvent event) {
    var slot = event.getSlotContext();
    if (slot.cosmetic()) return;
    String path = ReforgeProfiles.path(event.getItemStack());
    boolean ring = Curios.RING_SLOT.equals(slot.identifier()) && path.startsWith("ring_");
    boolean book = Curios.SPELLBOOK_SLOT.equals(slot.identifier()) && path.startsWith("spellbook_");
    if (!ring && !book) return;

    var tier = TieredNeoAPI.getTierAttribute(TieredNeoAPI.getTierId(event.getItemStack()));
    if (tier == null) return;
    for (var template : tier.getAttributes()) {
      var attributeId = ResourceLocation.tryParse(template.getAttributeTypeID());
      if (attributeId == null) continue;
      BuiltInRegistries.ATTRIBUTE
          .getHolder(attributeId)
          .ifPresent(
              attribute -> {
                var modifier = template.getEntityAttributeModifier();
                var id = modifier.id().withSuffix("/" + slot.identifier() + "_" + slot.index());
                event.addModifier(
                    attribute, new AttributeModifier(id, modifier.amount(), modifier.operation()));
              });
    }
  }
}
