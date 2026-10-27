package dev.aeroscapes.worldgen;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Field-level repairs applied to newly decoded original resources, before native structure DFU. */
public final class RuntimeTemplateRepairs {
  private static final Set<String> CTOV =
      Set.of(
          "pillager_outpost/badlands/allays_cage",
          "pillager_outpost/beach/allays_cage",
          "pillager_outpost/dark_forest/allay_cage",
          "pillager_outpost/desert/allays_cage",
          "pillager_outpost/jungle/allay_cage",
          "pillager_outpost/mountain/allay_cage",
          "pillager_outpost/plains/allay_cage",
          "pillager_outpost/savanna/allay_cage",
          "pillager_outpost/snowy/allay_cage",
          "pillager_outpost/swamp/allay_cage",
          "pillager_outpost/taiga/allay_cage",
          "village/halloween/jobsite/fd_farm",
          "village/jungle/jobsite/fd_farm",
          "village/jungle_tree/jobsite/fd_farm",
          "village/mesa/jobsite/fd_farm",
          "village/mesa_fortified/jobsite/fd_farm",
          "village/mountain/jobsite/fd_farm",
          "village/mountain_alpine/jobsite/fd_farm",
          "village/mushroom/jobsite/fd_farm",
          "village/plains/jobsite/fd_farm",
          "village/plains_fortified/jobsite/fd_farm",
          "village/savanna/jobsite/fd_farm",
          "village/savanna_na/jobsite/fd_farm",
          "village/snowy_igloo/jobsite/fd_farm",
          "village/swamp/jobsite/fd_farm",
          "village/swamp_fortified/jobsite/fd_farm",
          "village/taiga/jobsite/fd_farm",
          "village/taiga_fortified/jobsite/fd_farm");

  private static final Set<String> PHILIPS =
      Set.of(
          "crypt_room1",
          "crypt_room2",
          "crypt_room4",
          "crypt_room5",
          "crypt_room7",
          "crypt_start",
          "level_ruin_three8",
          "ocean_ruin9");

  private static final Set<String> ANCIENT_CITY =
      Set.of(
          "ancient_city/city/entrance/entrance_connector",
          "ancient_city/city/entrance/entrance_path_1",
          "ancient_city/city/entrance/entrance_path_2",
          "ancient_city/city/entrance/entrance_path_3",
          "ancient_city/city/entrance/entrance_path_4",
          "ancient_city/city/entrance/entrance_path_5",
          "ancient_city/city_center/city_center_1",
          "ancient_city/city_center/city_center_2",
          "ancient_city/city_center/city_center_3",
          "ancient_city/city_center/walls/bottom_1",
          "ancient_city/city_center/walls/bottom_2",
          "ancient_city/city_center/walls/bottom_left_corner",
          "ancient_city/city_center/walls/bottom_right_corner_1",
          "ancient_city/city_center/walls/bottom_right_corner_2",
          "ancient_city/city_center/walls/left",
          "ancient_city/city_center/walls/right",
          "ancient_city/city_center/walls/top",
          "ancient_city/city_center/walls/top_left_corner",
          "ancient_city/city_center/walls/top_right_corner",
          "ancient_city/structures/barracks",
          "ancient_city/structures/camp_1",
          "ancient_city/structures/camp_2",
          "ancient_city/structures/camp_3",
          "ancient_city/structures/chamber_1",
          "ancient_city/structures/chamber_2",
          "ancient_city/structures/chamber_3",
          "ancient_city/structures/ice_box_1",
          "ancient_city/structures/large_pillar_1",
          "ancient_city/structures/large_ruin_1",
          "ancient_city/structures/medium_pillar_1",
          "ancient_city/structures/medium_ruin_1",
          "ancient_city/structures/medium_ruin_2",
          "ancient_city/structures/sauna_1",
          "ancient_city/structures/small_ruin_1",
          "ancient_city/structures/small_ruin_2",
          "ancient_city/structures/small_statue",
          "ancient_city/structures/tall_ruin_1",
          "ancient_city/structures/tall_ruin_2",
          "ancient_city/structures/tall_ruin_3",
          "ancient_city/structures/tall_ruin_4",
          "ancient_city/walls/intact_corner_wall_1",
          "ancient_city/walls/intact_horizontal_wall_1",
          "ancient_city/walls/intact_horizontal_wall_2",
          "ancient_city/walls/intact_horizontal_wall_bridge",
          "ancient_city/walls/intact_horizontal_wall_passage_1",
          "ancient_city/walls/intact_horizontal_wall_stairs_1",
          "ancient_city/walls/intact_horizontal_wall_stairs_2",
          "ancient_city/walls/intact_horizontal_wall_stairs_3",
          "ancient_city/walls/intact_horizontal_wall_stairs_4",
          "ancient_city/walls/intact_intersection_wall_1",
          "ancient_city/walls/intact_lshape_wall_1",
          "ancient_city/walls/ruined_corner_wall_1",
          "ancient_city/walls/ruined_corner_wall_2",
          "ancient_city/walls/ruined_horizontal_wall_stairs_1",
          "ancient_city/walls/ruined_horizontal_wall_stairs_2",
          "ancient_city/walls/ruined_horizontal_wall_stairs_3",
          "ancient_city/walls/ruined_horizontal_wall_stairs_4");

