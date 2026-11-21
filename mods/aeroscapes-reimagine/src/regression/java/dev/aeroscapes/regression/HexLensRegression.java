package dev.aeroscapes.regression;

import at.petrak.hexcasting.common.items.ItemLens;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexItems;
import com.google.common.collect.HashMultimap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

final class HexLensRegression {
  private HexLensRegression() {}

  static void verify(ServerPlayer player) {
    var lens = HexItems.SCRYING_LENS.getDefaultInstance();
    if (player.getEquipmentSlotForItem(lens) != EquipmentSlot.HEAD)
      throw new IllegalStateException("The scrying lens cannot be equipped in the helmet slot");
    var zoom = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(HexAttributes.GRID_ZOOM);
    var sight = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(HexAttributes.SCRY_SIGHT);
    var expected = HashMultimap.<Holder<Attribute>, AttributeModifier>create();
    expected.put(zoom, ItemLens.GRID_ZOOM);
    expected.put(sight, ItemLens.SCRY_SIGHT);
    if (!CuriosApi.getItemStackSlots(lens, player).containsKey("head"))
      throw new IllegalStateException(
          "The scrying lens cannot be equipped in the Curios head slot");
    var accessory =
        CuriosApi.getAttributeModifiers(
            new SlotContext("head", player, 0, false, true),
            ResourceLocation.parse("curios:head/0"),
            lens);
    if (!accessory.entries().containsAll(expected.entries()))
      throw new IllegalStateException("The Curios scrying lens lacks sight or grid zoom");
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      var applied = HashMultimap.<Holder<Attribute>, AttributeModifier>create();
      lens.forEachModifier(slot, applied::put);
      boolean hasSight = applied.containsEntry(sight, ItemLens.SCRY_SIGHT);
      boolean hasZoom = applied.containsEntry(zoom, ItemLens.GRID_ZOOM);
      if (hasSight != (slot == EquipmentSlot.HEAD) || hasZoom != (slot == EquipmentSlot.HEAD))
        throw new IllegalStateException("Unexpected lens attributes in equipment slot " + slot);
    }
  }
}
