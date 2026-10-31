package dev.aeroscapes.regression;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.HangingEntityAnchorProcessor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Live-registry coverage for biome-source, village-set and template-data repairs. */
public final class WorldgenDataRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final List<String> REPAIRED_POOLS =
      List.of(
          "philipsruins:ancient_dungeon",
          "philipsruins:ancient_ruins",
          "philipsruins:level_two_ruins",
          "philipsruins:level_two_ruins_pool",
          "philipsruins:underground_structures",
          "natures_spirit:village/cypress/houses",
          "natures_spirit:village/adobe/adobe_houses_layer_1_top",
          "natures_spirit:village/adobe/adobe_houses_layer_1_back",
          "natures_spirit:village/adobe/adobe_houses_layer_1_back_2",
          "terralith:village/desert/houses",
          "terralith:village/desert/houses_extra");

  private static final Set<String> HANGING_TEMPLATES =
      Set.of(
          "minecraft:illager_mansion/manor_fixed/manor_lighting_house_left_corner",
          "minecraft:illager_mansion/manor_fixed/manor_lighting_house_right_corner",
          "minecraft:illager_mansion/mansion_house_2x4_1",
          "minecraft:illager_mansion/mansion_house_2x4_2",
          "minecraft:illager_mansion/mansion_house_2x4_3",
          "minecraft:illager_mansion/mansion_house_2x5_left_1",
          "minecraft:illager_mansion/mansion_house_2x5_left_2",
          "minecraft:illager_mansion/mansion_house_2x5_left_3",
          "minecraft:illager_mansion/mansion_house_2x5_right_1",
          "minecraft:illager_mansion/mansion_house_2x5_right_2",
          "minecraft:illager_mansion/mansion_house_2x5_right_3",
          "minecraft:illager_mansion/mansion_house_2x6_cross_1",
          "minecraft:illager_mansion/mansion_house_2x6_cross_2",
          "minecraft:illager_mansion/mansion_house_2x6_cross_3",
          "minecraft:illager_mansion/mansion_house_2xcurve_left_1",
          "minecraft:illager_mansion/mansion_house_2xcurve_left_2",
          "minecraft:illager_mansion/mansion_house_2xcurve_left_3",
          "minecraft:illager_mansion/mansion_house_2xcurve_right_1",
          "minecraft:illager_mansion/mansion_house_2xcurve_right_2",
          "minecraft:illager_mansion/mansion_house_2xcurve_right_3",
          "minecraft:illager_mansion/mansion_house_3x8_left_1",
          "minecraft:illager_mansion/mansion_house_3x8_left_2",
          "minecraft:illager_mansion/mansion_house_3x8_right_1",
          "minecraft:illager_mansion/mansion_house_3x8_right_2",
          "minecraft:illager_mansion/mansion_house_library_right_1",
          "minecraft:illager_mansion/mansion_house_library_right_2",
          "nova_structures:illager_hideout/illager_hideout_5x6_2",
          "nova_structures:underground_house/underground_house_1",
          "nova_structures:underground_house/underground_house_5",
          "nova_structures:underground_house/underground_house_trap_1",
          "nova_structures:underground_house/underground_house_trap_5",
          "philipsruins:level_three_ruins2",
          "philipsruins:walkway3");
  private static final List<String> HANGING_POOLS =
      List.of(
          "minecraft:illager_mansion/illager_mansion_house_part",
          "minecraft:illager_mansion/illager_mansion_house_part_straight",
          "minecraft:illager_mansion/illager_mansion_main",
          "nova_structures:illager_hideout_room",
          "nova_structures:underground_house",
          "philipsruins:level_three_ruins",
          "philipsruins:lost_soul_dungeon/side_pool3");

  private WorldgenDataRegression() {}

  public static void verify(ServerLevel level) {
    VoidWeatherRegression.biomeContract(level);
    var generator = level.getChunkSource().getGenerator();
    var possible = generator.getBiomeSource().possibleBiomes();
    var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
    for (String name :
        List.of(
            "aeroscapes:fractured_jungle",
            "terralith:glacial_chasm",
            "terralith:amethyst_rainforest",
            "terralith:cave/fungal_caves",
            "terralith:cave/mantle_caves",
            "terralith:cave/thermal_caves")) {
      var holder = biomes.getHolder(ResourceLocation.parse(name));
      require(
          holder.isPresent() && possible.contains(holder.orElseThrow()), "Missing biome " + name);
    }
    var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    for (String family : List.of("cypress", "wisteria", "adobe", "coconut")) {
      var holder = structures.getHolder(ResourceLocation.parse("natures_spirit:village_" + family));
      require(holder.isPresent(), "Missing Nature's Spirit village " + family);
      require(
          !level
              .getChunkSource()
              .getGeneratorState()
              .getPlacementsForStructure(holder.orElseThrow())
              .isEmpty(),
          "No natural village placement for " + family);
    }
    var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
    for (String name : REPAIRED_POOLS) {
      var pool = pools.get(ResourceLocation.parse(name));
      require(pool != null && pool.size() > 0, "Missing or empty repaired pool " + name);
      if (pool == null) {
        throw new IllegalStateException("Pool disappeared after validation");
      }
      for (var element : pool.getShuffledTemplates(RandomSource.create(1))) {
        if (element instanceof EmptyPoolElement) {
          continue;
        }
        var size = element.getSize(level.getStructureManager(), Rotation.NONE);
        require(
            size.getX() > 0 && size.getY() > 0 && size.getZ() > 0,
            "Unresolvable template in repaired pool " + name + ": " + element);
      }
    }
    hangingProcessorWiring(level);
    hangingAnchors(level);
    GroundedColumnRegression.verify();
    GroundedColumnRegression.verifyWitchWiring(level);
    ProjectionRegression.verify();
    ProjectionRegression.verifyWiring(level);
    StoneTransitionRegression.verify(level);
    if (level.getSeed() == 1) {
      naturalBiome(level, "terralith:sandstone_valley", new BlockPos(-16384, 231, -7680));
      naturalBiome(level, "terralith:birch_taiga", new BlockPos(-15360, 217, 1536));
      naturalBiome(level, "terralith:glacial_chasm", new BlockPos(-5632, 217, 7168));
      naturalBiome(level, "minecraft:ice_spikes", new BlockPos(5122, 142, -1022));
      naturalBiome(level, "aeroscapes:fractured_jungle", new BlockPos(-6656, 87, 13568));
      naturalGeode(level);
      LOGGER.info(
          "Worldgen data invariants checked: live registry and seed-1 natural biome/geode checks");
    } else {
      LOGGER.info(
          "Worldgen data registry invariants checked; seed-1 natural biome/geode fixtures skipped for seed {}",
          level.getSeed());
    }
  }

  private static void naturalGeode(ServerLevel level) {
    BlockPos origin = new BlockPos(16403, 177, 8721);
    for (int chunkX = (origin.getX() - 16) >> 4; chunkX <= (origin.getX() + 16) >> 4; chunkX++) {
      for (int chunkZ = (origin.getZ() - 16) >> 4; chunkZ <= (origin.getZ() + 16) >> 4; chunkZ++) {
        level.getChunk(chunkX, chunkZ);
      }
    }
    boolean amethyst = false;
    boolean budding = false;
    for (BlockPos position :
        BlockPos.betweenClosed(origin.offset(-16, -16, -16), origin.offset(16, 16, 16))) {
      var state = level.getBlockState(position);
      amethyst |= state.is(Blocks.AMETHYST_BLOCK);
      budding |= state.is(Blocks.BUDDING_AMETHYST);
    }
    require(amethyst && budding, "Natural island geode lost its amethyst or budding core");
  }

  private static void hangingProcessorWiring(ServerLevel level) {
    Set<String> resolved = new HashSet<>();
    var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    for (String name : HANGING_POOLS) {
      var pool = pools.get(ResourceLocation.parse(name));
      require(pool != null, "Missing hanging-entity template pool " + name);
      if (pool == null) {
        throw new IllegalStateException("Pool disappeared after validation");
      }
      var encoded = StructureTemplatePool.DIRECT_CODEC.encodeStart(operations, pool).getOrThrow();
      hangingElements(encoded, resolved);
    }
    require(resolved.equals(HANGING_TEMPLATES), "Hanging processor misses templates " + resolved);
  }

  private static void hangingElements(JsonElement element, Set<String> resolved) {
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        hangingElements(child, resolved);
      }
    } else if (element.isJsonObject()) {
      var object = element.getAsJsonObject();
      if (object.has("location") && object.get("location").isJsonPrimitive()) {
        String template = object.get("location").getAsString();
        boolean expected = HANGING_TEMPLATES.contains(template);
        boolean attached =
            object.has("processors") && hasHangingProcessor(object.get("processors"));
        require(
            attached == expected,
            "Resolved pool element has incorrect hanging-processor scope: " + template);
        if (expected) {
          resolved.add(template);
        }
      }
      for (JsonElement child : object.asMap().values()) {
        hangingElements(child, resolved);
      }
    }
  }

  private static boolean hasHangingProcessor(JsonElement element) {
    if (element.isJsonPrimitive()) {
      return element.getAsString().equals("aeroscapes_reimagine:hanging_entity_anchor");
    }
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        if (hasHangingProcessor(child)) {
          return true;
        }
      }
    } else if (element.isJsonObject()) {
      var object = element.getAsJsonObject();
      if (object.has("processor_type")
          && object
              .get("processor_type")
              .getAsString()
              .equals("aeroscapes_reimagine:hanging_entity_anchor")) {
        return true;
      }
      if (object.has("processors")) {
        return hasHangingProcessor(object.get("processors"));
      }
    }
    return false;
  }

  private static void naturalBiome(ServerLevel level, String biome, BlockPos position) {
    var generator = level.getChunkSource().getGenerator();
    var randomState = level.getChunkSource().randomState();
    require(
        generator
            .getBiomeSource()
            .getNoiseBiome(
                position.getX() >> 2,
                position.getY() >> 2,
                position.getZ() >> 2,
                randomState.sampler())
            .is(ResourceLocation.parse(biome)),
        "Lost selected island biome " + biome + " at " + position);
    require(
        !generator
            .getBaseColumn(position.getX(), position.getZ(), level, randomState)
            .getBlock(position.getY())
            .isAir(),
        "Selected biome has no natural terrain at " + position);
  }

  private static void hangingAnchors(ServerLevel level) {
    CompoundTag original = new CompoundTag();
    original.putString("id", "minecraft:item_frame");
    original.putInt("TileX", 311);
    original.putInt("TileY", 50);
    original.putInt("TileZ", 214);
    original.putByte("Facing", (byte) 3);
    CompoundTag item = new CompoundTag();
    item.putString("id", "minecraft:quartz");
    item.putInt("count", 1);
    original.put("Item", item);
    var entity =
        new StructureTemplate.StructureEntityInfo(
            new Vec3(21.5, 4.5, 1.03125), new BlockPos(21, 4, 1), original);
    hangingAnchor(level, entity);
    var nativeTemplate =
        level
            .getStructureManager()
            .get(ResourceLocation.parse("nova_structures:underground_house/underground_house_1"))
            .orElseThrow()
            .save(new CompoundTag());
    ListTag nativeEntities = nativeTemplate.getList("entities", 10);
    for (int index = 0; index < nativeEntities.size(); index++) {
      CompoundTag record = nativeEntities.getCompound(index);
      CompoundTag nbt = record.getCompound("nbt");
      if (!nbt.getString("id").equals("minecraft:painting")) {
        continue;
      }
      ListTag position = record.getList("pos", 6);
      ListTag blockPosition = record.getList("blockPos", 3);
      hangingAnchor(
          level,
          new StructureTemplate.StructureEntityInfo(
              new Vec3(position.getDouble(0), position.getDouble(1), position.getDouble(2)),
              new BlockPos(
                  blockPosition.getInt(0), blockPosition.getInt(1), blockPosition.getInt(2)),
              nbt));
      return;
    }
    throw new IllegalStateException("Native underground-house painting was discarded");
  }

  private static void hangingAnchor(
      ServerLevel level, StructureTemplate.StructureEntityInfo entity) {
    CompoundTag original = entity.nbt;
    CompoundTag before = original.copy();
    BlockPos origin = new BlockPos(1000, 200, -1000);
    for (Rotation rotation : Rotation.values()) {
      for (Mirror mirror : Mirror.values()) {
        var settings =
            new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .addProcessor(HangingEntityAnchorProcessor.INSTANCE);
        var placed =
            StructureTemplate.processEntityInfos(
                new StructureTemplate(), level, origin, settings, List.of(entity));
        require(placed.size() == 1, "Hanging repair discarded an entity");
        var transformed = placed.getFirst();
        require(
            transformed.nbt.getInt("TileX") == transformed.blockPos.getX()
                && transformed.nbt.getInt("TileY") == transformed.blockPos.getY()
                && transformed.nbt.getInt("TileZ") == transformed.blockPos.getZ(),
            "Hanging anchor did not follow " + rotation + "/" + mirror);
        CompoundTag expected = before.copy();
        expected.putInt("TileX", transformed.blockPos.getX());
        expected.putInt("TileY", transformed.blockPos.getY());
        expected.putInt("TileZ", transformed.blockPos.getZ());
        require(transformed.nbt.equals(expected), "Hanging repair changed unrelated entity data");
        require(original.equals(before), "Hanging repair mutated the shared native template");
        CompoundTag placedNbt = transformed.nbt.copy();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(transformed.pos.x));
        position.add(DoubleTag.valueOf(transformed.pos.y));
        position.add(DoubleTag.valueOf(transformed.pos.z));
        placedNbt.put("Pos", position);
        var loaded = EntityType.create(placedNbt, level).orElseThrow();
        require(
            loaded instanceof HangingEntity hanging
                && hanging.getPos().equals(transformed.blockPos),
            "Native entity loading lost the hanging anchor");
        if (loaded instanceof ItemFrame frame) {
          require(
              frame.getItem().is(Items.QUARTZ) && frame.getItem().getCount() == 1,
              "Native entity loading lost the frame's item");
        } else if (loaded instanceof Painting painting) {
          require(
              painting
                  .getVariant()
                  .unwrapKey()
                  .orElseThrow()
                  .location()
                  .equals(ResourceLocation.parse(before.getString("variant"))),
              "Native entity loading changed the painting variant");
        }
      }
    }
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) {
      throw new IllegalStateException(failure);
    }
  }
}