  private static final String EXPERIMENT_LAB = "pink_salt_shrine/second_rooms/experiment_lab";
  private static final String TREASURE_ROOM = "pink_salt_shrine/second_rooms/treasure_room";
  private static final Map<String, String> PHILIPS_BLOCKS =
      Map.of(
          "philipsruins:chiseled_bone_bricks", "minecraft:bone_block",
          "philipsruins:fractured_bone_bricks", "minecraft:bone_block",
          "philipsruins:bone_bricks", "minecraft:bone_block",
          "philipsruins:bone_chunk_block", "minecraft:bone_block",
          "philipsruins:bone_bricks_slab", "minecraft:quartz_slab",
          "philipsruins:sand_stone_brick", "minecraft:cut_sandstone",
          "philipsruins:sandstone_tiles", "minecraft:smooth_sandstone");

  private RuntimeTemplateRepairs() {}

  public static boolean applies(ResourceLocation id) {
    return switch (id.getNamespace()) {
      case "ctov" -> CTOV.contains(id.getPath());
      case "philipsruins" -> PHILIPS.contains(id.getPath());
      case "galosphere" ->
          id.getPath().equals(EXPERIMENT_LAB) || id.getPath().equals(TREASURE_ROOM);
      case "minecraft" -> ANCIENT_CITY.contains(id.getPath());
      default -> false;
    };
  }

  /**
   * Mutates only a private decoded resource, never a shared template or saved/generated structure.
   */
  public static void apply(ResourceLocation id, CompoundTag data) {
    if (!applies(id)) return;
    switch (id.getNamespace()) {
      case "ctov" -> {
        replacePalette(data, Map.of("farmersdelight:rice_panicle", "farmersdelight:rice_panicles"));
        repairAllayListeners(data);
      }
      case "philipsruins" -> replacePalette(data, PHILIPS_BLOCKS);
      case "galosphere" -> {
        if (id.getPath().equals(TREASURE_ROOM)) {
          replacePalette(data, Map.of("galosphere:silver_balance", "minecraft:air"));
        } else {
          removeMissingInventoryItems(data);
        }
      }
      case "minecraft" -> fillCityAir(data);
      default -> throw new IllegalStateException("Unrecognized template repair " + id);
    }
  }

  private static void replacePalette(CompoundTag data, Map<String, String> replacements) {
    if (data.contains("palettes", Tag.TAG_LIST)) {
      for (Tag entry : data.getList("palettes", Tag.TAG_LIST)) {
        replacePaletteEntries((ListTag) entry, replacements);
      }
    } else {
      replacePaletteEntries(data.getList("palette", Tag.TAG_COMPOUND), replacements);
    }
  }

  private static void replacePaletteEntries(ListTag palette, Map<String, String> replacements) {
    for (Tag entry : palette) {
      CompoundTag state = (CompoundTag) entry;
      String replacement = replacements.get(state.getString("Name"));
      if (replacement != null) {
        state.putString("Name", replacement);
        if (replacement.equals("minecraft:air")) state.remove("Properties");
      }
    }
  }

  private static void repairAllayListeners(CompoundTag data) {
    for (Tag entry : data.getList("entities", Tag.TAG_COMPOUND)) {
      CompoundTag entity = ((CompoundTag) entry).getCompound("nbt");
      if (entity.getString("id").equals("minecraft:allay")
          && entity.contains("listener", Tag.TAG_COMPOUND)) {
        CompoundTag listener = entity.getCompound("listener");
        if (!listener.contains("selector")) {
          CompoundTag selector = new CompoundTag();
          selector.putLong("tick", -1L);
          listener.put("selector", selector);
        }
      }
    }
  }

  private static void removeMissingInventoryItems(CompoundTag data) {
    for (Tag entry : data.getList("blocks", Tag.TAG_COMPOUND)) {
      ListTag items = ((CompoundTag) entry).getCompound("nbt").getList("Items", Tag.TAG_COMPOUND);
      items.removeIf(
          item -> {
            String id = ((CompoundTag) item).getString("id");
            return id.equals("galosphere:succulent_petals")
                || id.equals("galosphere:pink_salt_chamber");
          });
    }
  }

  private static void fillCityAir(CompoundTag data) {
    ListTag size = data.getList("size", Tag.TAG_INT);
    int width = size.getInt(0);
    int height = size.getInt(1);
    int depth = size.getInt(2);
    ListTag blocks = data.getList("blocks", Tag.TAG_COMPOUND);
    BitSet occupied = new BitSet(width * height * depth);
    for (Tag entry : blocks) {
      ListTag position = ((CompoundTag) entry).getList("pos", Tag.TAG_INT);
      occupied.set((position.getInt(0) * height + position.getInt(1)) * depth + position.getInt(2));
    }
    ListTag palette = data.getList("palette", Tag.TAG_COMPOUND);
    int airIndex = -1;
    for (int index = 0; index < palette.size(); index++) {
      if (palette.getCompound(index).getString("Name").equals("minecraft:air")) airIndex = index;
    }
    if (airIndex < 0) {
      airIndex = palette.size();
      CompoundTag air = new CompoundTag();
      air.putString("Name", "minecraft:air");
      palette.add(air);
    }
    // Match the former version-bound override: original entries first, then X/Y/Z order,
    // leaving the entire bottom layer and every authored block/entity/unknown field intact.
    for (int x = 0; x < width; x++) {
      for (int y = 1; y < height; y++) {
        for (int z = 0; z < depth; z++) {
          if (!occupied.get((x * height + y) * depth + z)) {
            CompoundTag block = new CompoundTag();
            ListTag position = new ListTag();
            position.addAll(List.of(IntTag.valueOf(x), IntTag.valueOf(y), IntTag.valueOf(z)));
            block.put("pos", position);
            block.putInt("state", airIndex);
            blocks.add(block);
          }
        }
      }
    }
  }
}
