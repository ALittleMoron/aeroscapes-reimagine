package dev.aeroscapes.equipment.magic;

import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;

public final class SpellAccessoryAttributes {
  private SpellAccessoryAttributes() {}

  public static List<Component> baseLines(String slotName, ResourceLocation id, ItemStack stack) {
    if (!(stack.getItem() instanceof CurioBaseItem item)) return List.of();
    // Iron's base bonuses depend only on the slot name and index. EMI can reload before login.
    var slot = new SlotContext(slotName, null, 0, false, true);
    return item.getAttributeModifiers(slot, id, stack).entries().stream()
        .<Component>map(
            entry -> entry.getKey().value().toComponent(entry.getValue(), TooltipFlag.NORMAL))
        .toList();
  }
}
