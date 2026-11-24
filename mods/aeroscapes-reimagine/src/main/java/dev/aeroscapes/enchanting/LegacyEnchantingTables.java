package dev.aeroscapes.enchanting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.LockCode;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class LegacyEnchantingTables {
  private static final ResourceLocation LEGACY_TABLE =
      ResourceLocation.fromNamespaceAndPath("easymagic", "enchanting_table");
  private static final ResourceLocation TABLE =
      ResourceLocation.withDefaultNamespace("enchanting_table");

  private LegacyEnchantingTables() {}

  public static void register(IEventBus bus) {
    bus.addListener(LegacyEnchantingTables::registerAliases);
  }

  private static void registerAliases(RegisterEvent event) {
    var key = event.getRegistryKey();
    if (key.equals(Registries.BLOCK) || key.equals(Registries.BLOCK_ENTITY_TYPE)) {
      // Immersive Enchanting reads the same three Items slots on the vanilla table.
      event.getRegistry().addAlias(LEGACY_TABLE, TABLE);
    }
  }

  public interface LockedTable {
    LockCode aeroscapes$lockCode();
  }
}
