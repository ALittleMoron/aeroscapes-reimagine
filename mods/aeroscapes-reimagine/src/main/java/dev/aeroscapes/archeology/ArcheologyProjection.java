package dev.aeroscapes.archeology;

import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;

/** Exposes the native projection to the existing template support check. */
public interface ArcheologyProjection {
  Optional<Heightmap.Types> aeroscapes$heightmap();
}
