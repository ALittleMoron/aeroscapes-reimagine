package dev.aeroscapes.regression;

import dev.aeroscapes.sky.WhaleDecorationClearance;
import dev.aeroscapes.sky.WhaleSpawnClearance;
import dev.aeroscapes.sky.WhaleVariety;
import dev.dacite.skywhales.corpse.WhaleCorpseSpawner;
import dev.dacite.skywhales.corpse.WhaleCorpseTracker;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import xerca.xercapaint.entity.Entities;
import xerca.xercapaint.entity.EntityCanvas;
import xerca.xercapaint.entity.EntityEasel;

/** Checks native recipes, painted-data round trips, and the physical carcass boundary. */
public final class SkyArtRegression {
  private SkyArtRegression() {}

  public static void verify(ServerLevel level) throws ReflectiveOperationException {
    verifyRecipes(level);
    verifyPaintPersistence(level);
    verifySpawnClearance(level);
    verifyCarcass(level);
  }

  public static void verifySpawnClearance(ServerLevel level) {
    var center =
        new BlockPos(
            level.getSharedSpawnPos().getX(),
            level.getMaxBuildHeight() - 24,
            level.getSharedSpawnPos().getZ());
    var whale = SWEntities.SKY_WHALE.get().create(level);
    require(whale != null, "Spawn clearance uses the actual registered multipart entity");
    whale.setPos(Vec3.atLowerCornerOf(center));
    try {
      for (float yaw : new float[] {0, 90}) {
        whale.yBodyRot = yaw;
        require(
            WhaleSpawnClearance.clear(level, whale),
            "A fully loaded empty multipart volume permits natural placement");
        var forward = Vec3.directionFromRotation(0, yaw);
        var cliff = BlockPos.containing(whale.position().add(forward.scale(12)));
        var previous = level.getBlockState(cliff);
        try {
          level.setBlockAndUpdate(cliff, Blocks.STONE.defaultBlockState());
          require(
              level.noCollision(whale.getBoundingBox()),
              "Cliff fixture lies outside the native seven-block central spawn box");
          require(
              !WhaleSpawnClearance.clear(level, whale),
              "A cliff intersecting the actual nose rejects placement at each tested yaw");
        } finally {
          level.setBlockAndUpdate(cliff, previous);
        }
      }
      whale.yBodyRot = 0;
      var outsideBody = center.east(14);
      var previous = level.getBlockState(outsideBody);
      try {
        level.setBlockAndUpdate(outsideBody, Blocks.STONE.defaultBlockState());
        require(
            WhaleSpawnClearance.clear(level, whale),
            "An unrelated block inside the broad body envelope but outside every actual part remains allowed");
      } finally {
        level.setBlockAndUpdate(outsideBody, previous);
      }
      for (WhaleVariety variety : WhaleVariety.values()) {
        ((dev.aeroscapes.sky.WhaleVariantAccess) whale).aeroscapes$variety(variety);
        for (float yaw : new float[] {0, 37, 90, -125}) {
          whale.yBodyRot = yaw;
          require(
              WhaleSpawnClearance.clear(level, whale, variety),
              "Every decorated habitat fits loaded open sky at each tested yaw");
          var culling = whale.getBoundingBoxForCulling();
          for (var bounds : WhaleDecorationClearance.at(variety, whale.position(), yaw)) {
            require(
                culling.minX <= bounds.minX
                    && culling.minY <= bounds.minY
                    && culling.minZ <= bounds.minZ
                    && culling.maxX >= bounds.maxX
                    && culling.maxY >= bounds.maxY
                    && culling.maxZ >= bounds.maxZ,
                "Native culling includes the entire animated habitat at every tested yaw");
          }
        }
      }
      whale.yBodyRot = 0;
      BlockPos decoration = null;
      for (var bounds : WhaleDecorationClearance.at(WhaleVariety.REEF, whale.position(), 0)) {
        for (BlockPos candidate :
            BlockPos.betweenClosed(
                BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(
                    Math.nextDown(bounds.maxX),
                    Math.nextDown(bounds.maxY),
                    Math.nextDown(bounds.maxZ)))) {
          if (Math.abs(candidate.getX() - center.getX()) >= 4
              && candidate.getZ() - center.getZ() >= 9) {
            decoration = candidate.immutable();
            break;
          }
        }
        if (decoration != null) break;
      }
      require(decoration != null, "Reef fixture includes habitat beyond the native front parts");
      var previousDecoration = level.getBlockState(decoration);
      try {
        level.setBlockAndUpdate(decoration, Blocks.STONE.defaultBlockState());
        require(
            level.noCollision(whale.getBoundingBox()),
            "Habitat obstacle lies outside the central native collision body");
        require(
            !WhaleSpawnClearance.clear(level, whale, WhaleVariety.REEF),
            "An island intersecting habitat outside the native front parts rejects placement");
      } finally {
        level.setBlockAndUpdate(decoration, previousDecoration);
      }
      var unloaded = new BlockPos(1_000_000, center.getY(), 1_000_000);
      require(
          !level.isLoaded(unloaded), "Unloaded fixture starts outside generated/ticking chunks");
      whale.setPos(Vec3.atLowerCornerOf(unloaded));
      require(
          !WhaleSpawnClearance.clear(level, whale) && !level.isLoaded(unloaded),
          "Multipart clearance rejects unloaded volume without requesting a chunk");
    } finally {
      whale.discard();
    }
  }

