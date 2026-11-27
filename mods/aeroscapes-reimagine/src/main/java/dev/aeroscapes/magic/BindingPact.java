package dev.aeroscapes.magic;

import dev.ryanhcode.sable.Sable;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.spells.nature.RootSpell;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BindingPact {
  private static final String PAYMENT = "aeroscapes_reimagine_binding_pact";
  private static final String MARKER_TALENT = "magic_arcane_shackle_marker";
  private static final int MARKER_TICKS = 160;
  private static final double MARKER_RANGE = 3;
  private static final Map<UUID, ShackleMarker> MARKERS = new HashMap<>();

  private BindingPact() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(BindingPact::summonHit);
    NeoForge.EVENT_BUS.addListener(BindingPact::tickShackles);
    NeoForge.EVENT_BUS.addListener(BindingPact::unload);
  }

  private record ShackleMarker(
      ServerLevel level,
      ServerPlayer owner,
      BlockPos floor,
      Vec3 point,
      long expires,
      Predicate<LivingEntity> nativeTarget,
      BiConsumer<LivingEntity, Vec3> tether) {
    private boolean eligible(LivingEntity target) {
      if (!(target instanceof Enemy)
          || !target.isAlive()
          || target.distanceToSqr(point) > MARKER_RANGE * MARKER_RANGE
          || !nativeTarget.test(target)) return false;
      if (target instanceof IMagicSummon summon
          && summon.getSummoner() instanceof ServerPlayer summoner
          && SpellSpecializations.friendly(owner, summoner)) return false;
      return level
              .clip(
                  new ClipContext(
                      point,
                      target.getEyePosition(),
                      ClipContext.Block.COLLIDER,
                      ClipContext.Fluid.NONE,
                      owner))
              .getType()
          == HitResult.Type.MISS;
    }

    private void show() {
      level.sendParticles(
          ParticleHelper.ENDER_SPARKS, point.x, point.y, point.z, 6, .22, .01, .22, .015);
    }
  }

  public static void markShackle(
      ServerPlayer owner,
      BlockHitResult hit,
      Predicate<LivingEntity> nativeTarget,
      BiConsumer<LivingEntity, Vec3> tether) {
    ServerLevel level = owner.serverLevel();
    BlockPos floor = hit.getBlockPos();
    if (hit.getType() != HitResult.Type.BLOCK
        || hit.getDirection() != Direction.UP
        || hit.isInside()
        || !markerFloor(level, floor)
        || !SpellSpecializations.learned(owner, MARKER_TALENT)) return;
    Vec3 point = hit.getLocation().add(0, .06, 0);
    var marker =
        new ShackleMarker(
            level,
            owner,
            floor.immutable(),
            point,
            level.getGameTime() + MARKER_TICKS,
            nativeTarget,
            tether);
    MARKERS.put(owner.getUUID(), marker);
    marker.show();
  }

  private static boolean markerFloor(ServerLevel level, BlockPos floor) {
    BlockPos above = floor.above();
    if (!level.isLoaded(floor)
        || level.isOutsideBuildHeight(above)
        || ModList.get().isLoaded("sable") && ShipPlots.contains(level, floor)) return false;
    var surface = level.getBlockState(floor);
    var clearance = level.getBlockState(above);
    return surface.isCollisionShapeFullBlock(level, floor)
        && surface.getFluidState().isEmpty()
        && clearance.getFluidState().isEmpty()
        && clearance.getCollisionShape(level, above).isEmpty();
  }

  private static void tickShackles(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    long now = level.getGameTime();
    var markers = MARKERS.values().iterator();
    while (markers.hasNext()) {
      var marker = markers.next();
      if (marker.level() != level) continue;
      var owner = marker.owner();
      if (now >= marker.expires()
          || owner.level() != level
          || !owner.isAlive()
          || owner.hasDisconnected()
          || !markerFloor(level, marker.floor())
          || !SpellSpecializations.learned(owner, MARKER_TALENT)) {
        markers.remove();
        continue;
      }
      Vec3 point = marker.point();
      var target =
          level
              .getEntitiesOfClass(
                  LivingEntity.class,
                  new AABB(point, point).inflate(MARKER_RANGE),
                  marker::eligible)
              .stream()
              .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(point)));
      if (target.isPresent()) {
        markers.remove();
        marker.tether().accept(target.get(), point);
      } else if (now % 4 == 0) {
        marker.show();
      }
    }
  }

  private static void unload(LevelEvent.Unload event) {
    MARKERS.values().removeIf(marker -> marker.level() == event.getLevel());
  }

  private static final class ShipPlots {
    private static boolean contains(ServerLevel level, BlockPos floor) {
      return Sable.HELPER.getContaining(level, floor) != null;
    }
  }

  public record PaidRoot(LivingEntity target, Entity previousVehicle, float refund) {
    public void bind(ServerPlayer owner) {
      if (target.getVehicle() instanceof RootEntity root
          && root != previousVehicle
          && root.isAddedToLevel()
          && root.getOwner() == owner
          && root.getTarget() == target) {
        var payment = new CompoundTag();
        payment.putUUID("target", target.getUUID());
        payment.putFloat("refund", refund);
        root.getPersistentData().put(PAYMENT, payment);
      }
    }
  }

  public static PaidRoot paidRoot(
      AbstractSpell spell, ServerPlayer owner, MagicData magic, float paid) {
    if (!(spell instanceof RootSpell)
        || !(paid > 0)
        || !(magic.getAdditionalCastData() instanceof TargetEntityCastData cast)) return null;
    LivingEntity target = cast.getTarget(owner.serverLevel());
    if (target == null) return null;
    double share =
        MagicTalents.bonus(SpellKnowledge.unlocked(owner), "conjuration", "binding_refund");
    return share > 0 ? new PaidRoot(target, target.getVehicle(), (float) (paid * share)) : null;
  }

  private static void summonHit(LivingDamageEvent.Post event) {
    LivingEntity target = event.getEntity();
    if (event.getNewDamage() <= 0
        || !(target.getVehicle() instanceof RootEntity root)
        || !root.isAlive()
        || !root.getPersistentData().contains(PAYMENT, Tag.TAG_COMPOUND)
        || !(root.getOwner() instanceof ServerPlayer owner)) return;
    CompoundTag payment = root.getPersistentData().getCompound(PAYMENT);
    if (!payment.hasUUID("target")
        || !target.getUUID().equals(payment.getUUID("target"))
        || summonOwner(event.getSource()) != owner) return;
    root.getPersistentData().remove(PAYMENT);
    var magic = MagicData.getPlayerMagicData(owner);
    magic.addMana(payment.getFloat("refund"));
    PacketDistributor.sendToPlayer(owner, new SyncManaPacket(magic));
  }

  private static Entity summonOwner(DamageSource source) {
    if (source.getDirectEntity() instanceof IMagicSummon summon) return summon.getSummoner();
    if (source.getDirectEntity() instanceof Projectile projectile
        && projectile.getOwner() instanceof IMagicSummon summon) return summon.getSummoner();
    return source.getEntity() instanceof IMagicSummon summon ? summon.getSummoner() : null;
  }
}
