package dev.aeroscapes.interfaceart;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ContainerArtwork {
  public static final int LABEL_COLOR = 0xebeef5;
  public static final ResourceLocation ANVIL = texture("anvil");

  private static final ResourceLocation REFORGE =
      ResourceLocation.fromNamespaceAndPath("tieredneo", "textures/gui/reforging_screen.png");

  private static final Map<Class<? extends Screen>, ResourceLocation> VANILLA_PANELS =
      Map.ofEntries(
          Map.entry(ContainerScreen.class, texture("generic_54")),
          Map.entry(AnvilScreen.class, ANVIL),
          Map.entry(BlastFurnaceScreen.class, texture("blast_furnace")),
          Map.entry(BrewingStandScreen.class, texture("brewing_stand")),
          Map.entry(CartographyTableScreen.class, texture("cartography_table")),
          Map.entry(CrafterScreen.class, texture("crafter")),
          Map.entry(CraftingScreen.class, texture("crafting_table")),
          Map.entry(DispenserScreen.class, texture("dispenser")),
          Map.entry(FurnaceScreen.class, texture("furnace")),
          Map.entry(GrindstoneScreen.class, texture("grindstone")),
          Map.entry(HopperScreen.class, texture("hopper")),
          Map.entry(ShulkerBoxScreen.class, texture("shulker_box")),
          Map.entry(SmithingScreen.class, texture("smithing")),
          Map.entry(SmokerScreen.class, texture("smoker")),
          Map.entry(EnchantmentScreen.class, texture("enchanting_table")),
          Map.entry(HorseInventoryScreen.class, texture("horse")),
          Map.entry(LoomScreen.class, texture("loom")),
          Map.entry(MerchantScreen.class, texture("villager")),
          Map.entry(StonecutterScreen.class, texture("stonecutter")));
  private static final Map<String, ResourceLocation> PROVIDER_PANELS =
      Map.of(
          "com.herobrot.tieredneo.reforge.ReforgeScreen", REFORGE,
          "com.tom.storagemod.screen.FilingCabinetScreen", texture("generic_54"),
          "com.tom.storagemod.screen.ItemFilterScreen", texture("dispenser"),
          "com.ninni.spawn.client.inventory.OctoductScreen", texture("dispenser"),
          "org.violetmoon.quark.content.management.client.screen.HeldShulkerBoxScreen",
              texture("shulker_box"),
          "fuzs.easymagic.client.gui.screens.inventory.ModEnchantmentScreen",
              texture("enchanting_table"));

  private static final List<ResourceLocation> CREATIVE_PANELS =
      List.of(
          texture("creative_inventory/tab_inventory"),
          texture("creative_inventory/tab_items"),
          texture("creative_inventory/tab_item_search"));

  private static Set<ResourceLocation> activePanels = Set.of();

  private ContainerArtwork() {}

  private static ResourceLocation texture(String name) {
    return ResourceLocation.withDefaultNamespace("textures/gui/container/" + name + ".png");
  }

  static void refresh(ResourceManager resources) {
    var atlasPack = resources.getResource(InterfaceArt.ATLAS).map(Resource::sourcePackId);
    if (atlasPack.isEmpty()) {
      activePanels = Set.of();
      return;
    }

    var candidates = new HashSet<>(VANILLA_PANELS.values());
    candidates.addAll(CREATIVE_PANELS);
    candidates.addAll(PROVIDER_PANELS.values());
    candidates.add(
        ResourceLocation.withDefaultNamespace(
            "textures/gui/sprites/container/enchanting_table/enchantment_slot.png"));
    candidates.add(
        ResourceLocation.withDefaultNamespace(
            "textures/gui/sprites/container/enchanting_table/enchantment_slot_disabled.png"));
    candidates.removeIf(
        texture -> !resources.getResource(texture).map(Resource::sourcePackId).equals(atlasPack));
    activePanels = Set.copyOf(candidates);
  }

  public static boolean usesPanel(Screen screen) {
    var texture = VANILLA_PANELS.get(screen.getClass());
    if (texture == null) texture = PROVIDER_PANELS.get(screen.getClass().getName());
    return texture != null && usesPanel(texture);
  }

  public static boolean usesPanel(ResourceLocation texture) {
    return activePanels.contains(texture);
  }
}