  private static void verifyRecipes(ServerLevel level) {
    int checked = 0;
    for (var holder : level.getRecipeManager().getRecipes()) {
      if (!holder.id().getNamespace().equals("xercapaint")
          || !(holder.value() instanceof ShapedRecipe recipe)) continue;
      var input =
          CraftingInput.of(
              recipe.getWidth(),
              recipe.getHeight(),
              recipe.getIngredients().stream()
                  .map(
                      ingredient ->
                          ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy())
                  .toList());
      require(
          recipe.matches(input, level),
          "Native art recipe matches actual ingredients: " + holder.id());
      require(
          !recipe.assemble(input, level.registryAccess()).isEmpty(),
          "Art recipe produces its registered result");
      require(
          recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
          "Art ingredients are consumed exactly once");
      checked++;
    }
    require(checked == 14, "All fourteen native shaped canvas, palette and easel recipes load");

    var paintItems = xerca.xercapaint.item.Items.ITEM_PALETTE;
    var palette = new ItemStack(paintItems);
    var paintInput =
        CraftingInput.of(
            2, 2, List.of(palette, new ItemStack(Items.RED_DYE), ItemStack.EMPTY, ItemStack.EMPTY));
    var fill =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, paintInput, level).orElseThrow();
    require(
        fill.id().equals(ResourceLocation.parse("xercapaint:palette_filling")),
        "Native palette dye recipe remains reachable");
    var filled = fill.value().assemble(paintInput, level.registryAccess());
    require(filled.is(paintItems), "Palette filling preserves the art tool");
    var colors = filled.get(xerca.xercapaint.item.Items.PALETTE_BASIC_COLORS);
    require(
        colors != null && colors.value()[1] == 1,
        "Vanilla red dye fills its actual native color slot");
    require(
        fill.value().getRemainingItems(paintInput).stream().allMatch(ItemStack::isEmpty),
        "Filling consumes the original palette and dye without duplicate tools");
    var saved = (CompoundTag) filled.saveOptional(level.registryAccess());
    require(
        ItemStack.isSameItemSameComponents(
            filled, ItemStack.parseOptional(level.registryAccess(), saved)),
        "Filled palette data survives item serialization");

