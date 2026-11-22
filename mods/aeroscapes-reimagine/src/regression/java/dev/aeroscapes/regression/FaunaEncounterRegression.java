package dev.aeroscapes.regression;

import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.aeroscapes.sky.fauna.CloudJelly;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import dev.aeroscapes.sky.fauna.FaunaEncounters;
import dev.aeroscapes.sky.fauna.SkyRay;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class FaunaEncounterRegression {
  private static final TicketType<ChunkPos> TICKET =
      TicketType.create("aeroscapes_fauna", Comparator.comparingLong(ChunkPos::toLong));

  private FaunaEncounterRegression() {}

  private static ChunkPos patch(Vec3 observer) {
    return new ChunkPos(BlockPos.containing(observer.add(0, 0, 48)));
  }

  public static void warm(ServerLevel level, Vec3 observer) {
    ChunkPos center = patch(observer);
    level.getChunkSource().addRegionTicket(TICKET, center, 4, center);
  }

  public static boolean ready(ServerLevel level, Vec3 observer) {
    ChunkPos center = patch(observer);
    for (int x = center.x - 2; x <= center.x + 2; x++) {
      for (int z = center.z - 2; z <= center.z + 2; z++) {
        var chunk = new ChunkPos(x, z);
        if (!level.isNaturalSpawningAllowed(chunk) || !level.areEntitiesLoaded(chunk.toLong()))
          return false;
      }
    }
    return true;
  }

  public static void verify(ServerLevel level, Vec3 observer) {
    AABB neighborhood = new AABB(observer, observer).inflate(128);
    require(empty(level, neighborhood), "Fixture sky is unused");
    var spawning = level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);
    boolean originalSpawning = spawning.get();
    var clock = level.getServer().getWorldData().overworldData();
    long originalTime = level.getGameTime();
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("ec385291-e25f-445f-bb98-d586dd5a2384"),
            "SkyFauna",
            observer,
            null)) {
      session.player.setGameMode(GameType.SURVIVAL);
      session.player.setYRot(0);
      clock.setGameTime(300);
      spawning.set(false, level.getServer());
      opportunities(level);
      require(empty(level, neighborhood), "Disabled mob spawning stops ordinary encounters");
      spawning.set(true, level.getServer());
      session.player.setGameMode(GameType.SPECTATOR);
      opportunities(level);
      require(empty(level, neighborhood), "Spectators do not trigger encounters");
      session.player.setGameMode(GameType.SURVIVAL);
      clock.setGameTime(301);
      opportunities(level);
      require(empty(level, neighborhood), "Encounters wait for their scheduled opportunity");
      clock.setGameTime(300);
      opportunities(level);
      var rays = level.getEntitiesOfClass(SkyRay.class, neighborhood);
      var adult = rays.stream().filter(ray -> !ray.isCalf()).findFirst().orElseThrow();
      var jellies = level.getEntitiesOfClass(CloudJelly.class, neighborhood);
      require(
          rays.size() <= 2
              && rays.stream().filter(ray -> !ray.isCalf()).count() == 1
              && adult.getSpawnType() == MobSpawnType.NATURAL,
          "Repeated opportunities yield one adult ray with at most one calf");
      verifyColony(jellies);
      require(
          adult.getZ() > observer.z
              && jellies.stream().allMatch(jelly -> jelly.getZ() > observer.z),
          "The actual encounter is ahead of the observer");
      require(
          session.player.requestedViewDistance() == 2
              && adult.position().subtract(observer).horizontalDistance() <= 30,
          "Minimum client view distance brings encounters into the visible sky");
      clear(level, neighborhood);

      // Fixed natural rolls in the same forward patch exercise every larger bell band.
      var options = session.player.clientInformation();
      session.player.updateOptions(
          new ClientInformation(
              options.language(),
              8,
              options.chatVisibility(),
              options.chatColors(),
              options.modelCustomisation(),
              options.mainHand(),
              options.textFilteringEnabled(),
              options.allowsListing()));
      verifyFormData(level);
      verifySize(level, session.player, neighborhood, 14369, 3, 5);
      verifySize(level, session.player, neighborhood, 14403, 6, 9);
      verifySize(level, session.player, neighborhood, 15025, 12, 16);
      SkyRayFlightRegression.verifyFamily(level, session.player, neighborhood);
      verifyChunkBoundary(level, BlockPos.containing(observer.add(0, 0, 48)));
      verifySidewaysWall(level, observer.add(0, 0, 48));
    } finally {
      clear(level, neighborhood);
      spawning.set(originalSpawning, level.getServer());
      clock.setGameTime(originalTime);
      var center = patch(observer);
      level.getChunkSource().removeRegionTicket(TICKET, center, 4, center);
    }
  }

  private static void opportunities(ServerLevel level) {
    level.getRandom().setSeed(917);
    for (int index = 0; index < 96; index++)
      FaunaEncounters.tick(new LevelTickEvent.Post(() -> true, level));
  }

  private static void verifyColony(List<CloudJelly> jellies) {
    require(jellies.size() >= 2 && jellies.size() <= 5, "Only one bounded colony appears");
    require(
        jellies.stream().filter(jelly -> jelly.getBbWidth() >= 3).count() == 1
            && jellies.stream().filter(jelly -> jelly.getBbWidth() < 2).count()
                == jellies.size() - 1,
        "The colony has small and larger bells");
    for (var jelly : jellies) {
      require(
          jelly.getSpawnType() == MobSpawnType.NATURAL && !jelly.isPersistenceRequired(),
          "Colony members retain native natural spawn and cleanup");
      require(
          jelly.getYRot() == jellies.getFirst().getYRot(), "The colony begins drifting together");
      for (var other : jellies)
        if (other != jelly)
          require(
              !jelly.getBoundingBox().intersects(other.getBoundingBox()),
              "Mixed-size colony bodies clear one another");
    }
  }

  private static void verifySize(
      ServerLevel level, ServerPlayer observer, AABB neighborhood, int seed, int low, int high) {
    int count = FaunaEncounters.spawnNear(level, observer, RandomSource.create(seed), true);
    var jellies = level.getEntitiesOfClass(CloudJelly.class, neighborhood);
    require(count == jellies.size(), "Reported placement count matches the actual entities");
    verifyColony(jellies);
    var large =
        jellies.stream()
            .filter(jelly -> jelly.getBbWidth() >= low && jelly.getBbWidth() <= high)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Missing natural bell band " + low + ".." + high));
    var saved = large.saveWithoutId(new CompoundTag());
    var restored = AeroFauna.CLOUD_JELLY.get().create(level);
    require(restored != null, "Create native save reader");
    restored.load(saved);
    require(
        restored.getBoundingBox().equals(large.getBoundingBox())
            && restored.position().equals(large.position()),
        "Saved size, position and body bounds reload together");
    var bounds = large.clearanceBounds().minmax(large.getBoundingBox());
    if (low == 12) {
      require(
          jellies.stream().map(CloudJelly::form).distinct().count() >= 2,
          "One natural colony combines different anatomies");
      var fixed = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
      fixed.load(saved);
      fixed.setUUID(UUID.fromString("ecb46941-74f3-4c2b-a842-f097f06a194e"));
      fixed.setDriftHeading(48);
      require(level.addFreshEntity(fixed), "Place repeatable tracked giant for native strikes");
      try {
        verifyGiantTargets(level, observer, fixed);
      } finally {
        fixed.discard();
      }
    }
    clear(level, neighborhood);
    if (low == 12) {
      require(FaunaEncounters.clear(level, bounds), "The natural giant cleared its full body");
      var corner = BlockPos.containing(bounds.maxX - 0.25, bounds.minY + 0.25, bounds.maxZ - 0.25);
      var original = level.getBlockState(corner);
      try {
        level.setBlockAndUpdate(corner, Blocks.STONE.defaultBlockState());
        require(
            !FaunaEncounters.clear(level, bounds),
            "A solid block at the far lower corner excludes the giant");
      } finally {
        level.setBlockAndUpdate(corner, original);
      }
    }
  }

  private static void verifySidewaysWall(ServerLevel level, Vec3 feet) {
    var jelly = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
    jelly.setUUID(UUID.fromString("a3865e33-c31e-455a-ad94-72551ee8ea70"));
    jelly.setForm(CloudJellyAnatomy.Form.NETTLE);
    jelly.initSize(16);
    jelly.setDriftHeading(43);
    jelly.setPos(feet);
    var clock = level.getServer().getWorldData().overworldData();
    long start = level.getGameTime();
    long limit = start + 6000;
    while (start < limit
        && (jelly.swimFrame(start).basis.y.y > .23
            || Math.abs(jelly.swimFrame(start).basis.y.x) < .48)) start++;
    require(start < limit, "Fixture reaches natural sideways orientation");
    clock.setGameTime(start);
    require(level.addFreshEntity(jelly), "Place tilted giant near a cliff");
    var pose = jelly.swimFrame(start);
    Vec3 crown = feet.add(0, jelly.getBbHeight(), 0), distal = crown;
    for (int i = 0; i < pose.shape().strands().size(); i++) {
      var tip = pose.segment(i, pose.shape().strands().get(i).segments().size() - 1).getCenter();
      Vec3 point = crown.add(pose.basis.apply(new Vec3(tip.x, -tip.y, tip.z)));
      if (Math.abs(point.x - crown.x) > Math.abs(distal.x - crown.x)) distal = point;
    }
    int side = distal.x > crown.x ? 1 : -1;
    var body = jelly.clearanceBounds();
    int wallX = side > 0 ? (int) Math.ceil(body.maxX) : (int) Math.floor(body.minX) - 1;
    var corner = BlockPos.containing(wallX, distal.y - 1, distal.z - 1);
    var old = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
    try {
      for (int iy = 0; iy < 3; iy++)
        for (int iz = 0; iz < 3; iz++) {
          var cell = corner.offset(0, iy, iz);
          old.put(cell, level.getBlockState(cell));
          level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
        }
      var wall =
          new AABB(Vec3.atLowerCornerOf(corner), Vec3.atLowerCornerOf(corner.offset(1, 3, 3)));
      require(
          !jelly.getBoundingBox().inflate(4).intersects(wall),
          "Cliff is beyond native upright collision");
      jelly.setDeltaMovement(side * .35, 0, 0);
      for (int t = 0; t < 40; t++) {
        clock.setGameTime(start + t);
        jelly.tick();
        require(
            !jelly.clearanceBounds().intersects(wall),
            "Turning giant clears cliff with outstretched strands");
      }
      require(
          jelly.saveWithoutId(new CompoundTag()).contains("DriftTurnStart"),
          "Obstacle provokes a gradual course change");
    } finally {
      jelly.discard();
      old.forEach(level::setBlockAndUpdate);
    }
  }

  private static void verifyGiantTargets(
      ServerLevel level, ServerPlayer observer, CloudJelly giant) {
    int boundary = (Math.floorDiv(giant.getBlockY(), 16) + 1) * 16;
    for (var form : CloudJellyAnatomy.Form.values()) {
      giant.setForm(form);
      var shape = CloudJellyAnatomy.of(form);
      double unit = giant.getBbWidth() / CloudJellyAnatomy.SIZE_UNITS;
      for (int feetY : new int[] {boundary - 2, boundary + 1}) {
        giant.setPos(giant.getX(), feetY, giant.getZ());
        giant.setDeltaMovement(Vec3.ZERO);
        giant.tick();
        Vec3 crown = giant.position().add(0, CloudJellyAnatomy.of(giant.form()).height() * unit, 0);
        var frame = giant.swimFrame(level.getGameTime());
        Vec3 above = crown.add(frame.basis.y.scale(2));
        Vec3 below = crown.subtract(frame.basis.y.scale(.5));
        var hit =
            ProjectileUtil.getEntityHitResult(
                observer,
                above,
                below,
                new AABB(above, below).inflate(0.5),
                entity -> entity.is(giant) && entity.isPickable(),
                above.distanceToSqr(below));
        require(
            hit != null
                && hit.getEntity() instanceof PartEntity<?> part
                && part.getParent() == giant,
            form + " visible crown remains pickable across vertical sections");
        if (feetY > boundary) {
          if (form == CloudJellyAnatomy.Form.FRINGE) {
            float before = giant.getHealth();
            require(
                hit.getEntity().hurt(level.damageSources().playerAttack(observer), 1)
                    && giant.getHealth() < before,
                "A native crown hit reaches the giant's health");
          }
          for (int i = 0; i < shape.strands().size(); i++) {
            var tip = frame.segment(i, shape.strands().get(i).segments().size() - 1);
            Vec3 local = tip.getCenter();
            Vec3 skin =
                crown.add(frame.basis.apply(new Vec3(local.x, -tip.maxY, local.z).scale(unit)));
            Vec3 from = skin.subtract(frame.basis.y.scale(1));
            Vec3 to = skin.add(frame.basis.y.scale(.5));
            Entity strand = giant.getParts()[i + 1];
            var surface = strand.getBoundingBox().clip(from, to);
            require(
                surface.isPresent() && surface.get().distanceToSqr(skin) < 1e-8,
                form + " exact terminal face " + i);
            require(
                ProjectileUtil.getEntityHitResult(
                        level,
                        observer,
                        from,
                        to,
                        new AABB(from, to).inflate(0.5),
                        entity -> entity == strand && entity.canBeHitByProjectile())
                    != null,
                form + " projectile hits strand tip " + i);
          }
          verifyGaps(observer, giant, frame, unit);
        }
      }
    }
  }

  private static void verifyGaps(
      ServerPlayer observer, CloudJelly giant, CloudJellyMotion frame, double unit) {
    int hits = 0;
    int openings = 0;
    for (double modelY : new double[] {1, 7, 13}) {
      for (double modelZ = -7.5; modelZ <= 7.5; modelZ += 0.5) {
        double y = (modelY - frame.shape().height()) * unit, z = modelZ * unit;
        Vec3 crown = giant.position().add(0, CloudJellyAnatomy.of(giant.form()).height() * unit, 0);
        Vec3 center = crown.add(frame.basis.apply(new Vec3(0, y, z)));
        Vec3 delta = frame.basis.x.scale(giant.getBbWidth());
        Vec3 left = center.subtract(delta), right = center.add(delta);
        double clearance = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < frame.shape().strands().size(); i++) {
          for (int j = 0; j < frame.shape().strands().get(i).segments().size(); j++) {
            var segment = frame.segment(i, j);
            clearance =
                Math.max(
                    clearance,
                    Math.min(
                        Math.min(y + segment.maxY * unit, -segment.minY * unit - y),
                        Math.min(z - segment.minZ * unit, segment.maxZ * unit - z)));
          }
        }
        // Native slab clipping accepts coplanar rays within its numerical tolerance. The tip
        // face is independently crossed above; compare interiors and gaps on either side here.
        if (Math.abs(clearance) < 1e-5) continue;
        boolean strandAtShot = clearance > 0;
        boolean hit =
            ProjectileUtil.getEntityHitResult(
                    observer,
                    left,
                    right,
                    new AABB(left, right).inflate(.5),
                    e -> e.is(giant) && e.canBeHitByProjectile(),
                    left.distanceToSqr(right))
                != null;
        require(
            hit == strandAtShot,
            giant.form()
                + " wrong native projectile target at height "
                + y
                + " and z "
                + z
                + " (clearance "
                + clearance
                + ", actual "
                + hit
                + ")");
        if (hit) hits++;
        else openings++;
      }
    }
    require(
        hits > 0 && openings > 0, giant.form() + " has tangible strands and clear flight paths");
  }

  private static void verifyFormData(ServerLevel level) {
    var source = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
    var follower = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
    int[] ids = Arrays.stream(follower.getParts()).mapToInt(Entity::getId).toArray();
    for (var form : CloudJellyAnatomy.Form.values()) {
      source.setForm(form);
      source.initSize(12.75F);
      var saved = source.saveWithoutId(new CompoundTag());
      require(saved.getString("JellyForm").equals(form.id()), "Stable saved jelly form " + form);
      var restored = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
      restored.load(saved);
      checkShape(restored, form);
      long now = level.getGameTime();
      var expected = source.swimFrame(now);
      require(
          expected.basis.y.distanceToSqr(restored.swimFrame(now).basis.y) < 1e-10
              && expected.phase() == restored.swimFrame(now).phase(),
          "Reload preserves swimming direction and stroke phase");
      var packet = Objects.requireNonNull(source.getEntityData().getNonDefaultValues());
      for (var values : List.of(packet, packet.reversed())) {
        var lateTracker = Objects.requireNonNull(AeroFauna.CLOUD_JELLY.get().create(level));
        lateTracker.setUUID(source.getUUID());
        lateTracker.getEntityData().assignValues(values);
        require(
            expected.basis.y.distanceToSqr(lateTracker.swimFrame(now).basis.y) < 1e-10
                && expected.phase() == lateTracker.swimFrame(now).phase(),
            "Late tracker shares the moving body frame");
        checkShape(lateTracker, form);
      }
      follower
          .getEntityData()
          .assignValues(Objects.requireNonNull(source.getEntityData().packDirty()));
      checkShape(follower, form);
      require(
          Arrays.equals(ids, Arrays.stream(follower.getParts()).mapToInt(Entity::getId).toArray()),
          "Changing form preserved native part identities");
    }
    source.setForm(CloudJellyAnatomy.Form.FRINGE);
    follower
        .getEntityData()
        .assignValues(Objects.requireNonNull(source.getEntityData().packDirty()));
    checkShape(follower, CloudJellyAnatomy.Form.FRINGE);
    var legacy = source.saveWithoutId(new CompoundTag());
    legacy.remove("JellyForm");
    follower.load(legacy);
    checkShape(follower, CloudJellyAnatomy.Form.FRINGE);
    legacy.putString("JellyForm", "unknown");
    follower.setForm(CloudJellyAnatomy.Form.LANTERN);
    follower.load(legacy);
    checkShape(follower, CloudJellyAnatomy.Form.FRINGE);
  }

  private static void checkShape(CloudJelly jelly, CloudJellyAnatomy.Form expected) {
    var shape = CloudJellyAnatomy.of(expected);
    require(
        jelly.form() == expected
            && jelly.bellDiameter() == 12.75F
            && Math.abs(jelly.getBbHeight() - shape.height() * 12.75 / CloudJellyAnatomy.SIZE_UNITS)
                < 0.0001,
        "Synced form, diameter and body height " + expected);
    for (int i = 0; i < jelly.getParts().length; i++) {
      var part = jelly.getParts()[i];
      boolean active = i <= shape.strands().size();
      require(
          part.isPickable() == active && part.canBeHitByProjectile() == active,
          "Synced active target count " + expected);
      if (!active) require(part.getBoundingBox().getSize() == 0, "Dormant slot has no volume");
    }
  }

  private static void verifyChunkBoundary(ServerLevel level, BlockPos patch) {
    int z = patch.getZ() >> 4;
    int firstMissing = patch.getX() >> 4;
    int limit = firstMissing + 256;
    while (firstMissing < limit && level.getChunkSource().getChunkNow(firstMissing, z) != null)
      firstMissing++;
    require(firstMissing < limit, "Found a loaded-to-unloaded chunk boundary");
    double edge = firstMissing * 16.0;
    var giant =
        new AABB(
            edge - 10,
            patch.getY(),
            patch.getZ() - 8,
            edge + 6,
            patch.getY()
                + Arrays.stream(CloudJellyAnatomy.Form.values())
                    .mapToDouble(form -> CloudJellyAnatomy.of(form).height())
                    .max()
                    .orElseThrow(),
            patch.getZ() + 8);
    require(
        level.isLoaded(BlockPos.containing(giant.getCenter()))
            && !FaunaEncounters.clear(level, giant)
            && level.getChunkSource().getChunkNow(firstMissing, z) == null,
        "A giant centered in loaded sky cannot spill into or request a missing chunk");
  }

  private static boolean empty(ServerLevel level, AABB bounds) {
    return level.getEntitiesOfClass(CloudJelly.class, bounds).isEmpty()
        && level.getEntitiesOfClass(SkyRay.class, bounds).isEmpty();
  }

  private static void clear(ServerLevel level, AABB bounds) {
    level.getEntitiesOfClass(CloudJelly.class, bounds).forEach(Entity::discard);
    level.getEntitiesOfClass(SkyRay.class, bounds).forEach(Entity::discard);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
