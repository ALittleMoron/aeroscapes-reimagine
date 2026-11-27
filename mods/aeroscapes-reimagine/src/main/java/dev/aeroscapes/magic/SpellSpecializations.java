package dev.aeroscapes.magic;

import dev.ryanhcode.sable.Sable;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class SpellSpecializations {
  private static final double OAKSKIN_REACH = 8.0;
  private static final double FROSTWAVE_HEIGHT = 4.0;

  private SpellSpecializations() {}

  public static boolean learned(ServerPlayer player, String node) {
    return SpellKnowledge.unlocked(player).contains(node);
  }

  public static boolean friendly(ServerPlayer caster, ServerPlayer recipient) {
    if (caster.level() != recipient.level()
        || !recipient.isAlive()
        || recipient.isSpectator()
        || recipient instanceof FakePlayer) return false;
    if (caster == recipient) return true;
    if (caster.getTeam() != null || recipient.getTeam() != null)
      return caster.isAlliedTo(recipient);
    return !caster.server.isPvpAllowed();
  }

  public static ServerPlayer oakskinTarget(ServerPlayer caster) {
    var hit = ProjectileUtil.getHitResultOnViewVector(caster, Entity::isPickable, OAKSKIN_REACH);
    if (hit instanceof EntityHitResult entityHit
        && entityHit.getEntity() instanceof ServerPlayer recipient
        && friendly(caster, recipient)
        && caster.hasLineOfSight(recipient)) return recipient;
    return caster;
  }

  public static void frostwave(ServerPlayer caster, float radius) {
    if (!Float.isFinite(radius) || radius <= 0) return;
    var level = caster.serverLevel();
    caster.clearFire();
    double rangeSquared = (double) radius * radius;
    var bounds = caster.getBoundingBox().inflate(radius, FROSTWAVE_HEIGHT, radius);
    for (var ally : level.getEntitiesOfClass(ServerPlayer.class, bounds, ServerPlayer::isOnFire)) {
      if (ally != caster
          && caster.distanceToSqr(ally) < rangeSquared
          && friendly(caster, ally)
          && Utils.hasLineOfSight(level, caster, ally, true)) ally.clearFire();
    }

    boolean ships = ModList.get().isLoaded("sable");
    var origin = caster.position();
    var eyes = caster.getEyePosition();
    for (var cursor :
        BlockPos.betweenClosed(
            BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
            BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
      if (!level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) continue;
      var state = level.getBlockState(cursor);
      if (!state.is(Blocks.FIRE)) continue;
      var center = Vec3.atCenterOf(cursor);
      if (origin.distanceToSqr(center) >= rangeSquared
          || !level.getWorldBorder().isWithinBounds(cursor)
          || !level.mayInteract(caster, cursor)
          || ships && ShipPlots.contains(level, cursor)
          || !visible(level, caster, eyes, center)) continue;
      var pos = cursor.immutable();
      var permission =
          CommonHooks.fireBlockBreak(
              level, caster.gameMode.getGameModeForPlayer(), caster, pos, state);
      if (!permission.isCanceled() && level.getBlockState(pos).is(Blocks.FIRE))
        level.removeBlock(pos, false);
    }
  }

  private static boolean visible(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 target) {
    return level
            .clip(
                new ClipContext(
                    from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
            .getType()
        == HitResult.Type.MISS;
  }

  private static final class ShipPlots {
    private static boolean contains(ServerLevel level, BlockPos position) {
      return Sable.HELPER.getContaining(level, position) != null;
    }
  }
}
