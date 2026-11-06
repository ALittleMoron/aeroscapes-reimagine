package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.util.LevelAccelerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import team.creative.littletiles.LittleTilesRegistry;
import team.creative.littletiles.api.common.tool.ILittleTool;
import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.block.little.tile.LittleTile;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.grid.LittleGrid;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import team.creative.littletiles.common.item.ItemMultiTiles;
import team.creative.littletiles.common.item.ItemPremadeStructure;
import team.creative.littletiles.common.math.box.LittleBox;

/** Native crafting, saved tile payloads and a force-loaded LittleTiles-only Sable body. */
public final class ConstructionRegression {
  private static final String MARKER = "aeroscapes_construction_fixture";
  private static final String BODY_LABEL = "aeroscapes_construction_body";
  private static final String PLATFORM_TOP = "aeroscapes_construction_platform_top";
  private final ServerLevel level;
  private final Path output;
  private final boolean reload;
  private final JsonObject report;
  private final JsonArray diagnostics = new JsonArray();
  private final Map<String, ServerSubLevel> diagnosticBodies = new LinkedHashMap<>();
  private final boolean diagnosticsOnly =
      Boolean.getBoolean("aeroscapes.regression.constructionDiagnostics");
  private ServerSubLevel ship;
  private int elapsed;
  private int retainedTicks;
  private boolean finished;

  private ConstructionRegression(ServerLevel level, Path output, boolean reload) {
    this.level = level;
    this.output = output;
    this.reload = reload;
    report = new JsonObject();
    report.add("body_diagnostics", diagnostics);
    report.addProperty("diagnostics_only", diagnosticsOnly);
  }

  public static void start(ServerLevel level, Path output, boolean reload) {
    ConstructionRegression fixture = new ConstructionRegression(level, output, reload);
    fixture.recipes();
    fixture.payloads();
    fixture.observeRemovals();
    if (!reload) fixture.assemble();
    NeoForge.EVENT_BUS.addListener(fixture::tick);
  }

