// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record PillarFeatureConfig(
    CheckedBlockPlacement checkedBlockPlacement,
    IntProvider height,
    IntProvider radius,
    IntProvider vineLength,
    IntProvider heightOffset,
    FloatProvider noiseFrequency,
    FloatProvider minRadiusScale,
    SimpleWeightedRandomList<DistanceTestType> distanceTestType,
    Optional<Integer> maxGroundDrop)
    implements FeatureConfiguration {
  public static final Codec<PillarFeatureConfig> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      CheckedBlockPlacement.CODEC
                          .fieldOf("block_placement")
                          .forGetter(PillarFeatureConfig::checkedBlockPlacement),
                      IntProvider.CODEC.fieldOf("height").forGetter(PillarFeatureConfig::height),
                      IntProvider.CODEC.fieldOf("radius").forGetter(PillarFeatureConfig::radius),
                      IntProvider.CODEC
                          .fieldOf("vine_length")
                          .forGetter(PillarFeatureConfig::vineLength),
                      IntProvider.CODEC
                          .fieldOf("height_offset")
                          .forGetter(PillarFeatureConfig::heightOffset),
                      FloatProvider.CODEC
                          .fieldOf("noise_frequency")
                          .forGetter(PillarFeatureConfig::noiseFrequency),
                      FloatProvider.CODEC
                          .fieldOf("min_radius_scale")
                          .forGetter(PillarFeatureConfig::minRadiusScale),
                      SimpleWeightedRandomList.wrappedCodec(DistanceTestType.CODEC)
                          .fieldOf("distance_test_type")
                          .forGetter(PillarFeatureConfig::distanceTestType),
                      Codec.intRange(0, Integer.MAX_VALUE)
                          .optionalFieldOf("max_ground_drop")
                          .forGetter(PillarFeatureConfig::maxGroundDrop))
                  .apply(instance, PillarFeatureConfig::new));
}
