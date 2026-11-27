package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.combat.BeastmasterState.Group;
import dev.aeroscapes.equipment.combat.BeastmasterState.Member;
import dev.aeroscapes.equipment.combat.BeastmasterState.Species;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.ClassProgression;
import dev.aeroscapes.races.RaceIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class BeastmasterCompanions {
  private static final String OWNER = "aeroscapes_reimagine:beastmaster_owner";
  private static final String PERSISTENCE = "beastmaster_persistence";
  private static final String COOLDOWN = "beastmaster_cooldown";
  private static final String RECALL = "beastmaster_recall";
  private static final String FOCUS = "beastmaster_focus_attack";
  private static final int TEMPORARY_TICKS = 60 * 20;
  private static final int BASE_COOLDOWN_TICKS = 150 * 20;
  private static final int TRAINED_COOLDOWN_TICKS = 120 * 20;
  private static final int FOCUS_RANGE = 24;
  private static final int FOLLOW_RANGE = 32;
  private static final int COMMAND_TICKS = 100;
  private static final int ACTION_CHECK_TICKS = 10;
  private static final int[] LANDING_HEIGHTS = {0, 1, -1, 2, -2};
  private static final Set<Mob> SPAWNING = new HashSet<>();
  private static final WeakHashMap<Mob, Command> COMMANDS = new WeakHashMap<>();

  private record Command(@Nullable LivingEntity target, long until) {}

  private record Placement(PathfinderMob mob, Member member) {}

  private record Arrival(List<Member> members, String reason) {
    static Arrival refused(String reason) {
      return new Arrival(List.of(), reason);
    }
  }

  private BeastmasterCompanions() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::join);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::leave);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::tickPlayer);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::tickCompanion);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BeastmasterCompanions::death);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::incoming);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::damaged);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::protectDrops);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::protectExperience);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::interact);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::interactSpecific);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::grief);
    NeoForge.EVENT_BUS.addListener(BeastmasterCompanions::changeDimension);
  }

  public static boolean learned(ServerPlayer player, String node) {
    return ClassProgression.hasCombatTalent(player, node);
  }

  public static int cooldownTicks(ServerPlayer player, String node) {
    if (Species.byNode(node) == null) return 0;
    var server = player.server;
    long remaining =
        BeastmasterState.get(server).owner(player.getUUID()).cooldownUntil() - now(server);
    return Math.clamp(remaining, 0, Integer.MAX_VALUE);
  }

  public static String status(ServerPlayer player, String node) {
    if (Species.byNode(node) == null) return "";
    var group = currentGroup(player);
    if (group == null) return "";
    if (group.destination() != null) return "beastmaster_pending";
    return group.persistent() ? "beastmaster_persistent" : "beastmaster_temporary";
  }

  public static String reason(ServerPlayer player, String node) {
    if (!learned(player, node)) return "beastmaster_unlearned";
    if (player.isCreative() || player.isSpectator()) return "beastmaster_mode";
    if (!PracticeActions.personal(player) || !player.isAlive() || player.isSleeping())
      return "beastmaster_inactive";
    var species = Species.byNode(node);
    if (species != null) {
      if (cooldownTicks(player, node) > 0) return "cooldown";
      return BuiltInRegistries.ENTITY_TYPE.containsKey(species.entityType)
          ? ""
          : "beastmaster_species_unavailable";
    }
    if (!node.equals(RECALL) && !node.equals(FOCUS)) return "beastmaster_unavailable";
    var group = currentGroup(player);
    if (group != null && group.destination() != null) return "beastmaster_pending";
    if (group == null || localMembers(player, group).isEmpty()) return "beastmaster_no_group";
    return node.equals(FOCUS) && focusTarget(player) == null ? "beastmaster_no_target" : "";
  }

  public static boolean activate(ServerPlayer player, String node) {
    reconcile(player);
    String unavailable = reason(player, node);
    if (!unavailable.isEmpty()) {
      SpellKnowledge.message(player, unavailable);
      return false;
    }
    var species = Species.byNode(node);
    if (species != null) {
      String failure = summon(player, species);
      if (failure.isEmpty()) return true;
      SpellKnowledge.message(player, failure);
      return false;
    }
    var group = currentGroup(player);
    if (group == null || group.destination() != null) return false;
    var target = node.equals(FOCUS) ? focusTarget(player) : null;
    if (node.equals(FOCUS) && target == null) return false;
    for (var mob : localMembers(player, group)) {
      COMMANDS.put(mob, new Command(target, now(player.server) + COMMAND_TICKS));
      if (target == null) recall(mob, player);
      else mob.setTarget(target);
    }
    return true;
  }

  public static int groupCount(ServerPlayer player) {
    var group = currentGroup(player);
    return group == null ? 0 : group.members().size();
  }

  public static boolean pending(ServerPlayer player) {
    var group = currentGroup(player);
    return group != null && group.destination() != null;
  }

  public static boolean isCompanionOf(Entity entity, ServerPlayer player) {
    return player.getUUID().equals(ownerId(entity)) && companionIdentity(entity) != null;
  }

  @Nullable
  public static UUID companionIdentity(Entity entity) {
    if (!(entity.level() instanceof ServerLevel level)) return null;
    var group = activeGroup(entity, level);
    var member = group == null ? null : group.member(entity.getUUID());
    return member == null ? null : member.identity();
  }

  private static String summon(ServerPlayer player, Species species) {
    long now = now(player.server);
    int count = learned(player, species.node + "_count") ? species.limit : 1;
    var members = new ArrayList<Member>(count);
    for (int slot = 0; slot < count; slot++) {
      UUID identity = UUID.randomUUID();
      members.add(
          new Member(identity, identity, player.level().dimension().location(), species.health));
    }
    var fresh =
        new Group(
            species,
            learned(player, PERSISTENCE) ? 0 : now + TEMPORARY_TICKS,
            learned(player, species.node + "_damage"),
            learned(player, species.node + "_protection"),
            null,
            members);
    var placed = place(player, fresh);
    if (!placed.reason().isEmpty()) return placed.reason();
    var state = BeastmasterState.get(player.server);
    var previous = state.owner(player.getUUID()).group();
    int delay = learned(player, COOLDOWN) ? TRAINED_COOLDOWN_TICKS : BASE_COOLDOWN_TICKS;
    state.put(player.getUUID(), now + delay, fresh.withMembers(placed.members()));
    discard(player.server, previous);
    return "";
  }

  /** Called only after a successful paid Waystone move has settled. */
  public static void waystoneArrived(ServerPlayer player) {
    reconcile(player);
    var state = BeastmasterState.get(player.server);
    var owner = state.owner(player.getUUID());
    var previous = owner.group();
    if (previous == null || !previous.persistent()) return;
    var destination = player.level().dimension().location();
    var survivors = new ArrayList<Member>(previous.members().size());
    for (var member : previous.members()) {
      var entity = loaded(player.server, member);
      if (entity == null) survivors.add(member);
      else if (entity instanceof LivingEntity living && living.isAlive())
        survivors.add(member.withHealth(living.getHealth()));
    }
    var pending =
        survivors.isEmpty()
            ? null
            : new Group(
                previous.species(),
                0,
                previous.stronger(),
                previous.protectedGroup(),
                destination,
                survivors);
    state.put(player.getUUID(), owner.cooldownUntil(), pending);
    discard(player.server, previous);
    retryArrival(player);
  }

  private static void retryArrival(ServerPlayer player) {
    var state = BeastmasterState.get(player.server);
    var owner = state.owner(player.getUUID());
    var group = owner.group();
    if (group == null || !player.level().dimension().location().equals(group.destination())) return;
    var landed = place(player, group);
    if (landed.reason().isEmpty()) {
      var arrived =
          new Group(
              group.species(), 0, group.stronger(), group.protectedGroup(), null, landed.members());
      state.put(player.getUUID(), owner.cooldownUntil(), arrived);
    }
  }

  public static void reconcile(ServerPlayer player) {
    var state = BeastmasterState.get(player.server);
    var owner = state.owner(player.getUUID());
    var group = owner.group();
    if (group == null) {
      if (owner.cooldownUntil() <= now(player.server)) state.put(player.getUUID(), 0, null);
      return;
    }
    if (expired(group, player.server)) {
      dismiss(player);
      return;
    }
    if (!ProgressionBridge.categoriesAvailable()) return;
    if (!learned(player, group.species().node)
        || group.persistent() && !learned(player, PERSISTENCE)) {
      dismiss(player);
      return;
    }

    boolean stronger = learned(player, group.species().node + "_damage");
    boolean protection = learned(player, group.species().node + "_protection");
    if (stronger != group.stronger() || protection != group.protectedGroup()) {
      var updated =
          new Group(
              group.species(),
              group.expiresAt(),
              stronger,
              protection,
              group.destination(),
              group.members());
      state.put(player.getUUID(), owner.cooldownUntil(), updated);
      for (var member : group.members()) {
        if (loaded(player.server, member) instanceof Mob mob) setDamage(mob, updated);
      }
    }
  }

  private static void dismiss(ServerPlayer player) {
    var state = BeastmasterState.get(player.server);
    var owner = state.owner(player.getUUID());
    state.put(player.getUUID(), owner.cooldownUntil(), null);
    discard(player.server, owner.group());
  }

  private static void discard(MinecraftServer server, @Nullable Group group) {
    if (group == null) return;
    for (var member : group.members()) {
      var entity = loaded(server, member);
      if (entity != null) entity.discard();
    }
  }

  private static Arrival place(ServerPlayer player, Group group) {
    var level = player.serverLevel();
    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(group.species().entityType).orElse(null);
    if (type == null) return Arrival.refused("beastmaster_species_unavailable");
    var placements = new ArrayList<Placement>(group.members().size());
    var occupied = new ArrayList<AABB>(group.members().size());
    for (var member : group.members()) {
      if (!(type.create(level) instanceof PathfinderMob mob))
        return Arrival.refused("beastmaster_species_unavailable");
      if (!placeNear(player, mob, occupied)) return Arrival.refused("beastmaster_no_space");
      mob.getPersistentData().putUUID(OWNER, player.getUUID());
      configure(mob, group, player.getUUID(), member.health());
      occupied.add(mob.getBoundingBox());
      placements.add(
          new Placement(
              mob,
              new Member(
                  member.identity(),
                  mob.getUUID(),
                  level.dimension().location(),
                  mob.getHealth())));
    }

    // A member can join only its committed roster. Reserve these entities during the atomic
    // attempt.
    boolean complete = false;
    try {
      for (var placement : placements) {
        var mob = placement.mob();
        SPAWNING.add(mob);
        try {
          if (!level.addFreshEntity(mob) || mob.isRemoved())
            return Arrival.refused("beastmaster_spawn_refused");
        } finally {
          SPAWNING.remove(mob);
        }
      }
      complete = true;
      return new Arrival(placements.stream().map(Placement::member).toList(), "");
    } finally {
      if (!complete) {
        for (var placement : placements) {
          if (placement.mob().isAddedToLevel()) placement.mob().discard();
        }
      }
    }
  }

  private static boolean placeNear(ServerPlayer player, PathfinderMob mob, List<AABB> occupied) {
    var level = player.serverLevel();
    var origin = player.blockPosition();
    for (int radius = 1; radius <= 3; radius++) {
      for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
          if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
          for (int height : LANDING_HEIGHTS) {
            var position = origin.offset(x, height, z);
            if (!level.isLoaded(position)) continue;
            mob.moveTo(Vec3.atBottomCenterOf(position), player.getYRot(), 0);
            AABB bounds = mob.getBoundingBox();
            if (!bounds.intersects(player.getBoundingBox().inflate(0.1))
                && occupied.stream().noneMatch(bounds::intersects)
                && level.getWorldBorder().isWithinBounds(bounds)
                && level.noCollision(mob, bounds)
                && !level.containsAnyLiquid(bounds)
                && safeFooting(level, position.getY(), bounds)) return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean safeFooting(ServerLevel level, int y, AABB bounds) {
    for (int x = (int) Math.floor(bounds.minX); x <= (int) Math.floor(bounds.maxX - 1.0E-5); x++) {
      for (int z = (int) Math.floor(bounds.minZ);
          z <= (int) Math.floor(bounds.maxZ - 1.0E-5);
          z++) {
        var floor = new BlockPos(x, y - 1, z);
        if (!level.isLoaded(floor)) return false;
        var surface = level.getBlockState(floor);
        if (!surface.isFaceSturdy(level, floor, Direction.UP)
            || surface.is(Blocks.MAGMA_BLOCK)
            || surface.is(Blocks.CAMPFIRE)
            || surface.is(Blocks.SOUL_CAMPFIRE)) return false;
      }
    }
    return true;
  }

  private static void configure(PathfinderMob mob, Group group, UUID owner, float health) {
    if (mob instanceof Wolf wolf) {
      wolf.setOwnerUUID(owner);
      wolf.setTame(true, false);
      wolf.setOrderedToSit(false);
      wolf.setInSittingPose(false);
      wolf.setPersistentAngerTarget(null);
    }
    if (mob instanceof Fox fox) fox.setSitting(false);
    var healthAttribute = mob.getAttribute(Attributes.MAX_HEALTH);
    if (healthAttribute != null) healthAttribute.setBaseValue(group.species().health);
    mob.setHealth(Math.min(mob.getMaxHealth(), health));
    setDamage(mob, group);
    mob.setCanPickUpLoot(false);
    mob.setPersistenceRequired();
    mob.removeAllGoals(goal -> true);
    mob.targetSelector.removeAllGoals(goal -> true);
    mob.goalSelector.addGoal(0, new FloatGoal(mob));
    mob.goalSelector.addGoal(1, new MeleeAttackGoal(mob, 1.2, true));
  }

  private static void setDamage(Mob mob, Group group) {
    var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
    if (attack != null)
      attack.setBaseValue(group.species().damage * (group.stronger() ? 1.5 : 1.0));
  }

  private static void tickPlayer(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player
        && player.isAlive()
        && player.tickCount % ACTION_CHECK_TICKS == 0) {
      reconcile(player);
      retryArrival(player);
    }
  }

  private static void tickCompanion(EntityTickEvent.Pre event) {
    if (!(event.getEntity() instanceof PathfinderMob mob)
        || !(mob.level() instanceof ServerLevel level)) return;
    var id = ownerId(mob);
    if (id == null || SPAWNING.contains(mob)) return;
    var group = activeGroup(mob, level);
    if (group == null) {
      mob.discard();
      event.setCanceled(true);
      return;
    }
    if (mob.tickCount % ACTION_CHECK_TICKS != 0) return;
    updateHealth(mob, level, group);
    var owner = level.getServer().getPlayerList().getPlayer(id);
    if (owner == null || !owner.isAlive() || owner.level() != level) {
      mob.setTarget(null);
      mob.getNavigation().stop();
      return;
    }
    control(mob, owner);
  }

  private static void control(PathfinderMob mob, ServerPlayer owner) {
    var command = COMMANDS.get(mob);
    long now = now(owner.server);
    if (command != null && command.until() > now) {
      var target = command.target();
      if (target == null) {
        recall(mob, owner);
        return;
      }
      if (hostile(owner, target) && owner.distanceToSqr(target) <= FOCUS_RANGE * FOCUS_RANGE) {
        mob.setTarget(target);
        return;
      }
    }
    COMMANDS.remove(mob);
    var attacking = owner.getLastHurtMob();
    boolean recentlyAttacked = owner.tickCount - owner.getLastHurtMobTimestamp() <= COMMAND_TICKS;
    if (recentlyAttacked
        && safeTarget(owner, attacking)
        && owner.distanceToSqr(attacking) <= 16 * 16) {
      mob.setTarget(attacking);
      return;
    }
    var attacker = owner.getLastHurtByMob();
    if (hostile(owner, attacker) && owner.distanceToSqr(attacker) <= 16 * 16) {
      mob.setTarget(attacker);
      return;
    }
    var target = mob.getTarget();
    if (hostile(owner, target) && owner.distanceToSqr(target) <= 16 * 16) return;
    if (mob.distanceToSqr(owner) <= 16 * 16) {
      var nearby =
          mob.level()
              .getEntitiesOfClass(
                  LivingEntity.class,
                  mob.getBoundingBox().inflate(10),
                  candidate -> hostile(owner, candidate) && mob.hasLineOfSight(candidate));
      target = nearby.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
      if (target != null) {
        mob.setTarget(target);
        return;
      }
    }
    recall(mob, owner);
  }

  private static void recall(PathfinderMob mob, ServerPlayer owner) {
    mob.setTarget(null);
    double distance = mob.distanceToSqr(owner);
    if (distance > 3 * 3 && distance <= FOLLOW_RANGE * FOLLOW_RANGE) {
      mob.getNavigation().moveTo(owner, 1.2);
    } else {
      mob.getNavigation().stop();
    }
  }

  @Nullable
  private static LivingEntity focusTarget(ServerPlayer owner) {
    var hit = ProjectileUtil.getHitResultOnViewVector(owner, Entity::isPickable, FOCUS_RANGE);
    if (hit instanceof EntityHitResult entityHit
        && entityHit.getEntity() instanceof LivingEntity target
        && hostile(owner, target)) return target;
    return null;
  }

  private static boolean hostile(ServerPlayer owner, @Nullable LivingEntity target) {
    return target instanceof Enemy && safeTarget(owner, target);
  }

  private static boolean safeTarget(ServerPlayer owner, @Nullable LivingEntity target) {
    return target != null
        && target.isAlive()
        && target.level() == owner.level()
        && !(target instanceof Player)
        && !(target instanceof TamableAnimal tamable && tamable.isTame())
        && ownerId(target) == null
        && !owner.isAlliedTo(target);
  }

  private static List<PathfinderMob> localMembers(ServerPlayer owner, Group group) {
    var result = new ArrayList<PathfinderMob>(group.members().size());
    for (var member : group.members()) {
      if (loaded(owner.server, member) instanceof PathfinderMob mob
          && mob.isAlive()
          && mob.level() == owner.level()
          && mob.distanceToSqr(owner) <= FOLLOW_RANGE * FOLLOW_RANGE) result.add(mob);
    }
    return result;
  }

  @Nullable
  private static Entity loaded(MinecraftServer server, Member member) {
    var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, member.dimension()));
    return level == null ? null : level.getEntity(member.entityId());
  }

  @Nullable
  private static Group currentGroup(ServerPlayer owner) {
    var group = BeastmasterState.get(owner.server).owner(owner.getUUID()).group();
    return group == null || expired(group, owner.server) ? null : group;
  }

  @Nullable
  private static Group activeGroup(Entity entity, ServerLevel level) {
    var id = ownerId(entity);
    if (id == null) return null;
    var server = level.getServer();
    var group = BeastmasterState.get(server).owner(id).group();
    if (group == null || group.destination() != null || expired(group, server)) return null;
    var member = group.member(entity.getUUID());
    return member != null && member.dimension().equals(level.dimension().location()) ? group : null;
  }

  private static boolean expired(Group group, MinecraftServer server) {
    return !group.persistent() && group.expiresAt() <= now(server);
  }

  private static long now(MinecraftServer server) {
    return server.overworld().getGameTime();
  }

  @Nullable
  private static UUID ownerId(Entity entity) {
    if (!(entity instanceof Fox)
        && !(entity instanceof Wolf)
        && !Species.BEAR.entityType.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())))
      return null;
    var data = entity.getPersistentData();
    return data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
  }

  private static void join(EntityJoinLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || !(event.getEntity() instanceof PathfinderMob mob)
        || SPAWNING.contains(mob)) return;
    var owner = ownerId(mob);
    if (owner == null || level.getServer().overworld() == null) return;
    var group = activeGroup(mob, level);
    var member = group == null ? null : group.member(mob.getUUID());
    if (member == null || !mob.isAlive()) event.setCanceled(true);
    else configure(mob, group, owner, Math.min(mob.getHealth(), member.health()));
  }

  private static void leave(EntityLeaveLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || !(event.getEntity() instanceof PathfinderMob mob)
        || level.getServer().overworld() == null) return;
    var group = activeGroup(mob, level);
    if (group == null) return;
    if (mob.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && mob.getHealth() > 0)
      updateHealth(mob, level, group);
    else if (mob.getRemovalReason() != Entity.RemovalReason.UNLOADED_WITH_PLAYER)
      removeMember(mob, level, group);
  }

  private static void updateHealth(Mob mob, ServerLevel level, Group group) {
    var member = group.member(mob.getUUID());
    if (member != null && mob.getHealth() > 0 && mob.getHealth() != member.health()) {
      var replacement = member.withHealth(mob.getHealth());
      rewriteMembers(
          mob,
          level,
          group,
          group.members().stream().map(old -> old.equals(member) ? replacement : old).toList());
    }
  }

  private static void removeMember(Entity mob, ServerLevel level, Group group) {
    rewriteMembers(
        mob,
        level,
        group,
        group.members().stream()
            .filter(member -> !member.entityId().equals(mob.getUUID()))
            .toList());
  }

  private static void rewriteMembers(
      Entity mob, ServerLevel level, Group group, List<Member> members) {
    var owner = ownerId(mob);
    if (owner == null) return;
    var state = BeastmasterState.get(level.getServer());
    state.put(
        owner,
        state.owner(owner).cooldownUntil(),
        members.isEmpty() ? null : group.withMembers(members));
  }

  private static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      dismiss(player);
    } else if (event.getEntity().level() instanceof ServerLevel level) {
      var group = activeGroup(event.getEntity(), level);
      if (group != null) removeMember(event.getEntity(), level, group);
    }
  }

  private static void incoming(LivingIncomingDamageEvent event) {
    if (!(event.getEntity().level() instanceof ServerLevel level)) return;
    var source = event.getSource().getEntity();
    if (source != null && ownerId(source) != null) {
      var group = activeGroup(source, level);
      var owner = level.getServer().getPlayerList().getPlayer(ownerId(source));
      if (group == null || owner == null || !safeTarget(owner, event.getEntity())) {
        event.setCanceled(true);
        return;
      }
    }
    var group = activeGroup(event.getEntity(), level);
    if (group != null && group.protectedGroup()) event.setAmount(event.getAmount() * 0.75F);
  }

  private static void damaged(LivingDamageEvent.Post event) {
    if (!(event.getEntity().level() instanceof ServerLevel level)) return;
    if (event.getEntity() instanceof Mob mob) {
      var group = activeGroup(mob, level);
      if (group != null) updateHealth(mob, level, group);
    }
    if (!(event.getSource().getEntity() instanceof Mob companion)
        || activeGroup(companion, level) == null) return;
    var owner = level.getServer().getPlayerList().getPlayer(ownerId(companion));
    if (owner != null) {
      var subclass = RaceIdentity.subclass(owner);
      PracticeActions.awardRangerSummonHit(
          owner, event, subclass != null && subclass.id().equals("beastmaster"));
    }
  }

  private static void protectDrops(LivingDropsEvent event) {
    if (ownerId(event.getEntity()) != null) event.setCanceled(true);
  }

  private static void protectExperience(LivingExperienceDropEvent event) {
    if (ownerId(event.getEntity()) != null) event.setDroppedExperience(0);
  }

  private static void interact(PlayerInteractEvent.EntityInteract event) {
    if (ownerId(event.getTarget()) != null) event.setCanceled(true);
  }

  private static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
    if (ownerId(event.getTarget()) != null) event.setCanceled(true);
  }

  private static void grief(EntityMobGriefingEvent event) {
    if (ownerId(event.getEntity()) != null) event.setCanGrief(false);
  }

  private static void changeDimension(EntityTravelToDimensionEvent event) {
    if (ownerId(event.getEntity()) != null) event.setCanceled(true);
  }
}
