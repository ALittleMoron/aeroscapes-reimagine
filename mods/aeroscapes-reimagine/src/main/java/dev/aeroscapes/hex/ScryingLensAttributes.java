package dev.aeroscapes.hex;

import at.petrak.hexcasting.common.items.ItemLens;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ScryingLensAttributes {
  private static final ResourceLocation LENS = ResourceLocation.parse("hexcasting:lens");

  private ScryingLensAttributes() {}

  @SubscribeEvent
  public static void modifiers(ItemAttributeModifierEvent event) {
    var stack = event.getItemStack();
    if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(LENS)) return;
    // The port supplies these modifiers to Curios only. Reuse its IDs in the helmet slot.
    ((ItemLens) stack.getItem())
        .getHexBaubleAttrs(stack)
        .forEach(
            (attribute, modifier) ->
                event.addModifier(
                    BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                    modifier,
                    EquipmentSlotGroup.HEAD));
  }
}
