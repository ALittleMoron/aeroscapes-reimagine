package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.spawn.WhaleSpawner;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs the same loaded flight path with the previous and candidate pack on copies of one save. */
final class FaunaFlightBenchmark {
  private static final Vec3 START = new Vec3(72.5, 268, 72.5);
  private static final List<Jelly> COLONY =
      List.of(
          new Jelly("fringe", 1.5F),
          new Jelly("nettle", 1.5F),
          new Jelly("lantern", 1.5F),
          new Jelly("mane", 4),
          new Jelly("fringe", 7),
          new Jelly("nettle", 13));
  private static final float COLONY_HEADING = -25;
  private final ServerLevel level;
  private final boolean populated;
  private final long[] elapsed = new long[240];
  private TestShipPlayerSession session;
  private int ticks;
  private int whalePlacements;
  private long tickStart;

  private FaunaFlightBenchmark(ServerLevel level, boolean populated) {
    this.level = level;
    this.populated = populated;
  }

  static void start(ServerLevel level, boolean populated) {
    var run = new FaunaFlightBenchmark(level, populated);
    NeoForge.EVENT_BUS.addListener(run::pre);
    NeoForge.EVENT_BUS.addListener(run::post);
  }

  private void pre(ServerTickEvent.Pre event) {
    tickStart = System.nanoTime();
  }

  private void post(ServerTickEvent.Post event) {
    if (++ticks == 5) {
      if (!level
          .getBlockState(new net.minecraft.core.BlockPos(72, 296, 72))
          .is(net.minecraft.world.level.block.Blocks.CHISELED_COPPER)) {
        throw new IllegalStateException("Missing 0.6.6 flight benchmark landmark");
      }
      session =
          TestShipPlayerSession.create(
              level,
              UUID.fromString("082d1c47-a681-4e5c-be09-67fbc19b3dee"),
              "FaunaFlight",
              START,
              null);
      session.player.setNoGravity(true);
      for (int x = -1; x <= 9; x++) {
        for (int z = -1; z <= 9; z++) level.getChunk(x, z);
      }
      if (populated) {
        spawn("sky_ray", 0);
        for (int i = 1; i <= COLONY.size(); i++) spawn("cloud_jelly", i);
      }
    }
    if (session == null) return;
    double dx = 12 * Math.sin(ticks / 26.0);
    session.player.setPos(START.x + dx, START.y, START.z);
    session.player.setDeltaMovement(Vec3.ZERO);
    if (ticks >= 25 && ticks <= 32) {
      var clock = level.getServer().getWorldData().overworldData();
      long time = level.getGameTime();
      try {
        clock.setGameTime(0);
        level.getRandom().setSeed(4147L * ticks);
        for (int i = 0; i < 8; i++) {
          WhaleSpawner.onLevelTick(new LevelTickEvent.Post(() -> true, level));
          var whales =
              level.getEntitiesOfClass(SkyWhale.class, new AABB(START, START).inflate(128));
          if (!whales.isEmpty()) whalePlacements++;
          whales.forEach(Entity::discard);
        }
      } finally {
        clock.setGameTime(time);
      }
    }
    if (ticks >= 201) {
      int index = ticks - 201;
      elapsed[index] = System.nanoTime() - tickStart;
      if (index + 1 == elapsed.length) {
        Arrays.sort(elapsed);
        LogUtils.getLogger()
            .info(
                "Fauna flight benchmark complete: populated={}, samples={}, medianMs={}, p95Ms={},"
                    + " whaleEncounters={}/64",
                populated,
                elapsed.length,
                elapsed[elapsed.length / 2] / 1e6,
                elapsed[elapsed.length * 95 / 100] / 1e6,
                whalePlacements);
        session.close();
        session = null;
      }
    }
  }

  private void spawn(String path, int index) {
    var type =
        BuiltInRegistries.ENTITY_TYPE
            .getOptional(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path))
            .orElseThrow();
    var mob = (Mob) type.create(level);
    if (mob == null) throw new IllegalStateException("Could not instantiate " + path);
    mob.getRandom().setSeed(74001L + index);
    if (path.equals("cloud_jelly")) {
      var specimen = COLONY.get(index - 1);
      var data = mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());
      data.putFloat("BellDiameter", specimen.diameter());
      data.putString("JellyForm", specimen.form());
      data.putFloat("DriftHeading", COLONY_HEADING);
      mob.load(data);
      var loaded = mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());
      if (!loaded.getString("JellyForm").equals(specimen.form())
          || loaded.getFloat("BellDiameter") != specimen.diameter()
          || loaded.getFloat("DriftHeading") != COLONY_HEADING)
        throw new IllegalStateException("Unstable benchmark specimen " + specimen);
    }
    mob.setPersistenceRequired();
    float yaw = index == 0 ? 40 : COLONY_HEADING;
    mob.moveTo(START.x + 3 * index, START.y, START.z + index, yaw, 0);
    mob.setYBodyRot(yaw);
    mob.setYHeadRot(yaw);
    if (!level.addFreshEntity(mob)) throw new IllegalStateException("Could not add " + path);
  }

  private record Jelly(String form, float diameter) {}
}
