package dev.aeroscapes.sky.fauna;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Vector3f;

/** An unprovoked ray glides past. A directly attacked ray makes one short defensive pass. */
public final class SkyRay extends FlyingMob {
  private static final EntityDataAccessor<Boolean> DEFENDING =
      SynchedEntityData.defineId(SkyRay.class, EntityDataSerializers.BOOLEAN);
  // Phase in degrees, stroke strength and bank in radians. New trackers get the current stroke.
  private static final EntityDataAccessor<Vector3f> FLIGHT =
      SynchedEntityData.defineId(SkyRay.class, EntityDataSerializers.VECTOR3);
  private int defenseStart;
  private int retreatUntil;
  private float heading;
  private boolean coasting;
  private float trackYaw = Float.NaN;
  private Vector3f previousFlight;
  private Vector3f currentFlight;
  private int flightUpdateTick;
  private final SkyRayWingPart[] wings;

  public SkyRay(EntityType<? extends SkyRay> type, Level level) {
    super(type, level);
    wings = SkyRayWingPart.create(this);
    setId(ENTITY_COUNTER.getAndAdd(wings.length + 1) + 1);
    setNoGravity(true);
  }

  @Override
  public void setId(int id) {
    super.setId(id);
    for (int i = 0; i < wings.length; i++) wings[i].setId(id + i + 1);
  }

  @Override
  public boolean isMultipartEntity() {
    return true;
  }

  @Override
  public PartEntity<?>[] getParts() {
    return wings;
  }

