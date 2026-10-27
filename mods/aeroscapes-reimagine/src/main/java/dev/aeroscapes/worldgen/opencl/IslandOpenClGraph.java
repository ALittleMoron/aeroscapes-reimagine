package dev.aeroscapes.worldgen.opencl;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.c2me.IslandTerrainNode;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Reject graphs that would sample uploaded island geometry outside the GPU block batch. */
public final class IslandOpenClGraph {
  private IslandOpenClGraph() {}

  public static void validateFlatCache(AstNode root) {
    ArrayDeque<AstNode> pending = new ArrayDeque<>();
    Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    pending.add(root);
    while (!pending.isEmpty()) {
      AstNode node = pending.removeLast();
      if (!visited.add(node)) continue;
      if (node instanceof IslandTerrainNode)
        throw new IllegalStateException(
            "Island OpenCL geometry cannot run in a null-context flat-cache prefill");
      Collections.addAll(pending, node.getChildren());
    }
  }

  public static void validate(NoiseRouter router) {
    for (DensityFunction function :
        List.of(
            router.barrierNoise(),
            router.fluidLevelFloodednessNoise(),
            router.fluidLevelSpreadNoise(),
            router.lavaNoise(),
            router.temperature(),
            router.vegetation(),
            router.continents(),
            router.erosion(),
            router.depth(),
            router.ridges(),
            router.initialDensityWithoutJaggedness(),
            router.veinToggle(),
            router.veinRidged(),
            router.veinGap()))
      function.mapAll(
          child -> {
            if (child instanceof IslandTerrain)
              throw new IllegalStateException(
                  "Island OpenCL supports owned geometry only in final density; "
                      + "aquifer, biome and preliminary-surface graphs need an independent adapter");
            return child;
          });
  }
}
