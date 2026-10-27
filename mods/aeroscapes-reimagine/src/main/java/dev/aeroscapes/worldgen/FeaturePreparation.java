package dev.aeroscapes.worldgen;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.mixin.FeatureIndexAccess;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Prepare the current native feature index after biome modifiers and Lithostitched injection. */
public final class FeaturePreparation {
  private FeaturePreparation() {}

  public static void prepare(ServerStartedEvent event) {
    long started = System.nanoTime();
    Set<ChunkGenerator> prepared = Collections.newSetFromMap(new IdentityHashMap<>());
    for (var level : event.getServer().getAllLevels()) {
      ChunkGenerator generator = level.getChunkSource().getGenerator();
      if (prepared.add(generator)) {
        // TerraBlender 4.1.0.8 cancels validate(); query the same current native supplier.
        ((FeatureIndexAccess) generator).aeroscapesReimagine$featuresPerStep().get();
      }
    }
    LogUtils.getLogger()
        .info(
            "Prepared native feature indexes: generators={} elapsed-ms={}",
            prepared.size(),
            (System.nanoTime() - started) / 1_000_000);
  }
}