  private void recipes() {
    ItemStack iron = new ItemStack(Items.IRON_INGOT);
    ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI);
    ItemStack empty = ItemStack.EMPTY;
    ItemStack chisel = craft("chisel", 1, 3, iron, iron, lapis);
    result(chisel, "chisel", 1);
    result(
        craft("hammer", 3, 3, iron, iron, iron, empty, lapis, empty, empty, lapis, empty),
        "hammer",
        1);
    result(craft("saw", 2, 3, lapis, iron, lapis, iron, lapis, empty), "saw", 1);
    ItemStack wrench = craft("wrench", 1, 3, iron, lapis, lapis);
    result(wrench, "wrench", 1);
    result(craft("measure_tape", 1, 3, new ItemStack(Items.LEAD), iron, lapis), "measure_tape", 1);
    ItemStack wool = new ItemStack(Items.WHITE_WOOL);
    ItemStack brush =
        craft("paint_brush", 3, 3, wool, wool, wool, empty, lapis, empty, empty, lapis, empty);
    result(brush, "paint_brush", 1);
    ItemStack leather = new ItemStack(Items.LEATHER);
    result(
        craft("glove", 3, 3, empty, leather, empty, leather, lapis, leather, empty, leather, empty),
        "glove",
        1);
    result(
        craft(
            "bag",
            3,
            3,
            empty,
            new ItemStack(Items.STRING),
            empty,
            leather,
            lapis,
            leather,
            empty,
            leather,
            empty),
        "bag",
        1);
    ItemStack paper = new ItemStack(Items.PAPER);
    ItemStack blueprint =
        craft(
            "blueprint",
            3,
            3,
            paper,
            paper,
            paper,
            paper,
            lapis,
            paper,
            paper,
            new ItemStack(Items.REDSTONE),
            paper);
    result(blueprint, "blueprint", 4);
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    ItemStack workbench =
        craft(
            "workbench",
            3,
            3,
            planks,
            new ItemStack(Items.CRAFTING_TABLE),
            planks,
            planks,
            wrench,
            planks,
            planks,
            empty,
            planks);
    premade(workbench, "workbench", 1);
    premade(
        craft(
            "blankomatic",
            3,
            3,
            planks,
            iron,
            planks,
            planks,
            brush,
            planks,
            planks,
            empty,
            planks),
        "blankomatic",
        1);
    ItemStack concrete = new ItemStack(Items.BLACK_CONCRETE);
    ItemStack cable =
        craft(
            "single_cable4",
            3,
            3,
            concrete,
            concrete,
            concrete,
            concrete,
            new ItemStack(Items.REDSTONE_BLOCK),
            concrete,
            concrete,
            concrete,
            concrete);
    premade(cable, "single_cable4", 32);
    premade(craft("single_cable1", 1, 1, cable.copyWithCount(1)), "single_cable1", 4);
    premade(craft("single_cable16", 2, 2, cable, cable, cable, cable), "single_cable16", 1);
    ItemStack alpha = craft("transparency", 1, 1, new ItemStack(Items.GLASS));
    result(alpha, "bottle_alpha", 1);
    require(
        alpha.getOrDefault(LittleTilesRegistry.COLOR_AMOUNT.value(), 0) == 4096,
        "Transparency recipe lost its native color volume");
    ItemStack lava = craft("colored_lava", 1, 1, new ItemStack(Items.LAVA_BUCKET));
    result(lava, "colored_lava", 1);
    result(
        craft("colored_white_lava", 2, 1, new ItemStack(Items.MILK_BUCKET), lava),
        "colored_white_lava",
        1);
    result(
        craft(
            "colored_water",
            2,
            1,
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.WATER_BUCKET)),
        "colored_water",
        2);
    report.addProperty("native_tools_bag_blueprint_functional_recipes", true);
    report.addProperty("premade_structure_components_and_material_remainders", true);
  }

  private ItemStack craft(String recipeName, int width, int height, ItemStack... items) {
    CraftingInput input = CraftingInput.of(width, height, List.of(items));
    var holder =
        level
            .getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath("littletiles", recipeName))
            .orElseThrow();
    require(
        holder.value() instanceof CraftingRecipe, "Expected native crafting recipe: " + recipeName);
    CraftingRecipe recipe = (CraftingRecipe) holder.value();
    require(recipe.matches(input, level), "Actual survival ingredients rejected: " + recipeName);
    ItemStack result = recipe.assemble(input, level.registryAccess());
    require(!result.isEmpty(), "Native recipe returned no result: " + recipeName);
    var remaining = recipe.getRemainingItems(input);
    require(remaining.size() == input.size(), "Remainder slot count changed: " + recipeName);
    for (int slot = 0; slot < input.size(); slot++) {
      ItemStack remainder = remaining.get(slot);
      boolean bucket =
          input.getItem(slot).is(Items.LAVA_BUCKET)
              || input.getItem(slot).is(Items.MILK_BUCKET)
              || input.getItem(slot).is(Items.WATER_BUCKET);
      require(
          bucket ? remainder.is(Items.BUCKET) && remainder.getCount() == 1 : remainder.isEmpty(),
          "Unexpected ingredient remainder: " + recipeName + " slot " + slot);
    }
    return result;
  }

  private void payloads() {
    LittleGroup group = new LittleGroup();
    group.add(
        LittleGrid.get(16),
        new LittleElement(Blocks.OAK_PLANKS.defaultBlockState(), -1),
        new LittleBox(0, 0, 0, 16, 8, 16));
    ItemStack structure = ItemMultiTiles.of(group);
    ItemStack restored = roundTrip(structure);
    require(
        ILittleTool.getData(structure).equals(ILittleTool.getData(restored)),
        "Placed-tile item payload changed through native serialization");
    require(
        LittleGroup.load(ILittleTool.getData(restored)).totalBoxes() == 1,
        "Restored tile geometry is empty");
    ItemStack blueprint = new ItemStack(LittleTilesRegistry.BLUEPRINT.value());
    ((ItemLittleBlueprint) blueprint.getItem()).saveTiles(blueprint, group);
    var blueprintData = ItemLittleBlueprint.getContent(blueprint);
    require(
        ItemLittleBlueprint.getContent(roundTrip(blueprint)).equals(blueprintData),
        "Native blueprint payload changed through item save/reload");
    require(
        LittleGroup.load(blueprintData).totalBoxes() == 1,
        "Native blueprint content could not be loaded");
    ItemStack premade = ItemPremadeStructure.of("workbench");
    require(
        ItemPremadeStructure.getPremadeId(roundTrip(premade)).equals("workbench"),
        "Native premade subtype lost through item save/reload");
    report.addProperty("native_structure_blueprint_premade_item_save_reload", true);
  }

  private ItemStack roundTrip(ItemStack original) {
    ItemStack restored =
        ItemStack.parse(level.registryAccess(), original.save(level.registryAccess()))
            .orElseThrow();
    require(
        ItemStack.isSameItemSameComponents(original, restored)
            && original.getCount() == restored.getCount(),
        "Native item save/reload changed components or count");
    return restored;
  }

  private void assemble() {
    BlockPos position = level.getSharedSpawnPos().above(48);
    shapeBoundaries(position.offset(0, 0, 8));
    level.getChunkAt(position);
    require(
        position.getY() < level.getMaxBuildHeight() && level.isEmptyBlock(position),
        "Construction fixture requires empty space above spawn");
    var state = LittleTilesRegistry.BLOCK_TILES.value().defaultBlockState();
    level.setBlockAndUpdate(position, state);
    require(level.getBlockEntity(position) instanceof BETiles, "Native tile block entity missing");
    BETiles tiles = (BETiles) level.getBlockEntity(position);
    tiles.convertTo(LittleGrid.get(16));
    tiles.updateTiles(
        collection ->
            collection
                .noneStructureTiles()
                .add(
                    new LittleTile(
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        -1,
                        new LittleBox(0, 0, 0, 16, 8, 16))));
    require(tiles.boxesCount() == 1, "Native tile insertion failed");
    var serialized = tiles.saveWithFullMetadata(level.registryAccess());
    BlockEntity restored =
        BlockEntity.loadStatic(position, state, serialized, level.registryAccess());
    require(
        restored instanceof BETiles saved && saved.boxesCount() == 1,
        "Native tile block entity lost its geometry on reload");
    double mass = PhysicsBlockPropertyHelper.getMass(level, position, state);
    require(mass > 0 && Double.isFinite(mass), "Native LittleTiles material mass is not positive");
    JsonObject source = blockSnapshot(position);
    source.addProperty("stage", "source_before_assembly");
    diagnostics.add(source);
    ship = assembleBody(position, List.of(position), "littletiles");
    require(!ship.isRemoved(), "Native LittleTiles-only assembly was deleted immediately");
    CompoundTag data = ship.getUserDataTag();
    if (data == null) data = new CompoundTag();
    data.putBoolean(MARKER, true);
    ship.setUserDataTag(data);
    require(level.isEmptyBlock(position), "Native assembly left a duplicate source tile");
    report.addProperty("source_tile_mass", mass);
    report.addProperty("tile_block_entity_save_reload", true);
    control(position.offset(24, 0, 0), "vanilla_full_plank", Blocks.OAK_PLANKS.defaultBlockState());
    control(position.offset(48, 0, 0), "vanilla_bottom_slab", Blocks.OAK_SLAB.defaultBlockState());
    BlockPos mixed = position.offset(72, 0, 0);
    level.getChunkAt(mixed);
    require(
        level.isEmptyBlock(mixed) && level.isEmptyBlock(mixed.east()),
        "Mixed control requires air");
    level.setBlockAndUpdate(mixed, state);
    BETiles mixedTiles = (BETiles) level.getBlockEntity(mixed);
    mixedTiles.convertTo(LittleGrid.get(16));
    mixedTiles.updateTiles(
        collection ->
            collection
                .noneStructureTiles()
                .add(
                    new LittleTile(
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        -1,
                        new LittleBox(0, 0, 0, 16, 8, 16))));
    level.setBlockAndUpdate(mixed.east(), Blocks.OAK_PLANKS.defaultBlockState());
    assembleBody(mixed, List.of(mixed, mixed.east()), "littletiles_with_vanilla_carrier");
    writeReport();
    if (Boolean.getBoolean("aeroscapes.regression.constructionNativeMassRebuild")) {
      var pipeline = SubLevelContainer.getContainer(level).physicsSystem().getPipeline();
      for (ServerSubLevel body : diagnosticBodies.values()) {
        body.buildMassTracker();
        body.updateMergedMassData(1);
        pipeline.onStatsChanged(body);
        snapshot(body, "fixture_native_mass_rebuild");
      }
      report.addProperty("fixture_native_mass_rebuild", true);
      writeReport();
    }
    if (!diagnosticsOnly) bodyMasses();
  }

  private void shapeBoundaries(BlockPos first) {
    BlockPos second = first.east(2);
    level.getChunkAt(first);
    level.getChunkAt(second);
    require(level.isEmptyBlock(first) && level.isEmptyBlock(second), "Shape fixtures require air");
    BlockState state = LittleTilesRegistry.BLOCK_TILES.value().defaultBlockState();
    level.setBlockAndUpdate(first, state);
    level.setBlockAndUpdate(second, state);
    BETiles half = (BETiles) level.getBlockEntity(first);
    BETiles other = (BETiles) level.getBlockEntity(second);
    half.convertTo(LittleGrid.get(16));
    other.convertTo(LittleGrid.get(16));
    half.updateTiles(
        collection ->
            collection
                .noneStructureTiles()
                .add(
                    new LittleTile(
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        -1,
                        new LittleBox(0, 0, 0, 16, 8, 16))));
    shape(first, true, false, 0.25);
    shapeCenter(first, new Vector3d(0.5, 0.25, 0.5));
    shape(second, false, false, 0);
    require(
        level.getBlockState(first).equals(level.getBlockState(second)),
        "Different tile payloads must exercise the same native BlockState");
    other.updateTiles(
        collection ->
            collection
                .noneStructureTiles()
                .add(
                    new LittleTile(
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        -1,
                        new LittleBox(0, 0, 0, 16, 16, 16))));
    shape(second, true, true, 0.5);
    shapeCenter(second, new Vector3d(0.5));
    shape(first, true, false, 0.25);
    other.updateTiles(
        collection -> {
          var tiles = collection.noneStructureTiles();
          tiles.clear();
          tiles.add(
              new LittleTile(
                  Blocks.OAK_PLANKS.defaultBlockState(), -1, new LittleBox(0, 0, 0, 16, 8, 16)));
          tiles.add(
              new LittleTile(
                  Blocks.STONE.defaultBlockState(), -1, new LittleBox(0, 8, 0, 16, 16, 16)));
        });
    BlockState otherState = level.getBlockState(second);
    var nativeCollision = otherState.getCollisionShape(level, second, CollisionContext.empty());
    var occupiedUnion =
        nativeCollision.toAabbs().stream().map(Shapes::create).reduce(Shapes.empty(), Shapes::or);
    JsonObject union = new JsonObject();
    union.addProperty("native_box_count", nativeCollision.toAabbs().size());
    union.addProperty("native_custom_shape_full", Block.isShapeFullBlock(nativeCollision));
    union.addProperty("vanilla_occupied_union_full", Block.isShapeFullBlock(occupiedUnion));
    report.add("two_material_cube_occupancy", union);
    double twoMaterialMass =
        0.5
                * PhysicsBlockPropertyHelper.getMass(
                    level, second, Blocks.OAK_PLANKS.defaultBlockState())
            + 0.5
                * PhysicsBlockPropertyHelper.getMass(
                    level, second, Blocks.STONE.defaultBlockState());
    shape(second, true, true, twoMaterialMass);
    double lowerMass =
        0.5
            * PhysicsBlockPropertyHelper.getMass(
                level, second, Blocks.OAK_PLANKS.defaultBlockState());
    double upperMass =
        0.5 * PhysicsBlockPropertyHelper.getMass(level, second, Blocks.STONE.defaultBlockState());
    shapeCenter(
        second, new Vector3d(0.5, (lowerMass * 0.25 + upperMass * 0.75) / twoMaterialMass, 0.5));
    other.updateTiles(
        collection -> {
          var tiles = collection.noneStructureTiles();
          tiles.clear();
          for (LittleBox box :
              List.of(
                  new LittleBox(0, 0, 0, 16, 1, 16), new LittleBox(0, 15, 0, 16, 16, 16),
                  new LittleBox(0, 1, 0, 16, 15, 1), new LittleBox(0, 1, 15, 16, 15, 16),
                  new LittleBox(0, 1, 1, 1, 15, 15), new LittleBox(15, 1, 1, 16, 15, 15)))
            tiles.add(new LittleTile(Blocks.OAK_PLANKS.defaultBlockState(), -1, box));
        });
    shape(second, true, false, 0.5 * (1 - 14.0 * 14 * 14 / (16 * 16 * 16)));
    shape(first, true, false, 0.25);
    other.updateTiles(
        collection -> {
          var tiles = collection.noneStructureTiles();
          tiles.clear();
          tiles.add(
              new LittleTile(
                  Blocks.DANDELION.defaultBlockState(), -1, new LittleBox(0, 0, 0, 16, 16, 16)));
        });
    shape(second, false, false, 0);
    shape(first, true, false, 0.25);
    half.updateTiles(collection -> collection.noneStructureTiles().clear());
    shape(first, false, false, 0);
    level.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
    require(
        !VoxelNeighborhoodState.isSolid(level, first, state),
        "Missing tile BE must not create a solid ghost");
    report.addProperty("native_position_shape_empty_noncolliding_boundaries", true);
  }

  private void shape(BlockPos position, boolean solid, boolean full, double mass) {
    BlockState state = level.getBlockState(position);
    LevelAccelerator accelerator = new LevelAccelerator(level);
    var collision = state.getCollisionShape(level, position, CollisionContext.empty());
    var occupied =
        collision.toAabbs().stream().map(Shapes::create).reduce(Shapes.empty(), Shapes::or);
    require(
        !collision.isEmpty() == solid,
        "Native tile collision differs from fixture geometry at " + position);
    require(
        Block.isShapeFullBlock(occupied) == full,
        "Native tile fullness differs from fixture geometry at " + position);
    require(
        VoxelNeighborhoodState.isSolid(accelerator, position, state) == solid,
        "Sable lost position-dependent LittleTiles solidity at " + position);
    require(
        VoxelNeighborhoodState.isFullBlock(accelerator, position, state) == full,
        "Sable lost position-dependent LittleTiles fullness at " + position);
    double actual =
        MassTracker.build(
                accelerator,
                new BoundingBox3i(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    position.getX(),
                    position.getY(),
                    position.getZ()))
            .getMass();
    require(
        Math.abs(actual - mass) < 1e-9,
        "Native material mass after collision filtering differs at "
            + position
            + ": "
            + actual
            + " != "
            + mass);
  }

  private void bodyMasses() {
    Map<String, Double> expected =
        Map.of(
            "littletiles",
            0.25,
            "vanilla_full_plank",
            0.5,
            "vanilla_bottom_slab",
            0.25,
            "littletiles_with_vanilla_carrier",
            0.75);
    require(
        diagnosticBodies.keySet().equals(expected.keySet()), "Missing native mass comparison body");
    JsonObject masses = new JsonObject();
    for (var entry : expected.entrySet()) {
      ServerSubLevel body = diagnosticBodies.get(entry.getKey());
      require(!body.isRemoved(), "Native body was removed: " + entry.getKey());
      double actual = body.getMassTracker().getMass();
      require(
          Double.isFinite(actual) && Math.abs(actual - entry.getValue()) < 1e-9,
          "Native body material mass differs for "
              + entry.getKey()
              + ": "
              + actual
              + " != "
              + entry.getValue());
      masses.addProperty(entry.getKey(), actual);
      assertCenter(
          body.getMassTracker(),
          body.getPlot().getCenterBlock(),
          localCenter(entry.getKey()),
          entry.getKey());
    }
    ServerSubLevel mixed = diagnosticBodies.get("littletiles_with_vanilla_carrier");
    require(
        level.getBlockEntity(mixed.getPlot().getCenterBlock()) instanceof BETiles tiles
            && tiles.boxesCount() == 1,
        "Mixed carrier lost its actual LittleTiles payload");
    report.add("native_body_masses", masses);
  }

  private void shapeCenter(BlockPos position, Vector3dc expected) {
    var tracker =
        MassTracker.build(
            new LevelAccelerator(level),
            new BoundingBox3i(
                position.getX(),
                position.getY(),
                position.getZ(),
                position.getX(),
                position.getY(),
                position.getZ()));
    assertCenter(tracker, position, expected, "world tile shape");
  }

  private static Vector3d localCenter(String label) {
    return switch (label) {
      case "littletiles", "vanilla_bottom_slab" -> new Vector3d(0.5, 0.25, 0.5);
      case "vanilla_full_plank" -> new Vector3d(0.5);
      case "littletiles_with_vanilla_carrier" -> new Vector3d(7.0 / 6, 5.0 / 12, 0.5);
      default -> throw new IllegalArgumentException("Unknown mass fixture " + label);
    };
  }

  private static void assertCenter(
      MassData data, BlockPos anchor, Vector3dc expected, String label) {
    Vector3d center = new Vector3d(expected).add(anchor.getX(), anchor.getY(), anchor.getZ());
    require(
        data.getCenterOfMass() != null && data.getCenterOfMass().distance(center) < 1e-6,
        "Native material-weighted center differs for "
            + label
            + ": "
            + data.getCenterOfMass()
            + " != "
            + center);
  }

  private void settledBodies() {
    JsonObject heights = new JsonObject();
    for (var entry : diagnosticBodies.entrySet()) {
      ServerSubLevel body = entry.getValue();
      BlockPos origin = body.getPlot().getCenterBlock();
      double bottom =
          body.logicalPose()
              .transformPosition(new Vector3d(origin.getX(), origin.getY(), origin.getZ()))
              .y;
      double expected = body.getUserDataTag().getDouble(PLATFORM_TOP);
      require(
          Math.abs(bottom - expected) < 0.15,
          "Native collider did not settle on the actual stone platform for "
              + entry.getKey()
              + ": "
              + bottom
              + " != "
              + expected);
      heights.addProperty(entry.getKey(), bottom);
    }
    report.add("native_platform_contact_heights", heights);
  }

  private ServerSubLevel assembleBody(BlockPos position, List<BlockPos> blocks, String label) {
    int floor = position.getY() - 18;
    require(floor >= level.getMinBuildHeight(), "Construction platform outside build height");
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        level.setBlockAndUpdate(
            new BlockPos(position.getX() + x, floor, position.getZ() + z),
            Blocks.STONE.defaultBlockState());
    int maximumX = blocks.stream().mapToInt(BlockPos::getX).max().orElseThrow();
    ServerSubLevel body =
        SubLevelAssemblyHelper.assembleBlocks(
            level,
            position,
            blocks,
            new BoundingBox3i(
                position.getX(),
                position.getY(),
                position.getZ(),
                maximumX + 1,
                position.getY() + 1,
                position.getZ() + 1));
    CompoundTag data = body.getUserDataTag();
    if (data == null) data = new CompoundTag();
    data.putString(BODY_LABEL, label);
    data.putDouble(PLATFORM_TOP, floor + 1);
    body.setUserDataTag(data);
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    diagnosticBodies.put(label, body);
    snapshot(body, "immediately_after_assembly");
    if (!diagnosticsOnly
        && !Boolean.getBoolean("aeroscapes.regression.constructionNativeMassRebuild")) {
      Vector3d expected = localCenter(label).add(position.getX(), position.getY(), position.getZ());
      require(
          body.logicalPose().position().distance(expected) < 1e-6,
          "Assembly must read loaded native mass center before placing "
              + label
              + ": "
              + body.logicalPose().position()
              + " != "
              + expected);
    }
    return body;
  }

  private void control(BlockPos position, String label, BlockState state) {
    level.getChunkAt(position);
    require(level.isEmptyBlock(position), "Vanilla control requires air");
    level.setBlockAndUpdate(position, state);
    JsonObject source = blockSnapshot(position);
    source.addProperty("stage", "source_before_assembly");
    source.addProperty("label", label);
    diagnostics.add(source);
    assembleBody(position, List.of(position), label);
  }

  private JsonObject blockSnapshot(BlockPos position) {
    JsonObject result = new JsonObject();
    result.addProperty("position", position.toShortString());
    BlockState state = level.getBlockState(position);
    result.addProperty("block", state.toString());
    measurement(
        result, "native_material_mass", PhysicsBlockPropertyHelper.getMass(level, position, state));
    BlockEntity entity = level.getBlockEntity(position);
    if (entity != null) {
      result.addProperty("block_entity_removed", entity.isRemoved());
      result.addProperty(
          "block_entity_payload", entity.saveWithFullMetadata(level.registryAccess()).toString());
    }
    if (entity instanceof BETiles tiles) {
      result.addProperty("tile_boxes", tiles.boxesCount());
      result.addProperty("tile_grid", tiles.getGrid().count);
      result.addProperty("tile_bounds", tiles.getBlockBB().toString());
    }
    return result;
  }

  private void snapshot(ServerSubLevel body, String stage) {
    JsonObject snapshot = new JsonObject();
    snapshot.addProperty("stage", stage);
    snapshot.addProperty("tick", elapsed);
    snapshot.addProperty("uuid", body.getUniqueId().toString());
    CompoundTag data = body.getUserDataTag();
    snapshot.addProperty("label", data == null ? "unmarked" : data.getString(BODY_LABEL));
    snapshot.addProperty("removed", body.isRemoved());
    snapshot.addProperty("physical_position", body.logicalPose().position().toString());
    measurement(snapshot, "physical_y", body.logicalPose().position().y());
    snapshot.addProperty("plot_bounds", String.valueOf(body.getPlot().getBoundingBox()));
    if (body.getMassTracker() != null)
      measurement(snapshot, "merged_mass", body.getMassTracker().getMass());
    if (body.getSelfMassTracker() != null)
      measurement(snapshot, "self_mass", body.getSelfMassTracker().getMass());
    if (!body.isRemoved()) {
      measurement(
          snapshot,
          "independent_native_bounds_mass",
          MassTracker.build(new LevelAccelerator(level), body.getPlot().getBoundingBox())
              .getMass());
      snapshot.add("center_block", blockSnapshot(body.getPlot().getCenterBlock()));
    }
    diagnostics.add(snapshot);
  }

  private static void measurement(JsonObject sample, String name, double value) {
    if (Double.isFinite(value)) sample.addProperty(name, value);
    else sample.addProperty(name, Double.toString(value));
  }

  private void observeRemovals() {
    SubLevelContainer.getContainer(level)
        .addObserver(
            new SubLevelObserver() {
              @Override
              public void onSubLevelRemoved(SubLevel removed, SubLevelRemovalReason reason) {
                if (finished || !(removed instanceof ServerSubLevel body)) return;
                CompoundTag data = body.getUserDataTag();
                if (data == null || !data.contains(BODY_LABEL)) return;
                snapshot(body, "native_removal_" + reason.name());
                JsonObject event = new JsonObject();
                event.addProperty("stage", "native_removal_stack");
                event.addProperty("label", data.getString(BODY_LABEL));
                event.addProperty("reason", reason.name());
                JsonArray frames = new JsonArray();
                StackWalker.getInstance()
                    .walk(stack -> stack.limit(24).map(StackWalker.StackFrame::toString).toList())
                    .forEach(frames::add);
                event.add("stack", frames);
                diagnostics.add(event);
                LogUtils.getLogger()
                    .info(
                        "Construction diagnostic body {} removed at fixture tick {} physicalY={} reason={}",
                        data.getString(BODY_LABEL),
                        elapsed,
                        body.logicalPose().position().y(),
                        reason);
                writeReport();
              }
            });
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    elapsed++;
    if (diagnosticsOnly) {
      if (elapsed <= 2 || elapsed == 5 || elapsed == 20 || elapsed == 100) {
        diagnosticBodies.values().forEach(body -> snapshot(body, "server_tick_post"));
        writeReport();
      }
      if (elapsed >= 100) {
        report.addProperty("diagnostic_ticks_completed", elapsed);
        writeReport();
        finished = true;
        LogUtils.getLogger().info("Construction diagnostics completed");
      }
      return;
    }
    if (reload && (ship == null || diagnosticBodies.size() != 4)) {
      for (ServerSubLevel candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
        CompoundTag data = candidate.getUserDataTag();
        if (data != null && data.contains(BODY_LABEL))
          diagnosticBodies.put(data.getString(BODY_LABEL), candidate);
        if (data != null && data.getBoolean(MARKER)) {
          require(
              ship == null || ship == candidate,
              "More than one construction fixture body is loaded");
          ship = candidate;
        }
      }
      if (ship == null || diagnosticBodies.size() != 4) {
        require(elapsed < 200, "Saved force-loaded construction bodies did not reload");
        return;
      }
    }
    require(
        ship != null && !ship.isRemoved(), "LittleTiles-only ship was deleted during native ticks");
    bodyMasses();
    BlockEntity entity = level.getBlockEntity(ship.getPlot().getCenterBlock());
    require(
        entity instanceof BETiles tiles && tiles.boxesCount() == 1,
        "Assembled LittleTiles-only body lost its native block payload");
    double shipMass = ship.getMassTracker().getMass();
    require(
        shipMass > 0 && Double.isFinite(shipMass),
        "Assembled LittleTiles-only body lost its finite positive mass");
    retainedTicks++;
    if (retainedTicks < 100) return;
    settledBodies();
    report.addProperty("littletiles_only_ship_uuid", ship.getUniqueId().toString());
    report.addProperty("littletiles_only_ship_native_ticks", retainedTicks);
    report.addProperty("littletiles_only_ship_mass", shipMass);
    report.addProperty("native_ship_restart_reload", reload);
    require(level.getServer().saveEverything(false, true, true), "Native world save failed");
    writeReport();
    finished = true;
    LogUtils.getLogger()
        .info(reload ? "Construction reload regression passed" : "Construction regression passed");
  }

  private void writeReport() {
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write construction fixture result", failure);
    }
  }

  private static void result(ItemStack result, String id, int count) {
    require(
        result.is(
                BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("littletiles", id)))
            && result.getCount() == count,
        "Wrong native result for " + id);
  }

  private static void premade(ItemStack result, String structure, int count) {
    result(result, "premade", count);
    require(
        ItemPremadeStructure.getPremadeId(result).equals(structure),
        "Native recipe did not attach functional structure " + structure);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
