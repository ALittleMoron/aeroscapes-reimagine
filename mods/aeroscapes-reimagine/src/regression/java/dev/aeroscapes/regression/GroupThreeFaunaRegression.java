package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.phys.Vec3;

/** Native animal interactions and actual basin spawn predicates on a disposable server. */
public final class GroupThreeFaunaRegression {
  private GroupThreeFaunaRegression() {}

  public static void verify(ServerLevel level, Path output) {
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("18ba6c26-444c-46ee-a342-fc1200ec233c"),
            "FaunaRegression",
            Vec3.atCenterOf(level.getSharedSpawnPos().above(2)),
            null)) {
      bird(level, session.player);
      elephant(level, session.player);
      OtterFeedingRegression.verify(level, session.player);
      report.addProperty("bird_taming_without_head_mount", true);
      report.addProperty("elephant_five_cakes_chest_saddle_cargo_roundtrip", true);
      report.addProperty("otter_feeding_one_food_survival_and_creative", true);
    }
    report.add("basin_spawn_predicates", basins(level));
    report.add("natural_water_habitats", FaunaWaterHabitatRegression.run(level));
    report.add("cocoon_height_samples", cocoons(level));
    report.add("unsupported_dragonfly_air", unsupportedDragonflyAir(level));
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write fauna regression evidence", failure);
    }
  }

  private static void bird(ServerLevel level, ServerPlayer player) {
    TamableAnimal bird = animal(level, "naturalist:bird");
    try {
      bird.tame(player);
      bird.setOrderedToSit(false);
      bird.moveTo(player.position());
      require(
          bird.getOwner() == player && bird.getBoundingBox().intersects(player.getBoundingBox()),
          "Head-mount reproduction requires an overlapping owner");
      var goalClass =
          Class.forName(
              "com.crispytwig.naturalist.world.entity.animal.bird.Bird$MountOnOwnersHeadGoal");
      var constructor = goalClass.getDeclaredConstructor(bird.getClass());
      require(constructor.trySetAccessible(), "Cannot inspect the native head-mount goal");
      Goal goal = (Goal) constructor.newInstance(bird);
      require(!goal.canUse(), "Tamed bird still attempts to mount its owner's head");
      require(!bird.isPassenger(), "Bird unexpectedly became a passenger");
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot exercise pinned Naturalist head-mount goal", failure);
    } finally {
      bird.discard();
    }
  }

  private static void elephant(ServerLevel level, ServerPlayer player) {
    TamableAnimal elephant = animal(level, "naturalist:elephant");
    try {
      elephant.setBaby(true);
      for (int count = 0; count < 5; count++) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
        elephant.interact(player, InteractionHand.MAIN_HAND);
        require(elephant.isTame() == (count == 4), "Elephant taming must require five cakes");
      }
      require(elephant.isOwnedBy(player), "Elephant owner not retained");
      elephant.setAge(0);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
      elephant.interact(player, InteractionHand.MAIN_HAND);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
      elephant.interact(player, InteractionHand.MAIN_HAND);
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      player.setShiftKeyDown(true);
      elephant.interact(player, InteractionHand.MAIN_HAND);
      require(
          player.containerMenu.slots.size() == 63,
          "Elephant menu must have 25 cargo and 2 equipment slots");
      require(
          player.containerMenu.getSlot(25).getItem().is(Items.SADDLE),
          "Native saddle interaction lost equipment");
      player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND, 7));
      CompoundTag saved = new CompoundTag();
      elephant.save(saved);
      player.closeContainer();
      TamableAnimal restored = animal(level, "naturalist:elephant");
      try {
        restored.load(saved);
        require(restored.isOwnedBy(player), "Saved elephant lost ownership");
        restored.interact(player, InteractionHand.MAIN_HAND);
        require(
            player.containerMenu.slots.size() == 63
                && player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND)
                && player.containerMenu.getSlot(0).getItem().getCount() == 7
                && player.containerMenu.getSlot(25).getItem().is(Items.SADDLE),
            "Saved elephant lost or duplicated cargo/equipment");
      } finally {
        restored.discard();
      }
    } finally {
      player.closeContainer();
      player.setShiftKeyDown(false);
      elephant.discard();
    }
  }

  private static TamableAnimal animal(ServerLevel level, String id) {
    Entity animal = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)).create(level);
    require(animal instanceof TamableAnimal, "Missing native tameable animal " + id);
    return (TamableAnimal) animal;
  }

  private static JsonArray basins(ServerLevel level) {
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Fauna evidence requires owned terrain");
    JsonArray result = new JsonArray();
    boolean upper = false;
    boolean lower = false;
    for (int x = -8192; x <= 8192 && !(upper && lower); x += 1024) {
      for (int z = -8192; z <= 8192 && !(upper && lower); z += 1024) {
        var basin = terrain.basin(x, z);
        if (basin == null || (basin.waterline() >= 176 ? upper : lower)) continue;
        BlockPos position = new BlockPos(basin.centerX(), basin.waterline() - 5, basin.centerZ());
        if (!level.getFluidState(position).is(FluidTags.WATER)) continue;
        BlockPos air = new BlockPos(position.getX(), basin.waterline() + 10, position.getZ());
        if (!level.getBlockState(air).isAir() || !level.getBlockState(air.below()).isAir())
          continue;
        require(
            spawns(level, "naturalist:piranha", position),
            "Piranha rejected actual basin water " + position);
        require(
            spawns(level, "crittersandcompanions:otter", position),
            "Otter rejected actual basin water " + position);
        require(
            !spawns(level, "crittersandcompanions:otter", air), "Otter accepted unsupported air");
        JsonObject row = new JsonObject();
        row.addProperty("x", position.getX());
        row.addProperty("y", position.getY());
        row.addProperty("z", position.getZ());
        row.addProperty("waterline", basin.waterline());
        row.addProperty("piranha_and_otter_in_water_no_otter_in_air", true);
        result.add(row);
        if (basin.waterline() >= 176) upper = true;
        else lower = true;
      }
    }
    require(upper && lower, "No witnesses for both native basin-height bands found");
    return result;
  }

  private static boolean spawns(ServerLevel level, String id, BlockPos position) {
    return SpawnPlacements.checkSpawnRules(
        BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id)),
        level,
        MobSpawnType.NATURAL,
        position,
        level.random);
  }

  private static JsonObject unsupportedDragonflyAir(ServerLevel level) {
    var generator = level.getChunkSource().getGenerator();
    for (int x = -1024; x <= 1024; x += 128)
      for (int z = -1024; z <= 1024; z += 128) {
        if (generator.getBaseHeight(
                x,
                z,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                level,
                level.getChunkSource().randomState())
            > 64) continue;
        BlockPos position = new BlockPos(x, 64, z);
        if (!level.getBlockState(position).isAir()
            || level.getRawBrightness(position, 0) <= 8
            || dev.aeroscapes.fauna.FaunaHabitats.surface(63, level, position)
                != level.getMinBuildHeight() - 17) continue;
        require(
            !spawns(level, "crittersandcompanions:dragonfly", position),
            "Dragonfly accepted unsupported bright air near the former global sea level");
        JsonObject report = new JsonObject();
        report.addProperty("x", x);
        report.addProperty("y", 64);
        report.addProperty("z", z);
        return report;
      }
    throw new IllegalStateException("No unsupported bright-air control found");
  }

  private static JsonArray cocoons(ServerLevel level) {
    JsonArray report = new JsonArray();
    var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
    for (String name :
        List.of(
            "silk_cocoon", "silk_cocoon_lush", "hanging_silk_cocoon", "hanging_silk_cocoon_lush")) {
      var feature =
          registry.get(ResourceLocation.fromNamespaceAndPath("crittersandcompanions", name));
      require(feature != null, "Missing native cocoon feature " + name);
      var range =
          feature.placement().stream()
              .filter(HeightRangePlacement.class::isInstance)
              .map(HeightRangePlacement.class::cast)
              .findFirst()
              .orElseThrow();
      var context =
          new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(feature));
      RandomSource random = RandomSource.create(78231);
      RandomSource expected = RandomSource.create(78231);
      int minimum = Integer.MAX_VALUE;
      int maximum = Integer.MIN_VALUE;
      for (int sample = 0; sample < 256; sample++) {
        var positions = range.getPositions(context, random, BlockPos.ZERO).toList();
        require(positions.size() == 1, "Cocoon height selection multiplied native attempts");
        int height = positions.getFirst().getY();
        require(
            height >= level.getMinBuildHeight() + 16 && height <= level.getMaxBuildHeight() - 17,
            "Cocoon height escaped the scan-safe dimension bounds");
        expected.nextInt(level.getHeight() - 32);
        require(
            random.nextLong() == expected.nextLong(),
            "Cocoon height selection changed RNG draw count");
        minimum = Math.min(minimum, height);
        maximum = Math.max(maximum, height);
      }
      require(minimum < 0 && maximum > 140, "Cocoon height sampling still excludes island layers");
      JsonObject row = new JsonObject();
      row.addProperty("feature", name);
      row.addProperty("minimum_sampled", minimum);
      row.addProperty("maximum_sampled", maximum);
      row.addProperty("one_position_and_one_draw", true);
      report.add(row);
    }
    var vanilla =
        HeightRangePlacement.uniform(VerticalAnchor.absolute(60), VerticalAnchor.absolute(140));
    var context =
        new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
    for (int sample = 0; sample < 64; sample++) {
      int height =
          vanilla
              .getPositions(context, RandomSource.create(sample), BlockPos.ZERO)
              .findFirst()
              .orElseThrow()
              .getY();
      require(height >= 60 && height <= 140, "Unrelated height provider was changed");
    }
    return report;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
