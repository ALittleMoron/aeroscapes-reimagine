package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import dev.aeroscapes.worldgen.BlackstoneOres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real ore writes, loaded loot/recipes and naturally generated Mantle material controls. */
public final class BlackstoneOreRegression {
  private static final BlockPos ORIGIN = new BlockPos(8, 310, 8);

  private record Placement(Set<Long> positions, long randomTail) {}

  private BlackstoneOreRegression() {}

  public static void verify(ServerLevel level, Path output) {
    require(level.getSeed() == 1, "Blackstone ore regression requires host seed 1");
    boolean reload = Boolean.getBoolean("aeroscapes.regression.blackstoneOresReload");
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    report.addProperty("reload", reload);
    report.addProperty("variants", BlackstoneOres.MATERIALS.size());
    if (!reload) {
      Placement nativeBlob =
          blob(
              level,
              Blocks.DEEPSLATE.defaultBlockState(),
              Blocks.BLACKSTONE.defaultBlockState(),
              Blocks.BLACKSTONE.defaultBlockState());
      for (String material : BlackstoneOres.MATERIALS) {
        BlockState nativeOre = nativeOre(material);
        BlockState variant = BlackstoneOres.adapt(Blocks.BLACKSTONE.defaultBlockState(), nativeOre);
        require(variant != nativeOre, "Missing blackstone variant " + material);
        require(
            BlackstoneOres.adapt(Blocks.DEEPSLATE.defaultBlockState(), nativeOre) == nativeOre,
            "Ordinary deepslate ore changed " + material);
        require(
            BlackstoneOres.adapt(Blocks.STONE.defaultBlockState(), nativeOre) == nativeOre,
            "Ordinary stone ore changed " + material);
        require(
            nativeBlob.equals(
                blob(level, nativeOre, Blocks.BLACKSTONE.defaultBlockState(), variant)),
            "Blackstone blob changed its footprint or random stream " + material);
        BlockState stoneOre =
            BuiltInRegistries.BLOCK
                .get(
                    ResourceLocation.fromNamespaceAndPath(
                        BlackstoneOres.sourceNamespace(material), material + "_ore"))
                .defaultBlockState();
        require(
            nativeBlob.equals(
                blob(level, stoneOre, Blocks.BLACKSTONE.defaultBlockState(), stoneOre)),
            "Blackstone blob changed a stone-hosted ore or random stream " + material);
        Placement nativePlacement =
            place(level, Blocks.DEEPSLATE.defaultBlockState(), nativeOre, nativeOre);
        Placement adapted = place(level, Blocks.BLACKSTONE.defaultBlockState(), nativeOre, variant);
        require(
            nativePlacement.equals(adapted), "Ore shape or random consumption changed " + material);
        require(
            adapted.equals(place(level, Blocks.BLACKSTONE.defaultBlockState(), stoneOre, variant)),
            "OreFeature lost its plain-source blackstone write or random stream " + material);
        loot(level, nativeOre, variant);
        tools(level, nativeOre, variant);
        recipes(level, material, variant);
        experience(level, nativeOre, variant);
        report.addProperty(material + "_placed_blocks", adapted.positions().size());
        if (material.equals("palladium")) {
          report.addProperty(
              "palladium_native_requires_correct_tool", nativeOre.requiresCorrectToolForDrops());
          report.addProperty(
              "palladium_blackstone_requires_correct_tool", variant.requiresCorrectToolForDrops());
        }
        LogUtils.getLogger().info("Blackstone ore material checks passed: {}", material);
      }
      blob(
          level,
          Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
          Blocks.GRANITE.defaultBlockState(),
          Blocks.DEEPSLATE_IRON_ORE.defaultBlockState());
      report.addProperty("plain_source_blob_controls", BlackstoneOres.MATERIALS.size());
      report.addProperty("plain_source_ore_write_controls", BlackstoneOres.MATERIALS.size());
      redstone(level);
    }
    sentinels(level, reload);
    report.addProperty("natural_mantle_blackstone_ores", naturalMantle(level));
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write blackstone ore report " + output, failure);
    }
    LogUtils.getLogger()
        .info("Blackstone ore regression passed: reload={} report={}", reload, output);
  }

  private static BlockState nativeOre(String material) {
    return BuiltInRegistries.BLOCK
        .get(
            ResourceLocation.fromNamespaceAndPath(
                BlackstoneOres.sourceNamespace(material), "deepslate_" + material + "_ore"))
        .defaultBlockState();
  }

  private static Placement place(
      ServerLevel level, BlockState base, BlockState ore, BlockState expectedState) {
    var section = fill(level, base);
    var random = RandomSource.create(734015L);
    var feature = new DirectOreFeature();
    require(feature.attempt(level, random, ore), "Native ore attempt failed");
    long tail = random.nextLong();
    Set<Long> result = new HashSet<>();
    for (int x = 0; x < 16; x++)
      for (int y = 0; y < 16; y++)
        for (int z = 0; z < 16; z++)
          if (!section.getBlockState(x, y, z).is(base.getBlock())) {
            require(
                section.getBlockState(x, y, z).equals(expectedState),
                "Native ore write used the wrong host material");
            result.add(new BlockPos(x, y, z).asLong());
          }
    require(!result.isEmpty(), "Ore attempt wrote no blocks");
    level.getChunk(0, 0).setUnsaved(true);
    return new Placement(Set.copyOf(result), tail);
  }

  private static LevelChunkSection fill(ServerLevel level, BlockState base) {
    var chunk = level.getChunk(0, 0);
    var section = chunk.getSection(chunk.getSectionIndex(ORIGIN.getY()));
    for (int x = 0; x < 16; x++)
      for (int y = 0; y < 16; y++)
        for (int z = 0; z < 16; z++) section.setBlockState(x, y, z, base);
    chunk.setUnsaved(true);
    return section;
  }

  private static Placement blob(
      ServerLevel level, BlockState existing, BlockState replacement, BlockState expectedInside) {
    var section = fill(level, Blocks.DEEPSLATE.defaultBlockState());
    BlockPos inside = ORIGIN.offset(1, 0, 0);
    BlockPos outside = ORIGIN.offset(6, 0, 0);
    level.setBlockAndUpdate(inside, existing);
    level.setBlockAndUpdate(outside, existing);
    var random = RandomSource.create(85175L);
    var configuration =
        new ReplaceSphereConfiguration(
            Blocks.DEEPSLATE.defaultBlockState(), replacement, UniformInt.of(2, 3));
    var feature = new ReplaceBlobsFeature(ReplaceSphereConfiguration.CODEC);
    require(
        feature.place(
            new FeaturePlaceContext<>(
                Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                random,
                ORIGIN,
                configuration)),
        "Native material blob failed");
    require(
        level.getBlockState(inside).equals(expectedInside),
        "Material blob changed its ore scope: existing="
            + existing
            + " expected="
            + expectedInside
            + " actual="
            + level.getBlockState(inside));
    require(
        level.getBlockState(outside).equals(existing),
        "Material blob changed an ore outside its footprint");
    Set<Long> changed = new HashSet<>();
    for (int x = 0; x < 16; x++)
      for (int y = 0; y < 16; y++)
        for (int z = 0; z < 16; z++) {
          if (x == outside.getX() && y == (outside.getY() & 15) && z == outside.getZ()) continue;
          if (!section.getBlockState(x, y, z).is(Blocks.DEEPSLATE))
            changed.add(new BlockPos(x, y, z).asLong());
        }
    return new Placement(Set.copyOf(changed), random.nextLong());
  }

  private static void loot(ServerLevel level, BlockState nativeOre, BlockState variant) {
    var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    for (int mode = 0; mode < 3; mode++) {
      ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
      if (mode == 1) tool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
      if (mode == 2) tool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
      for (int seed = 1; seed <= 32; seed++) {
        List<ItemStack> expected = drops(level, nativeOre, tool, seed);
        List<ItemStack> actual = drops(level, variant, tool, seed);
        require(expected.size() == actual.size(), "Ore loot stack count differs");
        for (int index = 0; index < expected.size(); index++) {
          ItemStack original = expected.get(index);
          ItemStack changed = actual.get(index);
          require(original.getCount() == changed.getCount(), "Ore loot amount differs");
          require(
              changed.is(
                  original.is(nativeOre.getBlock().asItem())
                      ? variant.getBlock().asItem()
                      : original.getItem()),
              "Ore loot item differs");
        }
      }
    }
  }

  private static List<ItemStack> drops(
      ServerLevel level, BlockState state, ItemStack tool, long seed) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(ORIGIN))
            .withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .create(LootContextParamSets.BLOCK);
    return level
        .getServer()
        .reloadableRegistries()
        .getLootTable(state.getBlock().getLootTable())
        .getRandomItems(params, seed);
  }

  private static void tools(ServerLevel level, BlockState original, BlockState variant) {
    String source = BuiltInRegistries.BLOCK.getKey(original.getBlock()).toString();
    String adapted = BuiltInRegistries.BLOCK.getKey(variant.getBlock()).toString();
    for (var tag :
        List.of(
            BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.NEEDS_STONE_TOOL,
            BlockTags.NEEDS_IRON_TOOL,
            BlockTags.NEEDS_DIAMOND_TOOL))
      require(
          original.is(tag) == variant.is(tag),
          "Blackstone ore tag differs: source="
              + source
              + " variant="
              + adapted
              + " tag="
              + tag.location()
              + " source_member="
              + original.is(tag)
              + " variant_member="
              + variant.is(tag));
    require(variant.is(BlockTags.MINEABLE_WITH_PICKAXE), "Blackstone ore lacks its mining tag");
    require(
        original.requiresCorrectToolForDrops() == variant.requiresCorrectToolForDrops(),
        "Blackstone ore harvest requirement differs");
    require(
        original.getDestroySpeed(level, ORIGIN) == variant.getDestroySpeed(level, ORIGIN),
        "Blackstone ore hardness differs");
    for (var item :
        List.of(
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
      ItemStack tool = new ItemStack(item);
      require(
          tool.isCorrectToolForDrops(original) == tool.isCorrectToolForDrops(variant),
          "Blackstone ore tool tier differs: source="
              + source
              + " variant="
              + adapted
              + " tool="
              + BuiltInRegistries.ITEM.getKey(item)
              + " source_accepted="
              + tool.isCorrectToolForDrops(original)
              + " variant_accepted="
              + tool.isCorrectToolForDrops(variant));
    }
  }

  private static void experience(ServerLevel level, BlockState original, BlockState variant) {
    ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
    for (int seed = 1; seed <= 32; seed++) {
      level.getRandom().setSeed(seed);
      int expected = original.getBlock().getExpDrop(original, level, ORIGIN, null, null, tool);
      long expectedTail = level.getRandom().nextLong();
      level.getRandom().setSeed(seed);
      int actual = variant.getBlock().getExpDrop(variant, level, ORIGIN, null, null, tool);
      require(expected == actual, "Blackstone ore experience differs");
      require(
          expectedTail == level.getRandom().nextLong(),
          "Blackstone ore experience consumed extra random draws");
    }
  }

  private static void recipes(ServerLevel level, String material, BlockState variant) {
    String output =
        switch (material) {
          case "copper", "iron", "gold", "zinc", "palladium" -> material + "_ingot";
          case "lapis" -> "lapis_lazuli";
          default -> material;
        };
    for (String process : List.of("smelting", "blasting")) {
      var recipe =
          level
              .getRecipeManager()
              .byKey(
                  ResourceLocation.parse(
                      "aeroscapes_reimagine:"
                          + output
                          + "_from_"
                          + process
                          + "_blackstone_"
                          + material
                          + "_ore"))
              .orElseThrow();
      if (!(recipe.value()
          instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking))
        throw new IllegalStateException("Blackstone ore cooking recipe has the wrong type");
      var input = new SingleRecipeInput(new ItemStack(variant.getBlock()));
      require(cooking.matches(input, level), "Blackstone ore cooking recipe does not match");
      require(
          BuiltInRegistries.ITEM
              .getKey(cooking.assemble(input, level.registryAccess()).getItem())
              .equals(
                  ResourceLocation.fromNamespaceAndPath(
                      BlackstoneOres.sourceNamespace(material), output)),
          "Blackstone ore cooking result differs");
    }
    if (material.equals("palladium")) return;
    var recipe =
        level
            .getRecipeManager()
            .byKey(
                ResourceLocation.parse(
                    "aeroscapes_reimagine:crushing/blackstone_" + material + "_ore"))
            .orElseThrow();
    var nativeRecipe =
        level
            .getRecipeManager()
            .byKey(ResourceLocation.parse("create:crushing/deepslate_" + material + "_ore"))
            .orElseThrow();
    if (!(recipe.value() instanceof CrushingRecipe crushing)
        || !(nativeRecipe.value() instanceof CrushingRecipe original))
      throw new IllegalStateException("Blackstone ore crushing recipe has the wrong type");
    require(
        crushing.matches(new SingleRecipeInput(new ItemStack(variant.getBlock())), level),
        "Blackstone ore crushing recipe does not match");
    require(
        crushing.getProcessingDuration() == original.getProcessingDuration(),
        "Blackstone ore crushing time changed");
    var expectedResults = original.getRollableResults();
    var actualResults = crushing.getRollableResults();
    require(
        expectedResults.size() == actualResults.size(),
        "Blackstone ore crushing result count changed");
    for (int index = 0; index < expectedResults.size(); index++) {
      var expected = expectedResults.get(index);
      var actual = actualResults.get(index);
      require(expected.getChance() == actual.getChance(), "Blackstone ore crushing chance changed");
      require(
          expected.getStack().getCount() == actual.getStack().getCount(),
          "Blackstone ore crushing yield changed");
      require(
          actual
              .getStack()
              .is(
                  expected.getStack().is(Blocks.COBBLED_DEEPSLATE.asItem())
                      ? Blocks.BLACKSTONE.asItem()
                      : expected.getStack().getItem()),
          "Blackstone ore crushing result changed");
    }
  }

  private static void redstone(ServerLevel level) {
    BlockState nativeLit =
        Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState().setValue(RedStoneOreBlock.LIT, true);
    BlockState lit = BlackstoneOres.adapt(Blocks.BLACKSTONE.defaultBlockState(), nativeLit);
    require(lit.getValue(RedStoneOreBlock.LIT), "Blackstone redstone lost its lit state");
    BlockState unlit = lit.setValue(RedStoneOreBlock.LIT, false);
    level.setBlockAndUpdate(ORIGIN, unlit);
    unlit.attack(level, ORIGIN, FakePlayerFactory.getMinecraft(level));
    require(
        level.getBlockState(ORIGIN).getValue(RedStoneOreBlock.LIT),
        "Blackstone redstone did not light");
    level.getBlockState(ORIGIN).randomTick(level, ORIGIN, RandomSource.create(1));
    require(
        !level.getBlockState(ORIGIN).getValue(RedStoneOreBlock.LIT),
        "Blackstone redstone did not extinguish");
  }

  private static void sentinels(ServerLevel level, boolean reload) {
    for (int index = 0; index < BlackstoneOres.MATERIALS.size(); index++) {
      String material = BlackstoneOres.MATERIALS.get(index);
      BlockState state =
          BlackstoneOres.adapt(Blocks.BLACKSTONE.defaultBlockState(), nativeOre(material));
      BlockPos position = new BlockPos(3 + index, 330, 8);
      if (reload)
        require(level.getBlockState(position).equals(state), "Saved ore changed " + material);
      else level.setBlockAndUpdate(position, state);
    }
  }

  private static int naturalMantle(ServerLevel level) {
    int variants = 0;
    int wrong = 0;
    for (int chunkX = -321; chunkX <= -319; chunkX++)
      for (int chunkZ = -273; chunkZ <= -271; chunkZ++) {
        var chunk = level.getChunk(chunkX, chunkZ);
        for (BlockPos position :
            BlockPos.betweenClosed(
                chunkX * 16, -96, chunkZ * 16, chunkX * 16 + 15, 16, chunkZ * 16 + 15)) {
          if (!level
              .getBiome(position)
              .unwrapKey()
              .orElseThrow()
              .location()
              .toString()
              .equals("terralith:cave/mantle_caves")) continue;
          ResourceLocation id =
              BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(position).getBlock());
          if (id.getNamespace().equals("aeroscapes_reimagine")
              && id.getPath().startsWith("blackstone_")
              && id.getPath().endsWith("_ore")) variants++;
          if (id.getPath().startsWith("deepslate_") && id.getPath().endsWith("_ore")) wrong++;
        }
      }
    require(variants > 0, "No naturally generated blackstone ore in Mantle fixture");
    // Mantle's blob boundaries may retain natural deepslate; those ores remain valid controls.
    LogUtils.getLogger()
        .info("Mantle ore fixture: blackstone={} remaining_deepslate={}", variants, wrong);
    return variants;
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }

  private static final class DirectOreFeature extends OreFeature {
    private DirectOreFeature() {
      super(OreConfiguration.CODEC);
    }

    private boolean attempt(ServerLevel level, RandomSource random, BlockState ore) {
      var configuration =
          new OreConfiguration(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES), ore, 16);
      return doPlace(level, random, configuration, 6, 10, 8, 8, 310, 310, 2, 304, 2, 12, 12);
    }
  }
}
