package dev.aeroscapes.sky.fauna;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

public final class CloudJelly extends FlyingMob {
  private static final EntityDataAccessor<Float> DIAMETER =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<String> FORM =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.STRING);
  private static final int TARGET_COUNT =
      1
          + Arrays.stream(CloudJellyAnatomy.Form.values())
              .mapToInt(form -> CloudJellyAnatomy.of(form).strands().size())
              .max()
              .orElseThrow();
  private static final EntityDataAccessor<Float> DRIFT_HEADING =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Float> TURN_FROM =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.FLOAT);
  private static final EntityDataAccessor<Long> TURN_START =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.LONG);
  private static final EntityDataAccessor<Boolean> PULSING =
      SynchedEntityData.defineId(CloudJelly.class, EntityDataSerializers.BOOLEAN);
  private static final ResourceKey<DamageType> STING =
      ResourceKey.create(
          Registries.DAMAGE_TYPE,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "jelly_sting"));
  private static final String LAST_STING = "AeroscapesCloudJellyLastSting";
  private static final int COOLDOWN = 40;
  private static final int SWEEP_RANGE = 32;

  private Map<Integer, Vec3> samples = Map.of();
  private Vec3 previousFeet;
  private long sampledAt = Long.MIN_VALUE;
  private final CloudJellyNavigation navigation = new CloudJellyNavigation();
  private double driftAltitude = Double.NaN;
  private long contactTime = Long.MIN_VALUE, contactSeed;
  private boolean contactRest;
  private float contactHeading;
  private CloudJellyContact previousAnatomy;
  private int pulseTicks;
  private float contactDiameter;
  private CloudJellyAnatomy.Form contactForm;
  private CloudJellyContact anatomy;
  private final AerialHitPart[] targets;

  public CloudJelly(EntityType<? extends CloudJelly> type, Level level) {
    super(type, level);
    targets = new AerialHitPart[TARGET_COUNT];
    for (int i = 0; i < targets.length; i++) targets[i] = new AerialHitPart(this);
    setId(ENTITY_COUNTER.getAndAdd(targets.length + 1) + 1);
    positionTargets();
    setNoGravity(true);
    if (!level.isClientSide) setDriftHeading(random.nextFloat() * 360);
  }

  public static AttributeSupplier.Builder createAttributes() {
    return createMobAttributes().add(Attributes.MAX_HEALTH, 12);
  }

  @Override
  public void setId(int id) {
    super.setId(id);
    for (int i = 0; i < targets.length; i++) targets[i].setId(id + i + 1);
  }

  @Override
  public boolean isMultipartEntity() {
    return true;
  }

  @Override
  public PartEntity<?>[] getParts() {
    return targets;
  }

  @Override
  public boolean isPickable() {
    return false;
  }

  @Override
  public void onAddedToLevel() {
    super.onAddedToLevel();
    positionTargets();
  }

  private CloudJellyContact anatomy() {
    float width = getBbWidth();
    var form = form();
    long now = level().getGameTime(), seed = CloudJellyMotion.seed(getUUID());
    float heading = entityData.get(DRIFT_HEADING);
    boolean same =
        anatomy != null
            && width == contactDiameter
            && form == contactForm
            && seed == contactSeed
            && isNoAi() == contactRest
            && heading == contactHeading;
    if (!same || contactTime != now) {
      previousAnatomy = same && contactTime == now - 1 ? anatomy : null;
      anatomy = new CloudJellyContact(swimFrame(now), width);
      contactTime = now;
      contactForm = form;
      contactDiameter = width;
      contactSeed = seed;
      contactRest = isNoAi();
      contactHeading = heading;
    }
    return anatomy;
  }

  public CloudJellyMotion swimFrame(double time) {
    return new CloudJellyMotion(
        form(), getBbWidth(), CloudJellyMotion.seed(getUUID()), time, this::headingAt, isNoAi());
  }

  public AABB clearanceBounds() {
    return anatomy().envelope().move(position());
  }

  @Override
  public AABB getBoundingBoxForCulling() {
    var current = anatomy();
    var bounds =
        previousAnatomy == null
            ? current.envelope()
            : current.envelope().minmax(previousAnatomy.envelope());
    return bounds.move(position()).inflate(.1);
  }

  public Vec3 bellCenter() {
    return position().add(anatomy().bellCenter());
  }

  private void positionTargets() {
    var boxes = anatomy().targetBounds();
    for (int i = 0; i < boxes.size(); i++) targets[i].place(boxes.get(i).move(position()));
    for (int i = boxes.size(); i < targets.length; i++) targets[i].hide();
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DIAMETER, 1.0F);
    builder.define(FORM, CloudJellyAnatomy.Form.FRINGE.id());
    builder.define(PULSING, false);
    builder.define(DRIFT_HEADING, 0F);
    builder.define(TURN_FROM, 0F);
    builder.define(TURN_START, Long.MIN_VALUE);
  }

  public void initSize(float diameter) {
    entityData.set(DIAMETER, Float.isFinite(diameter) ? Mth.clamp(diameter, 1, 16) : 1);
  }

  public float bellDiameter() {
    return entityData.get(DIAMETER);
  }

  public CloudJellyAnatomy.Form form() {
    return CloudJellyAnatomy.Form.byId(entityData.get(FORM));
  }

  public void setForm(CloudJellyAnatomy.Form form) {
    entityData.set(FORM, form.id());
  }

  public boolean isPulsing() {
    return entityData.get(PULSING);
  }

  public void setDriftHeading(float degrees) {
    entityData.set(TURN_START, Long.MIN_VALUE);
    entityData.set(DRIFT_HEADING, Float.isFinite(degrees) ? Mth.wrapDegrees(degrees) : 0);
  }

  private float headingAt(double time) {
    long start = entityData.get(TURN_START);
    float target = entityData.get(DRIFT_HEADING);
    if (start == Long.MIN_VALUE) return target;
    double progress = Mth.clamp((time - start) / (70 + 15 * getBbWidth()), 0, 1);
    return entityData.get(TURN_FROM)
        + Mth.wrapDegrees(target - entityData.get(TURN_FROM))
            * (float) (progress * progress * (3 - 2 * progress));
  }

  void turnAway(Vec3 normal, long now) {
    if (normal.horizontalDistanceSqr() < .1) return;
    float target = (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
    float current = headingAt(now);
    long last = entityData.get(TURN_START);
    if (Math.abs(Mth.wrapDegrees(target - entityData.get(DRIFT_HEADING))) < 25
        || last != Long.MIN_VALUE && now - last < 70 + 15 * getBbWidth()) return;
    entityData.set(TURN_FROM, current);
    entityData.set(TURN_START, now);
    entityData.set(DRIFT_HEADING, target);
  }

  @Override
  protected EntityDimensions getDefaultDimensions(Pose pose) {
    float diameter = bellDiameter();
    var base = super.getDefaultDimensions(pose);
    float height = diameter * CloudJellyAnatomy.of(form()).height() / CloudJellyAnatomy.SIZE_UNITS;
    return base.scale(diameter, height / base.height());
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (DIAMETER.equals(accessor) || FORM.equals(accessor)) {
      refreshDimensions();
    }
    if (targets != null
        && (DIAMETER.equals(accessor) || FORM.equals(accessor) || DRIFT_HEADING.equals(accessor)))
      positionTargets();
  }

  @Override
  protected void customServerAiStep() {
    if (!Double.isFinite(driftAltitude)) driftAltitude = getY();
    var stroke = swimFrame(level().getGameTime());
    Vec3 velocity = stroke.velocity(getDeltaMovement());
    double restoring = Mth.clamp((driftAltitude - getY()) * .0012, -.025, .025);
    if (onGround() || getY() < level().getMinBuildHeight() + 8)
      restoring = Math.max(.025, restoring);
    if (clearanceBounds().maxY > level().getMaxBuildHeight() - 4)
      restoring = Math.min(-.04, restoring);
    setDeltaMovement(navigation.avoid(this, velocity.add(0, restoring, 0)));
  }

  @Override
  public void tick() {
    super.tick();
    positionTargets();
    if (level().isClientSide || !isAlive()) return;
    if (pulseTicks > 0 && --pulseTicks == 0) entityData.set(PULSING, false);
    long now = level().getGameTime();
    boolean consecutive =
        sampledAt == now - 1
            && previousFeet != null
            && previousFeet.distanceToSqr(position()) <= SWEEP_RANGE * SWEEP_RANGE;
    Vec3 start = consecutive ? previousFeet : position();
    Map<Integer, Vec3> next = new HashMap<>();
    AABB nearby =
        getBoundingBoxForCulling()
            .expandTowards(start.subtract(position()))
            .inflate(SWEEP_RANGE + 0.25);
    for (var victim :
        level().getEntitiesOfClass(LivingEntity.class, nearby, CloudJelly::canSting)) {
      AABB bounds = CloudJellyCover.worldBounds(victim);
      Vec3 center = bounds.getCenter();
      Vec3 previous = consecutive ? samples.getOrDefault(victim.getId(), center) : center;
      stingAlong(victim, previous, start, bounds);
      next.put(victim.getId(), center);
    }
    samples = next;
    previousFeet = position();
    sampledAt = now;
  }

  private static boolean canSting(LivingEntity victim) {
    return victim.isAlive()
        && !victim.isSpectator()
        && !(victim instanceof CloudJelly)
        && !(victim instanceof ArmorStand)
        && !(victim instanceof Player player && player.isCreative());
  }

  public boolean stingAlong(
      LivingEntity victim, Vec3 previousVictimCenter, Vec3 previousJellyFeet) {
    return stingAlong(
        victim, previousVictimCenter, previousJellyFeet, CloudJellyCover.worldBounds(victim));
  }

  private boolean stingAlong(
      LivingEntity victim, Vec3 previousVictimCenter, Vec3 previousJellyFeet, AABB victimBounds) {
    if (level().isClientSide || !isAlive() || level() != victim.level() || !canSting(victim))
      return false;
    Vec3 center = victimBounds.getCenter();
    // Long discontinuities are teleports. Only fresh endpoint contact counts.
    boolean continuous =
        center.distanceToSqr(previousVictimCenter) <= SWEEP_RANGE * SWEEP_RANGE
            && previousJellyFeet.distanceToSqr(position()) <= SWEEP_RANGE * SWEEP_RANGE;
    Vec3 start = continuous ? previousJellyFeet : position();
    Vec3 previous = continuous ? previousVictimCenter : center;
    AABB sweptVictim = victimBounds.expandTowards(previous.subtract(center));
    if (!getBoundingBoxForCulling()
        .expandTowards(start.subtract(position()))
        .inflate(CloudJellyContact.MARGIN)
        .intersects(sweptVictim)) return false;
    long now = level().getGameTime();
    CompoundTag victimData = victim.getPersistentData();
    CompoundTag data =
        victim instanceof Player ? victimData.getCompound(Player.PERSISTED_NBT_TAG) : victimData;
    boolean firstSting = !data.contains(LAST_STING, Tag.TAG_LONG);
    long last = data.getLong(LAST_STING);
    if (!firstSting && now >= last && now - last < COOLDOWN) return false;
    var shape = anatomy();
    var before = continuous && previousAnatomy != null ? previousAnatomy : shape;
    boolean touched =
        shape.touches(
            before,
            previous.subtract(start),
            center.subtract(position()),
            victimBounds,
            (time, root, skin, contact) -> {
              Vec3 feet = start.lerp(position(), time);
              Vec3 core = feet.add(before.bellCenter().lerp(shape.bellCenter(), time));
              return CloudJellyCover.exposed(
                  victim, time, core, feet.add(root), feet.add(skin), feet.add(contact));
            });
    if (!touched || !victim.hurt(damageSources().source(STING, this), 2)) return false;
    if (victim instanceof Player player) {
      data = victimData.getCompound(Player.PERSISTED_NBT_TAG);
      victimData.put(Player.PERSISTED_NBT_TAG, data);
      if (firstSting)
        player.displayClientMessage(
            Component.translatable("message.aeroscapes_reimagine.cloud_jelly.shelter"), true);
    }
    data.putLong(LAST_STING, now);
    pulseTicks = 10;
    entityData.set(PULSING, true);
    if (!isSilent())
      level()
          .playSound(
              null,
              center.x,
              center.y,
              center.z,
              SoundEvents.PUFFER_FISH_STING,
              getSoundSource(),
              0.65F,
              1.2F);
    return true;
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  protected void pushEntities() {
    // The broad movement box includes empty space between the tentacles.
  }

  @Override
  public void addAdditionalSaveData(CompoundTag tag) {
    super.addAdditionalSaveData(tag);
    tag.putFloat("BellDiameter", bellDiameter());
    tag.putString("JellyForm", form().id());
    tag.putFloat("DriftHeading", entityData.get(DRIFT_HEADING));
    if (entityData.get(TURN_START) != Long.MIN_VALUE) {
      tag.putLong("DriftTurnStart", entityData.get(TURN_START));
      tag.putFloat("DriftTurnFrom", entityData.get(TURN_FROM));
    }
    if (Double.isFinite(driftAltitude)) tag.putDouble("DriftAltitude", driftAltitude);
  }

  @Override
  public void readAdditionalSaveData(CompoundTag tag) {
    super.readAdditionalSaveData(tag);
    setForm(CloudJellyAnatomy.Form.byId(tag.getString("JellyForm")));
    if (tag.contains("BellDiameter", Tag.TAG_ANY_NUMERIC)) initSize(tag.getFloat("BellDiameter"));
    driftAltitude =
        tag.contains("DriftAltitude", Tag.TAG_ANY_NUMERIC)
            ? tag.getDouble("DriftAltitude")
            : Double.NaN;
    if (tag.contains("DriftHeading", Tag.TAG_ANY_NUMERIC))
      setDriftHeading(tag.getFloat("DriftHeading"));
    if (tag.contains("DriftTurnStart", Tag.TAG_LONG)
        && tag.contains("DriftTurnFrom", Tag.TAG_ANY_NUMERIC)
        && Float.isFinite(tag.getFloat("DriftTurnFrom"))) {
      entityData.set(TURN_FROM, Mth.wrapDegrees(tag.getFloat("DriftTurnFrom")));
      entityData.set(TURN_START, tag.getLong("DriftTurnStart"));
    }
  }
}