  @Override
  public void onAddedToLevel() {
    super.onAddedToLevel();
    SkyRayWingPart.position(this, wings);
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide) updateFlight();
    setYBodyRot(getYRot());
    SkyRayWingPart.position(this, wings);
  }

  private void updateFlight() {
    Vec3 travel = position().subtract(xo, yo, zo);
    double speed = travel.length();
    float turn = 0;
    float pitch = 0;
    if (travel.horizontalDistanceSqr() > 1.0e-5) {
      float yaw = (float) Math.toDegrees(Math.atan2(-travel.x, travel.z));
      if (Float.isFinite(trackYaw)) turn = Mth.wrapDegrees(yaw - trackYaw);
      trackYaw = yaw;
      setYRot(
          Mth.approachDegrees(getYRot(), yaw, isDefending() || tickCount < retreatUntil ? 13 : 4));
      setYHeadRot(getYRot());
    }
    if (speed > 0.004) {
      pitch = (float) Math.toDegrees(Math.atan2(-travel.y, travel.horizontalDistance()));
    }
    setXRot(Mth.approach(getXRot(), Mth.clamp(pitch, -25, 25), 2.5F));
    Vector3f last = entityData.get(FLIGHT);
    float phase = currentFlight == null ? Math.floorMod(getId() * 53, 360) : last.x();
    float power = Mth.approach(last.y(), coasting || speed < 0.006 ? 0 : 1, 0.075F);
    phase = (phase + SkyRayWings.beatRate(speed) * Mth.RAD_TO_DEG * power) % 360;
    float bank = Mth.lerp(0.20F, last.z(), Mth.clamp(-turn * 0.12F, -0.46F, 0.46F));
    if (Math.abs(bank) < 0.001F) bank = 0;
    entityData.set(FLIGHT, new Vector3f(phase, power, bank));
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
    super.onSyncedDataUpdated(key);
    if (FLIGHT.equals(key)) {
      var next = entityData.get(FLIGHT);
      previousFlight = currentFlight == null ? next : currentFlight;
      currentFlight = next;
      flightUpdateTick = tickCount;
      if (level().isClientSide && wings != null) SkyRayWingPart.position(this, wings);
    }
  }

  public SkyRayWings.Pose flightPose(float partialTick) {
    float t = Mth.clamp(partialTick, 0, 1);
    var next = entityData.get(FLIGHT);
    var last = previousFlight == null || tickCount > flightUpdateTick + 1 ? next : previousFlight;
    return new SkyRayWings.Pose(
        Mth.rotLerp(t, last.x(), next.x()) * Mth.DEG_TO_RAD,
        Mth.lerp(t, last.y(), next.y()),
        Mth.lerp(t, last.z(), next.z()),
        Mth.lerp(t, xRotO, getXRot()) * Mth.DEG_TO_RAD);
  }

  public static AttributeSupplier.Builder createAttributes() {
    return createMobAttributes()
        .add(Attributes.MAX_HEALTH, 24)
        .add(Attributes.ATTACK_DAMAGE, 4)
        .add(Attributes.FOLLOW_RANGE, 24);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DEFENDING, false);
    builder.define(FLIGHT, new Vector3f());
  }

  public boolean isDefending() {
    return entityData.get(DEFENDING);
  }

  public AABB clearanceBounds() {
    double scale = getScale();
    return new AABB(
        getX() - 5 * scale,
        getY() - 2.9 * scale,
        getZ() - 5 * scale,
        getX() + 5 * scale,
        getY() + 5.0 * scale,
        getZ() + 5 * scale);
  }

  @Override
  public AABB getBoundingBoxForCulling() {
    return clearanceBounds();
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    boolean hurt = super.hurt(source, amount);
    if (hurt
        && !level().isClientSide
        && isAlive()
        && !isDefending()
        && tickCount >= retreatUntil
        && source.getEntity() instanceof LivingEntity attacker
        && attacker != this
        && eligibleAttacker(attacker)
        && directAttack(source)) {
      setTarget(attacker);
      if (getTarget() == attacker) {
        defenseStart = tickCount;
        entityData.set(DEFENDING, true);
        playSound(SoundEvents.PHANTOM_FLAP, 0.8F, 0.8F);
      }
    }
    return hurt;
  }

  private static boolean directAttack(DamageSource source) {
    if (source.is(DamageTypeTags.IS_EXPLOSION)) return false;
    if (source.getDirectEntity() instanceof Projectile && source.is(DamageTypeTags.IS_PROJECTILE))
      return true;
    return source.getDirectEntity() == source.getEntity()
        && (source.is(DamageTypes.PLAYER_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
  }

  private boolean eligibleAttacker(LivingEntity attacker) {
    return attacker.level() == level()
        && attacker.isAlive()
        && !attacker.isSpectator()
        && !(attacker instanceof Player player && player.isCreative())
        && !isAlliedTo(attacker)
        && distanceToSqr(attacker) <= 24 * 24;
  }

  @Override
  protected void customServerAiStep() {
    LivingEntity target = getTarget();
    Vec3 desired;
    coasting = false;
    if (isDefending()
        && target != null
        && eligibleAttacker(target)
        && tickCount - defenseStart < 80) {
      Vec3 toward = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
      desired = toward.normalize().scale(tickCount - defenseStart < 8 ? 0.06 : 0.60);
      if (tickCount - defenseStart >= 8
          && getBoundingBox().inflate(0.65).intersects(target.getBoundingBox())
          && hasLineOfSight(target)) {
        doHurtTarget(target);
        finishDefense(toward);
        desired = Vec3.directionFromRotation(-8, heading).scale(0.38);
      }
    } else {
      if (isDefending()) finishDefense(getLookAngle());
      if (tickCount <= 1) heading = getYRot();
      if (tickCount >= retreatUntil && (tickCount + getId()) % 100 == 0) {
        heading += random.nextFloat() * 52 - 26;
      }
      if (horizontalCollision && tickCount % 12 == 0) heading += 70;
      boolean retreating = tickCount < retreatUntil;
      coasting = !retreating && Math.floorMod(tickCount + getId() * 13, 220) >= 152;
      float pitch = (float) Math.sin((tickCount + getId()) * 0.027) * 12;
      if (coasting) pitch = 5 + pitch * 0.3F;
      if (getY() < level().getMinBuildHeight() + 12 || onGround()) pitch = -22;
      if (getY() > level().getMaxBuildHeight() - 12) pitch = 22;
      float steering = Mth.approachDegrees(getYRot(), heading, retreating ? 35 : 9);
      float phase = entityData.get(FLIGHT).x() * Mth.DEG_TO_RAD;
      double speed = retreating ? 0.38 : coasting ? 0.15 : 0.22 - 0.022 * Mth.cos(2 * phase);
      desired = Vec3.directionFromRotation(pitch, steering).scale(speed);
    }
    setDeltaMovement(getDeltaMovement().lerp(desired, coasting ? 0.12 : 0.22));
  }

  private void finishDefense(Vec3 toward) {
    heading = (float) Math.toDegrees(Math.atan2(toward.x, -toward.z));
    retreatUntil = tickCount + 40;
    setTarget(null);
    entityData.set(DEFENDING, false);
  }
}
