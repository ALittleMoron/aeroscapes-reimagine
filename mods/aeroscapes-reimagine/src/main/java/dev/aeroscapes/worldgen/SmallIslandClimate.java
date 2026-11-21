package dev.aeroscapes.worldgen;

import dev.aeroscapes.mixin.EffectiveBiomeParametersAccess;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import terrablender.worldgen.IExtendedParameterList;

/**
 * Resolves a shared climate sample without borrowing or changing the neighboring lookup history.
 */
final class SmallIslandClimate {
  private static final Field NATIVE_TREE = nativeTree();
  private static final List<Field> SEARCH_CACHES =
      Arrays.stream(NATIVE_TREE.getType().getDeclaredFields())
          .filter(field -> ThreadLocal.class.equals(field.getType()))
          .filter(
              field ->
                  switch (field.getName()) {
                    case "lastResult", "previousUltimateNode", "previousPenultimateNode" -> true;
                    default -> false;
                  })
          .peek(field -> field.setAccessible(true))
          .toList();

  private SmallIslandClimate() {}

  static Holder<Biome> sample(BiomeSource source, BlockPos anchor, Climate.Sampler sampler) {
    int x = anchor.getX() >> 2;
    int y = anchor.getY() >> 2;
    int z = anchor.getZ() >> 2;
    if (source instanceof MultiNoiseBiomeSource multi) {
      var parameters = ((EffectiveBiomeParametersAccess) multi).aeroscapes$parameters();
      return withoutHistory(parameters, x, y, z, () -> source.getNoiseBiome(x, y, z, sampler));
    }
    return source.getNoiseBiome(x, y, z, sampler);
  }

  static <T> T withoutHistory(
      Climate.ParameterList<?> parameters, int x, int y, int z, Supplier<T> query) {
    var restore = new ArrayList<Runnable>();
    try {
      Object nativeTree = NATIVE_TREE.get(parameters);
      clear(nativeTree, restore);
      if (parameters instanceof IExtendedParameterList<?> extended && extended.isInitialized()) {
        Object regional = extended.getTree(extended.getUniqueness(x, y, z));
        Object fallback = extended.getTree(0);
        if (regional != null && regional != nativeTree) clear(regional, restore);
        if (fallback != null && fallback != regional && fallback != nativeTree)
          clear(fallback, restore);
      }
      return query.get();
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException("Cannot isolate the pinned climate lookup caches", failure);
    } finally {
      for (int index = restore.size() - 1; index >= 0; index--) restore.get(index).run();
    }
  }

  private static void clear(Object tree, List<Runnable> restore) throws IllegalAccessException {
    for (Field field : SEARCH_CACHES) restore.add(clear((ThreadLocal<?>) field.get(tree)));
  }

  private static <T> Runnable clear(ThreadLocal<T> cache) {
    T previous = cache.get();
    cache.remove();
    return () -> {
      if (previous == null) cache.remove();
      else cache.set(previous);
    };
  }

  private static Field nativeTree() {
    try {
      Field field = Climate.ParameterList.class.getDeclaredField("index");
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException failure) {
      throw new IllegalStateException("Missing the pinned Minecraft climate search tree", failure);
    }
  }
}
