package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.worker.SearchWorkerManager;
import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import dev.aeroscapes.qualityoflife.CompassManagerBridge;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SearchWorkerManager.class, remap = false)
public interface CompassManagerAccess extends CompassManagerBridge {
  @Override
  @Accessor("workers")
  List<StructureSearchWorker<?>> aeroscapes$workers();
}
