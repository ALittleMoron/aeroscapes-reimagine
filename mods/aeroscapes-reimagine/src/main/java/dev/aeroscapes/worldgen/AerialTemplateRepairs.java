package dev.aeroscapes.worldgen;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Removes author-save residue while retaining the selected original vessel and village assets. */
public final class AerialTemplateRepairs {
  private static final Set<String> VESSELS =
      Set.of("airballoon", "airship", "pillager_airballoon", "pillager_airship");
  private static final Set<String> VILLAGE =
      Set.of(
          "alchemist_1",
          "bridge_1",
          "bridge_2",
          "bridge_3",
          "bridge_4",
          "bridge_5",
          "cartographer_1",
          "farm_1",
          "farm_2",
          "farm_3",
          "fletcher_1",
          "fountain",
          "house_1",
          "librarian_1",
          "librarian_2",
          "loom_1",
          "mason_1",
          "villager",
          "weaponsmith_1",
          "weaponsmith_2");

  private AerialTemplateRepairs() {}

  public static boolean applies(ResourceLocation id) {
    return id.getNamespace().equals("cr_aero_structures") && VESSELS.contains(id.getPath())
        || id.getNamespace().equals("skyvillages") && VILLAGE.contains(id.getPath());
  }

  public static void apply(ResourceLocation id, CompoundTag data) {
    if (!applies(id)) return;
    for (var value : data.getList("blocks", Tag.TAG_COMPOUND)) {
      CompoundTag block = ((CompoundTag) value).getCompound("nbt");
      if (block.contains("LootTable", Tag.TAG_STRING)) block.remove("LootTableSeed");
    }
    if (!id.getNamespace().equals("cr_aero_structures")) return;
    var entities = data.getList("entities", Tag.TAG_COMPOUND);
    entities.removeIf(
        value -> {
          String entity = ((CompoundTag) value).getCompound("nbt").getString("id");
          return entity.equals("simulated:contraption_diagram")
              || entity.equals("naturalist:butterfly")
              || entity.equals("minecraft:item");
        });
    for (var value : entities) {
      CompoundTag entity = ((CompoundTag) value).getCompound("nbt");
      String type = entity.getString("id");
      if (type.equals("minecraft:wandering_trader")) {
        var offers = entity.getCompound("Offers").getList("Recipes", Tag.TAG_COMPOUND);
        offers.removeIf(
            offer -> {
              CompoundTag sold = ((CompoundTag) offer).getCompound("sell");
              return sold.getString("id").equals("expandeddelight:peanut")
                  || sold.getString("id").equals("minecraft:filled_map");
            });
      }
      if (type.equals("minecraft:wandering_trader") || type.equals("minecraft:trader_llama")) {
        entity.putBoolean("PersistenceRequired", true);
        entity.putInt("DespawnDelay", 0);
      }
      // Trader llamas ignore PersistenceRequired and discard at delay zero unless tamed.
      if (type.equals("minecraft:trader_llama")) entity.putBoolean("Tame", true);
    }
  }
}
