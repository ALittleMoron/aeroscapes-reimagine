package dev.aeroscapes.mixin.testship;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.testship.TestShips;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import java.util.HashSet;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SwivelBearingBlockEntity.class, remap = false)
public abstract class SteeringOwnershipMixin {
  @WrapMethod(method = "assemble")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only bodies newly created by a tagged vessel become owned.
  private void aeroscapes$ownChild(Operation<Void> original) {
    var bearing = (SwivelBearingBlockEntity) (Object) this;
    if (bearing.getLevel() == null
        || bearing.getLevel().isClientSide
        || !(Sable.HELPER.getContaining(bearing.getLevel(), bearing.getBlockPos())
            instanceof ServerSubLevel parent)
        || !TestShips.isFixture(parent)) {
      original.call();
      return;
    }
    var container = SubLevelContainer.getContainer(bearing.getLevel());
    var before = new HashSet<UUID>();
    for (var body : container.getAllSubLevels()) before.add(body.getUniqueId());
    original.call();
    UUID childId = bearing.getSubLevelID();
    if (bearing.isAssembled()
        && childId != null
        && !before.contains(childId)
        && container.getSubLevel(childId) instanceof ServerSubLevel child) {
      TestShips.ownChild(parent, child);
    }
  }
}
