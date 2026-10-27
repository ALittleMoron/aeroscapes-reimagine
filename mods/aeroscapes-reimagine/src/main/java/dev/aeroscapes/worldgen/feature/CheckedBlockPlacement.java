// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public record CheckedBlockPlacement(List<Pair<BlockPredicate, BlockStateProvider>> blockPlacement) {
  public static final Codec<CheckedBlockPlacement> CODEC =
      Codec.pair(
              BlockPredicate.CODEC.fieldOf("check").codec(),
              BlockStateProvider.CODEC.fieldOf("blockStateProvider").codec())
          .listOf()
          .xmap(CheckedBlockPlacement::new, CheckedBlockPlacement::blockPlacement);
}