    var signed = painting(xerca.xercapaint.item.Items.ITEM_CANVAS);
    var copyInput =
        CraftingInput.of(
            2,
            2,
            List.of(
                signed,
                new ItemStack(xerca.xercapaint.item.Items.ITEM_CANVAS),
                ItemStack.EMPTY,
                ItemStack.EMPTY));
    var cloning =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, copyInput, level).orElseThrow();
    require(
        cloning.id().equals(ResourceLocation.parse("xercapaint:canvas_cloning")),
        "Signed-canvas cloning remains reachable");
    var copied = cloning.value().assemble(copyInput, level.registryAccess());
    require(
        copied.getOrDefault(xerca.xercapaint.item.Items.CANVAS_GENERATION, 0) == 2,
        "Native copy generation advances");
    require(
        copied
            .get(xerca.xercapaint.item.Items.CANVAS_PIXELS)
            .equals(signed.get(xerca.xercapaint.item.Items.CANVAS_PIXELS)),
        "Cloning retains the actual artwork");
    var remainder = cloning.value().getRemainingItems(copyInput);
    var remainderComponents = new java.util.HashSet<>(signed.getComponents().keySet());
    remainderComponents.addAll(remainder.getFirst().getComponents().keySet());
    var changedComponents =
        remainderComponents.stream()
            .filter(
                component ->
                    !java.util.Objects.equals(
                        signed.get(component), remainder.getFirst().get(component)))
            .map(component -> BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component))
            .toList();
    require(
        ItemStack.isSameItemSameComponents(remainder.get(0), signed)
            && remainder.stream().filter(stack -> !stack.isEmpty()).count() == 1,
        "One signed original returns while one blank canvas is consumed: slots="
            + remainder.stream()
                .map(
                    stack ->
                        stack.getCount() + "x" + BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .toList()
            + ", originalCount="
            + signed.getCount()
            + ", originalGeneration="
            + signed.get(xerca.xercapaint.item.Items.CANVAS_GENERATION)
            + ", inputSize="
            + copyInput.size()
            + ", sameComponents="
            + remainder.getFirst().getComponents().equals(signed.getComponents())
            + ", changedComponents="
            + changedComponents);

    for (String food : List.of("raw_whale_meat", "cooked_whale_meat")) {
      var item =
          BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("skywhales", food));
      require(
          new ItemStack(item)
              .is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:nutrients/proteins"))),
          "Whale meat belongs to the existing protein nutrition group");
    }
    var dustInput =
        CraftingInput.of(
            1,
            1,
            List.of(
                new ItemStack(
                    BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse("skywhales:whale_bone_dust")))));
    var dust =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, dustInput, level).orElseThrow();
    require(
        dust.value()
            .assemble(dustInput, level.registryAccess())
            .is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("aeronautics:end_stone_powder"))),
        "Whale dust retains the native alternative lift-material route");
  }

  private static ItemStack painting(xerca.xercapaint.item.ItemCanvas item) {
    var canvas = new ItemStack(item);
    canvas.set(xerca.xercapaint.item.Items.CANVAS_ID, UUID.randomUUID().toString());
    canvas.set(xerca.xercapaint.item.Items.CANVAS_VERSION, 7);
    canvas.set(xerca.xercapaint.item.Items.CANVAS_TITLE, "Aerial garden / Небесный сад");
    canvas.set(xerca.xercapaint.item.Items.CANVAS_AUTHOR, "SkyArtRegression");
    canvas.set(xerca.xercapaint.item.Items.CANVAS_GENERATION, 1);
    canvas.set(
        xerca.xercapaint.item.Items.CANVAS_PIXELS,
        java.util.Collections.nCopies(item.getWidth() * item.getHeight(), 0xff70a0c0));
    canvas.set(xerca.xercapaint.item.Items.CANVAS_SIDES_ACTIVE, true);
    canvas.set(
        xerca.xercapaint.item.Items.CANVAS_SIDE_PIXELS,
        java.util.Collections.nCopies(2 * item.getWidth() + 2 * item.getHeight(), 0xff906030));
    xerca.xercapaint.item.ItemCanvas.updateStackSize(canvas);
    return canvas;
  }

  private static void verifyPaintPersistence(ServerLevel level) {
    for (var item :
        List.of(
            xerca.xercapaint.item.Items.ITEM_CANVAS,
            xerca.xercapaint.item.Items.ITEM_CANVAS_LARGE,
            xerca.xercapaint.item.Items.ITEM_CANVAS_LONG,
            xerca.xercapaint.item.Items.ITEM_CANVAS_TALL,
            xerca.xercapaint.item.Items.ITEM_CANVAS_GLASS,
            xerca.xercapaint.item.Items.ITEM_CANVAS_GLASS_LARGE,
            xerca.xercapaint.item.Items.ITEM_CANVAS_GLASS_LONG,
            xerca.xercapaint.item.Items.ITEM_CANVAS_GLASS_TALL)) {
      var original = painting(item);
      var saved = (CompoundTag) original.saveOptional(level.registryAccess());
      var restored = ItemStack.parseOptional(level.registryAccess(), saved);
      require(
          ItemStack.isSameItemSameComponents(original, restored),
          "All eight canvas types retain pixels, sides, signature and material as items");
      var easel = new EntityEasel(level);
      easel.setItem(original, false);
      var easelData = new CompoundTag();
      easel.addAdditionalSaveData(easelData);
      var restoredEasel = new EntityEasel(level);
      restoredEasel.readAdditionalSaveData(easelData);
      require(
          ItemStack.isSameItemSameComponents(original, restoredEasel.getItem()),
          "Easel serialization retains the whole painting");

      String identity = original.get(xerca.xercapaint.item.Items.CANVAS_ID);
      var placed =
          new EntityCanvas(
              level,
              original,
              level.getSharedSpawnPos().above(32),
              Direction.NORTH,
              item.getCanvasType(),
              0);
      var placedData = new CompoundTag();
      placed.addAdditionalSaveData(placedData);
      var firstPicture = EntityCanvas.PICTURES.remove(identity);
      var loaded = new EntityCanvas(Entities.CANVAS, level);
      loaded.readAdditionalSaveData(placedData);
      try {
        var picture = EntityCanvas.PICTURES.get(identity);
        require(
            picture != null
                && Arrays.equals(picture.pixels(), firstPicture.pixels())
                && Arrays.equals(picture.sidePixels(), firstPicture.sidePixels()),
            "Placed painting restores native pixel storage from entity NBT without a warm picture cache");
        require(
            loaded.isGlass() == item.isGlass()
                && loaded.getWidth() == item.getWidth()
                && loaded.getHeight() == item.getHeight(),
            "Placed canvas retains material and dimensions");
      } finally {
        EntityCanvas.PICTURES.remove(identity);
        easel.discard();
        restoredEasel.discard();
        placed.discard();
        loaded.discard();
      }
    }
  }

  private static void verifyCarcass(ServerLevel level) throws ReflectiveOperationException {
    var container = SubLevelContainer.getContainer(level);
    require(
        container != null && container.physicsSystem() != null, "Native Sable physics is active");
    var previousIds =
        container.getAllSubLevels().stream().map(ServerSubLevel::getUniqueId).toList();
    BlockPos center = level.getSharedSpawnPos().above(48);
    BlockPos protectedPosition = center.above(3);
    var previousBlock = level.getBlockState(protectedPosition);
    var previousEntity = level.getBlockEntity(protectedPosition);
    var previousData =
        previousEntity == null ? null : previousEntity.saveWithFullMetadata(level.registryAccess());
    ServerSubLevel corpse = null;
    try {
      level.setBlockAndUpdate(protectedPosition, Blocks.CHEST.defaultBlockState());
      var chest = (ChestBlockEntity) level.getBlockEntity(protectedPosition);
      chest.setItem(0, new ItemStack(Items.DIAMOND, 13));
      var whale = SWEntities.SKY_WHALE.get().create(level);
      require(whale != null, "Pinned whale entity is registered");
      whale.setPos(Vec3.atLowerCornerOf(center).subtract(0, whale.getBbHeight() * 0.5, 0));
      whale.deathTime = 49;
      var deathTick = SkyWhale.class.getDeclaredMethod("tickDeath");
      deathTick.setAccessible(true);
      deathTick.invoke(whale);
      require(
          level.getBlockState(protectedPosition).is(Blocks.CHEST)
              && chest.getItem(0).is(Items.DIAMOND)
              && chest.getItem(0).getCount() == 13,
          "A real native whale death preserves occupied blocks and inventory at its carcass origin");
      var spawned =
          container.getAllSubLevels().stream()
              .filter(
                  sublevel ->
                      !sublevel.isRemoved() && !previousIds.contains(sublevel.getUniqueId()))
              .toList();
      require(spawned.size() == 1, "Native death leaves one physical body and no staging plot");
      corpse = spawned.getFirst();
      require(
          WhaleCorpseSpawner.CORPSE_NAME.equals(corpse.getName()),
          "Native carcass identity and decay behavior are retained");
      require(
          corpse.logicalPose().position().distance(center.getX(), center.getY(), center.getZ())
              < 40,
          "Reserved storage transforms the corpse back near the real death position");
      UUID identity = corpse.getUniqueId();
      var tracker = WhaleCorpseTracker.get(level);
      long created = tracker.firstSeen(identity, -1);
      require(created >= 0, "Native corpse age is tracked");
      var saved = SubLevelSerializer.toData(corpse, List.of());
      container.removeSubLevel(corpse, SubLevelRemovalReason.UNLOADED);
      container.processSubLevelRemovals();
      require(tracker.firstSeen(identity, -2) == created, "Unloading retains native corpse age");
      corpse = SubLevelSerializer.fullyLoad(level, saved);
      require(
          corpse.getUniqueId().equals(identity)
              && WhaleCorpseSpawner.CORPSE_NAME.equals(corpse.getName()),
          "Native Sable serializer reloads the same physical carcass");
      container.removeSubLevel(corpse, SubLevelRemovalReason.REMOVED);
      corpse = null;
      var ages = tracker.save(new CompoundTag(), level.registryAccess()).getList("corpses", 10);
      for (int index = 0; index < ages.size(); index++)
        require(
            !ages.getCompound(index).getUUID("id").equals(identity),
            "Permanent carcass removal also removes saved decay bookkeeping");
    } finally {
      if (corpse != null && !corpse.isRemoved())
        container.removeSubLevel(corpse, SubLevelRemovalReason.REMOVED);
      level.setBlockAndUpdate(protectedPosition, previousBlock);
      if (previousData != null && level.getBlockEntity(protectedPosition) != null)
        level
            .getBlockEntity(protectedPosition)
            .loadWithComponents(previousData, level.registryAccess());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
