package dev.aeroscapes.regression;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.NonVoidProjectionProcessor;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Compare the guard's boundary decisions and accepted positions with native gravity. */
final class ProjectionRegression {
  private static final Set<String> POOLS =
      Set.of(
          "nova_structures:remnant/big_remnant_2",
          "nova_structures:remnant/big_remnant_3",
          "nova_structures:remnant/medium_remnant",
          "nova_structures:remnant/medium_remnant_2",
          "nova_structures:remnant/village_road",
          "philipsruins:field_stone_ruins",
          "philipsruins:pumpkin_ruins");
  private static final String PROCESSOR = "aeroscapes_reimagine:non_void_projection";

  private ProjectionRegression() {}

  static void verify() {
    for (Rotation rotation : Rotation.values()) {
      check(-64, 2, rotation, false);
      check(-63, 0, rotation, true);
      check(220, 2, rotation, true);
      check(220, -284, rotation, false);
      check(220, -283, rotation, true);
      check(316, 4, rotation, true);
      check(316, 5, rotation, false);
    }
  }

  static void verifyWiring(ServerLevel level) {
    var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
    var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    Set<String> attachedTemplates = new HashSet<>();
    for (var pool : pools) {
      String name = pools.getKey(pool).toString();
      var encoded = StructureTemplatePool.DIRECT_CODEC.encodeStart(operations, pool).getOrThrow();
      checkElements(encoded, POOLS.contains(name), attachedTemplates);
    }
    if (attachedTemplates.size() != 39) {
      throw new IllegalStateException("Incomplete live projection scope: " + attachedTemplates);
    }
    BlockPos spawn = level.getSharedSpawnPos();
    int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ());
    var original =
        new StructureTemplate.StructureBlockInfo(
            new BlockPos(0, 2, 0), Blocks.STONE.defaultBlockState(), null);
    var transformed = new StructureTemplate.StructureBlockInfo(spawn, original.state(), null);
    var settings = new StructurePlaceSettings();
    var guarded =
        NonVoidProjectionProcessor.INSTANCE.process(
            level, BlockPos.ZERO, BlockPos.ZERO, original, transformed, settings, null);
    var projected =
        new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, -1)
            .process(level, BlockPos.ZERO, BlockPos.ZERO, original, transformed, settings, null);
    if (height <= level.getMinBuildHeight()
        || guarded != transformed
        || projected == null
        || projected.pos().getY() != height + 1) {
      throw new IllegalStateException("ServerLevel projection differs from native surface height");
    }
  }

  private static void checkElements(
      JsonElement element, boolean selectedPool, Set<String> templates) {
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        checkElements(child, selectedPool, templates);
      }
    } else if (element.isJsonObject()) {
      var object = element.getAsJsonObject();
      if (object.has("location") && object.get("location").isJsonPrimitive()) {
        boolean expected =
            selectedPool
                && object.has("projection")
                && object.get("projection").getAsString().equals("terrain_matching");
        boolean attached = object.has("processors") && hasProcessor(object.get("processors"));
        if (attached != expected) {
          throw new IllegalStateException("Incorrect live projection-processor scope: " + object);
        }
        if (attached) {
          templates.add(object.get("location").getAsString());
        }
      }
      for (JsonElement child : object.asMap().values()) {
        checkElements(child, selectedPool, templates);
      }
    }
  }

  private static boolean hasProcessor(JsonElement element) {
    if (element.isJsonPrimitive()) {
      return element.getAsString().equals(PROCESSOR);
    }
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        if (hasProcessor(child)) return true;
      }
    } else if (element.isJsonObject()) {
      for (JsonElement child : element.getAsJsonObject().asMap().values()) {
        if (hasProcessor(child)) return true;
      }
    }
    return false;
  }

  private static void check(int height, int localY, Rotation rotation, boolean accepted) {
    BlockPos local = new BlockPos(7, localY, 3);
    BlockPos position = local.rotate(rotation).offset(-13808, 120, 12256);
    AtomicInteger reads = new AtomicInteger();
    LevelReader level =
        (LevelReader)
            Proxy.newProxyInstance(
                LevelReader.class.getClassLoader(),
                new Class<?>[] {LevelReader.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("getHeight") && arguments != null) {
                    if (arguments[0] != Heightmap.Types.WORLD_SURFACE_WG
                        || (Integer) arguments[1] != position.getX()
                        || (Integer) arguments[2] != position.getZ()) {
                      throw new IllegalStateException("Projection queried a different column");
                    }
                    reads.incrementAndGet();
                    return height;
                  }
                  if (method.getName().equals("getMinBuildHeight")) {
                    return -64;
                  }
                  if (method.getName().equals("isOutsideBuildHeight") && arguments != null) {
                    int y = (Integer) arguments[0];
                    return y < -64 || y >= 320;
                  }
                  throw new IllegalStateException("Unexpected projection world call " + method);
                });
    CompoundTag data = new CompoundTag();
    data.putString("LootTable", "philipsruins:chest/level_three_ruins_loot");
    var state =
        Blocks.CHEST
            .defaultBlockState()
            .setValue(ChestBlock.FACING, rotation.rotate(Direction.NORTH));
    var original = new StructureTemplate.StructureBlockInfo(local, state, data);
    var transformed = new StructureTemplate.StructureBlockInfo(position, state, data);
    var settings = new StructurePlaceSettings().setRotation(rotation);
    var guarded =
        NonVoidProjectionProcessor.INSTANCE.process(
            level, BlockPos.ZERO, BlockPos.ZERO, original, transformed, settings, null);
    if ((guarded != null) != accepted || reads.get() != 1) {
      throw new IllegalStateException("Incorrect projection boundary at " + height + "/" + localY);
    }
    if (accepted) {
      var projected =
          new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, -1)
              .process(level, BlockPos.ZERO, BlockPos.ZERO, original, guarded, settings, null);
      if (guarded != transformed
          || projected == null
          || !projected
              .pos()
              .equals(new BlockPos(position.getX(), height - 1 + localY, position.getZ()))
          || projected.nbt() != data
          || projected.state() != state) {
        throw new IllegalStateException("Accepted native projection or loot was changed");
      }
    }
  }
}
