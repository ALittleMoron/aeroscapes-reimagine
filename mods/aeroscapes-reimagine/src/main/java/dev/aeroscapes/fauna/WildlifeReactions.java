package dev.aeroscapes.fauna;

import dev.aeroscapes.ecology.cliff.CliffHabitats;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class WildlifeReactions {
  private static final ResourceLocation GULL =
      ResourceLocation.fromNamespaceAndPath("fowlplay", "gull");
  private static final EntityTypeTest<Entity, Mob> MOBS = EntityTypeTest.forClass(Mob.class);
  private static final int MAX_REACTIONS = 8;
  private static final int MAX_CANDIDATES = 16;

  private WildlifeReactions() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(WildlifeReactions::explosion);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, WildlifeReactions::lightning);
  }

  private static void explosion(ExplosionEvent.Detonate event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var explosion = event.getExplosion();
    float strength = explosion.radius();
    if (strength >= 2) startle(level, explosion.center(), Math.min(28, 8 + 4 * strength));
  }

  private static void lightning(VanillaGameEvent event) {
    if (event.getLevel() instanceof ServerLevel level
        && event.getVanillaEvent().is(GameEvent.LIGHTNING_STRIKE.key())) {
      startle(level, event.getEventPosition(), 24);
    }
  }

  private static void startle(ServerLevel level, Vec3 source, double radius) {
    var candidates = new ArrayList<Mob>(MAX_CANDIDATES);
    level.getEntities(
        MOBS,
        new AABB(source, source).inflate(radius),
        mob ->
            (mob instanceof DeerReaction || isGull(mob))
                && mob.isAlive()
                && !mob.isNoAi()
                && !mob.isPassenger()
                && mob.distanceToSqr(source) < radius * radius,
        candidates,
        MAX_CANDIDATES);
    candidates.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(source)));
    int reacted = 0;
    for (Mob mob : candidates) {
      boolean startled =
          mob instanceof DeerReaction deer
              ? deer.aeroscapes$startle(source)
              : CliffHabitats.startle(mob, source);
      if (startled && ++reacted == MAX_REACTIONS) return;
    }
  }

  private static boolean isGull(Mob mob) {
    return GULL.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
  }

  public interface DeerReaction {
    boolean aeroscapes$startle(Vec3 source);
  }
}
