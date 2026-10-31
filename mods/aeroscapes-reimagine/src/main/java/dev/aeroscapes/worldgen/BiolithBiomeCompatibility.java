package dev.aeroscapes.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

/** Keeps the optional pinned Biolith 3.0.10 parameter cache aligned with the owned delegate. */
final class BiolithBiomeCompatibility {
  private static final Api API = loadApi();

  private record Api(Method dimension, Method setDimension, Method entries, Field cachedEntries) {}

  private BiolithBiomeCompatibility() {}

  static void initialize(BiomeSource source) {
    if (API == null || !(source instanceof MultiNoiseBiomeSource)) return;
    try {
      if (BuiltinDimensionTypes.OVERWORLD.equals(API.dimension().invoke(source))) return;
      if (API.entries().invoke(source) != null)
        throw new IllegalStateException(
            "Biolith dimension must be assigned before the island delegate's first parameter lookup");
      API.setDimension().invoke(source, BuiltinDimensionTypes.OVERWORLD);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(
          "Cannot initialize the island delegate for Biolith 3.0.10", failure);
    }
  }

  @SuppressWarnings(
      "unchecked") // Retain the initialized instance after validating its biome holders.
  static Climate.ParameterList<Holder<Biome>> parameters(
      MultiNoiseBiomeSource source, Climate.ParameterList<Holder<Biome>> raw) {
    if (API == null) return raw;
    try {
      Object effective = API.entries().invoke(source);
      if (effective == null) {
        source.possibleBiomes();
        effective = API.entries().invoke(source);
      }
      if (!(effective instanceof Climate.ParameterList<?> parameters))
        throw new IllegalStateException(
            "Biolith did not expose the island delegate's effective parameter list");
      for (var pair : parameters.values())
        if (!(pair.getSecond() instanceof Holder<?> holder) || !(holder.value() instanceof Biome))
          throw new IllegalStateException(
              "Biolith effective parameter list contains a non-biome value");
      return (Climate.ParameterList<Holder<Biome>>) parameters;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(
          "Cannot read Biolith 3.0.10 effective biome parameters", failure);
    }
  }

  static void bindGenerationCache(
      MultiNoiseBiomeSource source, Climate.ParameterList<Holder<Biome>> parameters) {
    if (API == null) return;
    try {
      // Biolith's public dimension setter does not replace its shallow-cloned parameter cache.
      API.cachedEntries().set(source, parameters);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException(
          "Cannot bind Biolith 3.0.10 generation parameter cache", failure);
    }
  }

  private static Api loadApi() {
    Class<?> carrier;
    try {
      carrier = Class.forName("com.terraformersmc.biolith.impl.biome.InterfaceBiomeSource");
    } catch (ClassNotFoundException absent) {
      return null;
    }
    try {
      Field cache = MultiNoiseBiomeSource.class.getDeclaredField("biolith$biomeEntries");
      if (cache.getType() != Climate.ParameterList.class)
        throw new IllegalStateException("Unexpected Biolith 3.0.10 parameter cache type");
      cache.setAccessible(true);
      return new Api(
          carrier.getMethod("biolith$getDimensionType"),
          carrier.getMethod("biolith$setDimensionType", ResourceKey.class),
          carrier.getMethod("biolith$getBiomeEntries"),
          cache);
    } catch (ReflectiveOperationException
        | InaccessibleObjectException
        | SecurityException failure) {
      throw new IllegalStateException(
          "Cannot access the pinned Biolith 3.0.10 biome-source contract", failure);
    }
  }
}
