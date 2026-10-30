package dev.aeroscapes.testship;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Retains the old schema-1 platform safely until its owner removes it; native airships are ignored.
 */
public final class TestShipController implements SubLevelObserver {
  public static final String MARKER = "aeroscapes_reimagine:test_ship";
  private final ServerSubLevelContainer container;
  private final List<ServerSubLevel> pending = new ArrayList<>();
  private final Map<UUID, Fixture> fixtures = new HashMap<>();
  private final TestShipBoarding boarding;

  public TestShipController(ServerSubLevelContainer container) {
    this.container = container;
    this.boarding = new TestShipBoarding(container);
  }

  @Override
  public void onSubLevelAdded(SubLevel ship) {
    if (ship instanceof ServerSubLevel server) pending.add(server);
  }

  @Override
  public void onSubLevelRemoved(SubLevel ship, SubLevelRemovalReason reason) {
    pending.remove(ship);
    Fixture fixture = fixtures.remove(ship.getUniqueId());
    if (fixture != null) fixture.release();
    if (reason == SubLevelRemovalReason.REMOVED && ship instanceof ServerSubLevel server)
      TestShips.removed(server);
  }

  @Override
  public void tick(SubLevelContainer ignored) {
    boarding.tick();
    for (ServerSubLevel ship : List.copyOf(pending)) {
      pending.remove(ship);
      CompoundTag data = ship.getUserDataTag();
      if (ship.isRemoved() || data == null || !data.contains(MARKER, CompoundTag.TAG_COMPOUND))
        continue;
      CompoundTag marker = data.getCompound(MARKER);
      if (marker.getInt("schema") == 2) continue;
      if (marker.getInt("schema") != 1
          || !marker.hasUUID("owner")
          || !marker.hasUUID("ship")
          || !marker.getUUID("ship").equals(ship.getUniqueId())) {
        LogUtils.getLogger().warn("Invalid weather test ship metadata for {}", ship.getUniqueId());
        continue;
      }
      // A restored fixture holds its actual saved pose, rather than resuming old motion.
      try {
        adopt(
            ship,
            ship.logicalPose().transformPosition(localAnchor(ship)),
            ship.logicalPose().orientation());
      } catch (IllegalStateException failure) {
        LogUtils.getLogger()
            .error("Cannot restore weather test ship hold {}", ship.getUniqueId(), failure);
      }
    }
    for (Fixture fixture : List.copyOf(fixtures.values())) {
      try {
        fixture.ensureHold();
      } catch (IllegalStateException failure) {
        fixture.release();
        fixtures.remove(fixture.ship.getUniqueId());
        LogUtils.getLogger()
            .error("Weather test ship hold unavailable {}", fixture.ship.getUniqueId(), failure);
      }
    }
  }

  private Fixture adopt(ServerSubLevel ship, Vector3dc goal, Quaterniondc orientation) {
    pending.remove(ship);
    Fixture fixture = new Fixture(ship, new Vector3d(goal), new Quaterniond(orientation));
    Fixture previous = fixtures.put(ship.getUniqueId(), fixture);
    if (previous != null) previous.release();
    try {
      fixture.ensureHold();
    } catch (IllegalStateException failure) {
      fixtures.remove(ship.getUniqueId());
      fixture.release();
      throw failure;
    }
    return fixture;
  }

  public Fixture get(UUID id) {
    return fixtures.get(id);
  }

  public TestShipBoarding boarding() {
    return boarding;
  }

  public static Vector3d localAnchor(ServerSubLevel ship) {
    BlockPos anchor = ship.getPlot().getCenterBlock();
    return new Vector3d(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
  }

  public final class Fixture {
    private final ServerSubLevel ship;
    private final Vector3d goal;
    private final Quaterniond orientation;
    private FreeConstraintHandle handle;

    private Fixture(ServerSubLevel ship, Vector3d goal, Quaterniond orientation) {
      this.ship = ship;
      this.goal = goal;
      this.orientation = orientation;
    }

    private void release() {
      if (handle != null && handle.isValid()) handle.remove();
      handle = null;
    }

    private void ensureHold() {
      if (ship.isRemoved() || handle != null && handle.isValid()) return;
      var pipeline = container.physicsSystem().getPipeline();
      handle =
          pipeline.addConstraint(
              null, ship, new FreeConstraintConfiguration(goal, localAnchor(ship), orientation));
      if (handle == null)
        throw new IllegalStateException("Cannot hold weather test ship " + ship.getUniqueId());
      double mass = ship.getMassTracker().getMass();
      if (!Double.isFinite(mass) || mass <= 0) {
        release();
        throw new IllegalStateException("Invalid weather test ship mass");
      }
      for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR)
        handle.setMotor(axis, 0, mass * 144, mass * 24, false, 0);
      for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR)
        handle.setMotor(axis, 0, mass * 200, mass * 60, false, 0);
      pipeline.wakeUp(ship);
    }
  }
}
