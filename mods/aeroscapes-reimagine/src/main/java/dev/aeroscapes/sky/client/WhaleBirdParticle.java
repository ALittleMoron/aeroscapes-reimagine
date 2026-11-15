package dev.aeroscapes.sky.client;

import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One swift circling a whale. It reuses Atmospheric Fauna's two flight frames, mirrored toward the
 * direction of travel as that mod does, and steers smoothly toward a moving orbit point.
 */
final class WhaleBirdParticle extends TextureSheetParticle {
  private static final int FADE_TICKS = 40;
  private final SkyWhale whale;
  private final TextureAtlasSprite[] frames;
  private final double radius;
  private final double height;
  private final double angularSpeed;
  private final int flapPeriod;
  private double angle;
  private boolean facingRight;
  private int fading = -1;

  WhaleBirdParticle(
      ClientLevel level, SkyWhale whale, TextureAtlasSprite[] frames, int index, long seed) {
    super(level, whale.getX(), whale.getY() + 8.0, whale.getZ());
    this.whale = whale;
    this.frames = frames;
    var random = net.minecraft.util.RandomSource.create(seed * 31L + index);
    radius = 12.0 + random.nextDouble() * 16.0;
    height = 4.0 + random.nextDouble() * 10.0;
    angularSpeed = (random.nextBoolean() ? 1.0 : -1.0) * (0.018 + random.nextDouble() * 0.02);
    angle = random.nextDouble() * Math.PI * 2.0;
    flapPeriod = 2 + random.nextInt(2);
    Vec3 start = orbitPoint(0.0F);
    setPos(start.x, start.y, start.z);
    xo = x;
    yo = y;
    zo = z;
    quadSize = 0.35F;
    lifetime = Integer.MAX_VALUE;
    hasPhysics = false;
    gravity = 0.0F;
    alpha = 0.0F;
    setSprite(frames[0]);
  }

  @Override
  public void tick() {
    xo = x;
    yo = y;
    zo = z;
    age++;
    var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    boolean present =
        whale.isAlive()
            && !whale.isRemoved()
            && whale.level() == level
            && whale.distanceToSqr(camera) < (WhaleBirds.RANGE + 24.0) * (WhaleBirds.RANGE + 24.0);
    if (!present && fading < 0) fading = FADE_TICKS;
    // A whale that is back in view keeps its birds.
    if (present && fading >= 0) fading = -1;
    if (fading >= 0 && --fading <= 0) {
      remove();
      return;
    }
    angle += angularSpeed;
    Vec3 target = present ? orbitPoint(age) : new Vec3(x, y + 0.1, z);
    xd = Mth.lerp(0.08, xd, (target.x - x) * 0.12);
    yd = Mth.lerp(0.08, yd, (target.y - y) * 0.12);
    zd = Mth.lerp(0.08, zd, (target.z - z) * 0.12);
    move(xd, yd, zd);
    float fade =
        fading >= 0 ? fading / (float) FADE_TICKS : Math.min(1.0F, age / (float) FADE_TICKS);
    alpha = fade;
    updateFacing(camera);
    boolean gliding = (age / 40) % 3 == 2;
    setSprite(frames[gliding ? 0 : (age / flapPeriod) % 2]);
  }

  private Vec3 orbitPoint(float time) {
    double wobble = Math.sin((time + radius * 7.0) * 0.05) * 1.5;
    return whale
        .position()
        .add(Math.cos(angle) * radius, height + wobble, Math.sin(angle) * radius);
  }

  private void updateFacing(Vec3 camera) {
    double dx = x - camera.x;
    double dz = z - camera.z;
    double distance = Math.sqrt(dx * dx + dz * dz);
    if (distance < 1.0E-4 || xd * xd + zd * zd < 1.0E-6) return;
    double across = zd * (dx / distance) - xd * (dz / distance);
    if (Math.abs(across) > 0.01) facingRight = across > 0.0;
  }

  @Override
  protected float getU0() {
    return facingRight ? sprite.getU1() : sprite.getU0();
  }

  @Override
  protected float getU1() {
    return facingRight ? sprite.getU0() : sprite.getU1();
  }

  @Override
  public ParticleRenderType getRenderType() {
    return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
  }
}
