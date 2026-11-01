package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.worker.RandomSpreadSearchWorker;
import dev.aeroscapes.qualityoflife.CompassRandomSpreadBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RandomSpreadSearchWorker.class, remap = false)
public interface CompassRandomSpreadAccess extends CompassRandomSpreadBridge {
  @Override
  @Accessor("length")
  int aeroscapes$ring();

  @Override
  @Accessor("spacing")
  int aeroscapes$spacing();

  @Override
  @Accessor("length")
  void aeroscapes$ring(int ring);

  @Override
  @Accessor("x")
  void aeroscapes$cellX(int x);

  @Override
  @Accessor("z")
  void aeroscapes$cellZ(int z);
}
