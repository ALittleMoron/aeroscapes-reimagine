package dev.aeroscapes.engineering;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The foundry supplies steel ingots but no plate item for the common material tag. */
public final class EngineeringMaterials {
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems("aeroscapes_reimagine");
  public static final DeferredItem<Item> STEEL_SHEET = ITEMS.registerSimpleItem("steel_sheet");

  private EngineeringMaterials() {}

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
    bus.addListener(EngineeringMaterials::creativeContents);
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) event.accept(STEEL_SHEET);
  }
}
