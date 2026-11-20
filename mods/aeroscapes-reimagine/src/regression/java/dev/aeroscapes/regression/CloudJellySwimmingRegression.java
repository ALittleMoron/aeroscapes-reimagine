package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class CloudJellySwimmingRegression {
  private CloudJellySwimmingRegression() {}

  static void verify(ServerLevel level, Vec3 observer) {
    var clock = level.getServer().getWorldData().overworldData();
    long originalTime = level.getGameTime();
    var jelly = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
    jelly.setUUID(new UUID(0, 0x4000));
    jelly.setForm(CloudJellyAnatomy.Form.NETTLE);
    jelly.initSize(3);
    jelly.setDriftHeading(0);
    jelly.moveTo(observer.x, observer.y + 12, observer.z);
    if (!level.addFreshEntity(jelly))
      throw new IllegalStateException("Could not add swimming jelly");
    int strokes = 0;
    int glides = 0;
    double strokeAcceleration = 0;
    double glideAcceleration = 0;
    double glideTravel = 0;
    Vec3 previousTravel = Vec3.ZERO;
    try {
      for (int tick = 0; tick < 160; tick++) {
        clock.setGameTime(tick);
        var frame = jelly.swimFrame(level.getGameTime());
        Vec3 start = jelly.position();
        level.tickNonPassenger(jelly);
        Vec3 travel = jelly.position().subtract(start);
        if (tick > 5 && frame.basis.y.horizontalDistance() > 0.9) {
          Vec3 forward = new Vec3(frame.basis.y.x, 0, frame.basis.y.z).normalize();
          double acceleration = travel.subtract(previousTravel).dot(forward);
          if (frame.thrust > 0.017) {
            strokes++;
            strokeAcceleration += acceleration;
          } else if (frame.phase() > 0.68) {
            glides++;
            glideAcceleration += acceleration;
            glideTravel += travel.horizontalDistance();
          }
        }
        previousTravel = travel;
      }
    } finally {
      jelly.discard();
      clock.setGameTime(originalTime);
    }
    if (strokes < 8
        || glides < 15
        || glideTravel / glides < 0.02
        || strokeAcceleration / strokes < glideAcceleration / glides + 0.003) {
      throw new IllegalStateException(
          "Side-swimming jelly lost its propulsive stroke or its open glide: "
              + strokes
              + " strokes / "
              + strokeAcceleration
              + ", "
              + glides
              + " glides / "
              + glideAcceleration
              + ", "
              + glideTravel
              + " blocks gliding");
    }
    LogUtils.getLogger()
        .info(
            "Jelly lateral swim verified across 160 ticks: {} powered samples, {} open glide samples",
            strokes,
            glides);
  }
}
