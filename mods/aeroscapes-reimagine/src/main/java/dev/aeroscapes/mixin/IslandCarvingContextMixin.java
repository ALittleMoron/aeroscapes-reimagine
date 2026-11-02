package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandCarvingContext;
import dev.aeroscapes.worldgen.IslandCaveHosts;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CarvingContext.class)
public abstract class IslandCarvingContextMixin implements IslandCarvingContext {
  @Unique private IslandCaveHosts.Range aeroscapes$layer;

  @Override
  public IslandCaveHosts.Range aeroscapes$layer() {
    return aeroscapes$layer;
  }

  @Override
  public void aeroscapes$layer(IslandCaveHosts.Range layer) {
    aeroscapes$layer = layer;
  }
}
