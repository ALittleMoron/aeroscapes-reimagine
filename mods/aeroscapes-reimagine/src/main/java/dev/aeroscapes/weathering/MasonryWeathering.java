package dev.aeroscapes.weathering;

import com.google.common.collect.ImmutableBiMap;
import com.ordana.immersive_weathering.blocks.cracked.CrackSpreader;
import com.ordana.immersive_weathering.blocks.cracked.Crackable;
import com.ordana.immersive_weathering.blocks.mossy.MossSpreader;
import com.ordana.immersive_weathering.blocks.mossy.Mossable;
import com.ordana.immersive_weathering.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MasonryWeathering {
  private static final ImmutableBiMap<ResourceKey<Block>, ResourceKey<Block>> MOSS = mossPairs();
  private static final ImmutableBiMap<ResourceKey<Block>, ResourceKey<Block>> CRACKS = crackPairs();

  private MasonryWeathering() {}

  private static ImmutableBiMap<ResourceKey<Block>, ResourceKey<Block>> mossPairs() {
    var pairs = ImmutableBiMap.<ResourceKey<Block>, ResourceKey<Block>>builder();
    for (String shape : new String[] {"", "_slab", "_stairs", "_wall"}) {
      mossPair(pairs, "natures_spirit", "cobbled_travertine" + shape);
      mossPair(
          pairs,
          "natures_spirit",
          shape.isEmpty() ? "travertine_bricks" : "travertine_brick" + shape);
      mossPair(pairs, "quark", "cobblestone_bricks" + shape);
    }
    mossPair(pairs, "quark", "cobblestone_bricks_vertical_slab");
    mossPair(pairs, "quark", "cobblestone_vertical_slab");
    mossPair(pairs, "quark", "stone_brick_vertical_slab");
    return pairs.buildOrThrow();
  }

  private static void mossPair(
      ImmutableBiMap.Builder<ResourceKey<Block>, ResourceKey<Block>> pairs,
      String namespace,
      String name) {
    pairs.put(key(namespace, name), key(namespace, "mossy_" + name));
  }

  private static ImmutableBiMap<ResourceKey<Block>, ResourceKey<Block>> crackPairs() {
    var pairs = ImmutableBiMap.<ResourceKey<Block>, ResourceKey<Block>>builder();
    for (String material : new String[] {"travertine", "chert"}) {
      for (String shape : new String[] {"_bricks", "_tiles"}) {
        String name = material + shape;
        pairs.put(key("natures_spirit", name), key("natures_spirit", "cracked_" + name));
      }
    }
    return pairs.buildOrThrow();
  }

  private static ResourceKey<Block> key(String namespace, String name) {
    return ResourceKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, name));
  }

  public static void extendMoss(ImmutableBiMap.Builder<Block, Block> pairs) {
    resolve(MOSS, pairs);
  }

  public static void extendCracks(ImmutableBiMap.Builder<Block, Block> pairs) {
    resolve(CRACKS, pairs);
  }

  private static void resolve(
      ImmutableBiMap<ResourceKey<Block>, ResourceKey<Block>> catalog,
      ImmutableBiMap.Builder<Block, Block> pairs) {
    catalog.forEach(
        (base, weathered) ->
            BuiltInRegistries.BLOCK
                .getOptional(base.location())
                .ifPresent(
                    original ->
                        BuiltInRegistries.BLOCK
                            .getOptional(weathered.location())
                            .ifPresent(result -> pairs.put(original, result))));
  }

  public static boolean randomTicks(BlockState state) {
    var id = BuiltInRegistries.BLOCK.getResourceKey(state.getBlock()).orElse(null);
    return MOSS.containsKey(id) || CRACKS.containsKey(id);
  }

  public static boolean cracks(BlockState state) {
    return CRACKS.containsKey(
        BuiltInRegistries.BLOCK.getResourceKey(state.getBlock()).orElse(null));
  }

  public static boolean repairs(BlockState state) {
    return CRACKS.containsValue(
        BuiltInRegistries.BLOCK.getResourceKey(state.getBlock()).orElse(null));
  }

  public static void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    var id = BuiltInRegistries.BLOCK.getResourceKey(state.getBlock()).orElse(null);
    boolean mossTarget = MOSS.containsKey(id);
    boolean crackTarget = CRACKS.containsKey(id);
    if ((!mossTarget && !crackTarget)
        || random.nextFloat() >= 0.1f
        || level.getBlockState(pos) != state) return;
    if (mossTarget
        && CommonConfigs.MOSS_SPREADING_ENABLED.get()
        && MossSpreader.INSTANCE.getWantedWeatheringState(true, pos, level)) {
      BlockState mossy = Mossable.getMossyBlock(state);
      if (mossy != state) {
        level.setBlockAndUpdate(pos, mossy);
        return;
      }
    }
    if (crackTarget
        && CommonConfigs.CRACK_SPREADING_ENABLED.get()
        && CrackSpreader.INSTANCE.getWantedWeatheringState(true, pos, level)) {
      BlockState cracked = Crackable.getCrackedBlock(state);
      if (cracked != state) level.setBlockAndUpdate(pos, cracked);
    }
  }
}
