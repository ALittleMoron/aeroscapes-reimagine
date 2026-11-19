package dev.aeroscapes.regression;

import dev.aeroscapes.equipment.AccessoryGuides;
import dev.aeroscapes.equipment.magic.SpellAccessoryAttributes;
import java.util.HashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

final class AccessoryGuidesRegression {
  private AccessoryGuidesRegression() {}

  static void verify(ServerPlayer player) {
    var irons = new HashSet<ResourceLocation>();
    for (var slot : CuriosApi.getPlayerSlots(player.level()).keySet()) {
      var tag =
          TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", slot));
      for (var item : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
        var id = BuiltInRegistries.ITEM.getKey(item.value());
        if (id.getNamespace().equals("irons_spellbooks")) irons.add(id);
      }
    }
    for (var entry : AccessoryGuides.ITEMS.entrySet()) {
      var id = entry.getKey();
      var stack = BuiltInRegistries.ITEM.getOptional(id).orElseThrow().getDefaultInstance();
      if (!CuriosApi.getItemStackSlots(stack, player).containsKey(entry.getValue().slot()))
        throw new IllegalStateException(
            "Wrong guide slot for " + id + ": " + entry.getValue().slot());
      if (irons.remove(id)) {
        var name = entry.getValue().slot();
        var expected =
            CuriosApi.getAttributeModifiers(
                    new SlotContext(name, player, 0, false, true), id, stack)
                .entries()
                .stream()
                .map(
                    modifier ->
                        modifier
                            .getKey()
                            .value()
                            .toComponent(modifier.getValue(), TooltipFlag.NORMAL))
                .toList();
        var initial = SpellAccessoryAttributes.baseLines(name, id, stack);
        if (initial.size() != expected.size()
            || !initial.containsAll(expected)
            || (id.getPath().equals("mana_ring") && initial.isEmpty()))
          throw new IllegalStateException("Accessory attributes before login differ for " + id);
      }
    }
    if (!irons.isEmpty())
      throw new IllegalStateException("Missing Iron's accessory guides: " + irons);
  }
}
