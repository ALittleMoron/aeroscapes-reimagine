package dev.aeroscapes.equipment.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Server-owned sight obstruction. A mob can finish its last known path through smoke. */
public final class SmokeArrows {
  private static final String SMOKE = "aeroscapes_reimagine:smoke_arrow";
  private static final int DURATION_TICKS = 100;
  private static final double RADIUS = 2.5;
  private static final Map<ServerLevel, List<Cloud>> CLOUDS = new WeakHashMap<>();

  private record Cloud(Vec3 center, long end) {}

  private SmokeArrows() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SmokeArrows::impact);
    NeoForge.EVENT_BUS.addListener(SmokeArrows::tick);
  }

  static boolean isSmoke(ItemStack ammo) {
    return ammo.is(Items.ARROW)
        && ammo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(SMOKE);
  }

  static void launched(AbstractArrow arrow, ItemStack ammo) {
    if (arrow.pickup == AbstractArrow.Pickup.ALLOWED
        && !ammo.has(DataComponents.INTANGIBLE_PROJECTILE)) {
      arrow.getPersistentData().putBoolean(SMOKE, true);
      arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
    }
  }

  private static void impact(ProjectileImpactEvent event) {
    if (!(event.getProjectile() instanceof AbstractArrow arrow)
        || !(arrow.level() instanceof ServerLevel level)
        || !(event.getRayTraceResult() instanceof BlockHitResult hit)
        || !arrow.getPersistentData().getBoolean(SMOKE)) return;
    arrow.getPersistentData().remove(SMOKE);
    var center = hit.getLocation().relative(hit.getDirection(), 0.25);
    CLOUDS
        .computeIfAbsent(level, ignored -> new ArrayList<>())
        .add(new Cloud(center, level.getGameTime() + DURATION_TICKS));
    show(level, center);
  }

  public static boolean obscures(Mob viewer, Entity target) {
    if (!(viewer instanceof Enemy)
        || !(target instanceof ServerPlayer)
        || !(viewer.level() instanceof ServerLevel level)
        || target.level() != level) return false;
    var clouds = CLOUDS.get(level);
    if (clouds == null) return false;
    Vec3 start = viewer.getEyePosition();
    Vec3 ray = target.getEyePosition().subtract(start);
    double length = ray.lengthSqr();
    if (length < 1.0E-6) return false;
    long now = level.getGameTime();
    for (Cloud cloud : clouds) {
      Vec3 offset = cloud.center().subtract(start);
      double fraction = Math.clamp(offset.dot(ray) / length, 0.0, 1.0);
      if (cloud.end() > now && ray.scale(fraction).distanceToSqr(offset) < RADIUS * RADIUS)
        return true;
    }
    return false;
  }

  private static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var clouds = CLOUDS.get(level);
    if (clouds == null) return;
    long now = level.getGameTime();
    clouds.removeIf(cloud -> cloud.end() <= now);
    if (clouds.isEmpty()) CLOUDS.remove(level);
    if (now % 4 == 0) {
      for (Cloud cloud : clouds) show(level, cloud.center());
    }
  }

  private static void show(ServerLevel level, Vec3 center) {
    level.sendParticles(
        ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 36, 0.9, 0.85, 0.9, 0.02);
  }
}
