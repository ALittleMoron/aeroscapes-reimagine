package dev.aeroscapes.qualityoflife;

import com.chaosthedude.explorerscompass.worker.StructureSearchWorker;
import java.util.List;

public interface CompassManagerBridge {
  List<StructureSearchWorker<?>> aeroscapes$workers();
}
