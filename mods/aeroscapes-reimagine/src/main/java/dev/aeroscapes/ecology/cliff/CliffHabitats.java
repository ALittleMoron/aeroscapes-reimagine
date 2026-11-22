package dev.aeroscapes.ecology.cliff;

import dev.aeroscapes.mixin.ecology.FowlCliffFlightAccess;
import dev.ryanhcode.sable.Sable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class CliffHabitats {
  public static final ResourceLocation GULL =
      ResourceLocation.fromNamespaceAndPath("fowlplay", "gull");

  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final String HOME = MOD_ID + ":cliff_home";
  private static final String SLOT = MOD_ID + ":cliff_slot";
  private static final String STARTLED = MOD_ID + ":cliff_startled";
  private static final int RADIUS_CHUNKS = 3;
  private static final int VISIT_RANGE = 48;
  private static final int FIRST_DUSK_TICK = 12000;
  private static final int LAST_DUSK_TICK = 13500;
  private static final int DAY_TICKS = 24000;

  private static final DeferredRegister<Feature<?>> FEATURES =
      DeferredRegister.create(Registries.FEATURE, MOD_ID);
  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
  private static final Supplier<AttachmentType<CliffSite>> SITE =
      registerSite("cliff_site", CliffSite.Kind.ROOTS);
  private static final Supplier<AttachmentType<CliffSite>> ROOTS =
      registerSite("cliff_roots", CliffSite.Kind.ROOTS);
  private static final Supplier<AttachmentType<CliffSite>> GULLS =
      registerSite("cliff_gulls", CliffSite.Kind.GULLS);
  private static final Supplier<AttachmentType<CliffSite>> BATS =
      registerSite("cliff_bats", CliffSite.Kind.BATS);

  private CliffHabitats() {}

  public static void register(IEventBus modBus) {
    FEATURES.register("cliff_habitat", CliffHabitatFeature::new);
    FEATURES.register(modBus);
    ATTACHMENTS.register(modBus);
    NeoForge.EVENT_BUS.addListener(CliffHabitats::tick);
  }

  private static Supplier<AttachmentType<CliffSite>> registerSite(String id, CliffSite.Kind kind) {
    return ATTACHMENTS.register(
        id,
        () ->
            AttachmentType.builder(() -> new CliffSite(kind, BlockPos.ZERO, Direction.NORTH))
                .serialize(CliffSite.CODEC)
                .build());
  }

  private static AttachmentType<CliffSite> attachment(CliffSite.Kind kind) {
    return switch (kind) {
      case ROOTS -> ROOTS.get();
      case GULLS -> GULLS.get();
      case BATS -> BATS.get();
    };
  }

  public static @Nullable CliffSite site(ChunkAccess chunk, CliffSite.Kind kind) {
    var current = chunk.getExistingDataOrNull(attachment(kind));
    if (current != null) return current;
    var legacy = chunk.getExistingDataOrNull(SITE.get());
    return legacy != null && legacy.kind() == kind ? legacy : null;
  }

  static void store(ChunkAccess chunk, CliffSite site) {
    chunk.setData(attachment(site.kind()), site);
  }

  public static boolean validateColony(ServerLevel level, LevelChunk chunk, CliffSite site) {
    if (site.stockedDay() >= 0) return true;
    for (int offset = -1; offset <= 1; offset++) {
      var perch = site.ledge(offset);
      if (CliffHabitatFeature.perch(level, perch) && potentialGullLight(level, perch)) continue;
      removeSite(chunk, site.kind());
      return false;
    }
    return true;
  }

  private static boolean potentialGullLight(ServerLevel level, BlockPos perch) {
    // Native Animal accepts grass or nonnegative light-based pathfinding value (light >= 12).
    return level.getBlockState(perch.below()).is(Blocks.GRASS_BLOCK)
        || level.getBrightness(LightLayer.SKY, perch) >= 12
        || level.getBrightness(LightLayer.BLOCK, perch) >= 12;
  }

  public static @Nullable CliffSite resolveRoost(
      ServerLevel level, LevelChunk chunk, CliffSite site) {
    if (CliffHabitatFeature.usableBatRoof(level, site) || site.stockedDay() >= 0) return site;
    // Neighboring chunks can finish their cave decoration after this roost was selected.
    for (BlockPos position : BlockPos.withinManhattan(site.anchor(), 3, 3, 3)) {
      int x = position.getX() - chunk.getPos().getMinBlockX();
      int z = position.getZ() - chunk.getPos().getMinBlockZ();
      if (x < 2 || x > 13 || z < 2 || z > 13) continue;
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        var candidate = new CliffSite(CliffSite.Kind.BATS, position.immutable(), direction);
        if (!CliffHabitatFeature.usableBatRoof(level, candidate)) continue;
        store(chunk, candidate);
        return candidate;
      }
    }
    removeSite(chunk, site.kind());
    return null;
  }

  private static void removeSite(LevelChunk chunk, CliffSite.Kind kind) {
    chunk.removeData(attachment(kind));
    var legacy = chunk.getExistingDataOrNull(SITE.get());
    if (legacy != null && legacy.kind() == kind) chunk.removeData(SITE.get());
  }

  private static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || !level.dimension().equals(Level.OVERWORLD)
        || level.players().isEmpty()
        || level.getGameTime() % 20 != 0
        || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
        || !level.getServer().isSpawningAnimals()) return;

    Set<Long> visited = new HashSet<>();
    for (var player : level.players()) {
      if (!player.isAlive() || player.isSpectator() || player instanceof FakePlayer) continue;
      Vec3 observer = player.position();
      if (ModList.get().isLoaded("sable"))
        observer = Sable.HELPER.projectOutOfSubLevel(level, observer);
      var center = new ChunkPos(BlockPos.containing(observer));
      for (int x = center.x - RADIUS_CHUNKS; x <= center.x + RADIUS_CHUNKS; x++) {
        for (int z = center.z - RADIUS_CHUNKS; z <= center.z + RADIUS_CHUNKS; z++) {
          var chunk = level.getChunkSource().getChunkNow(x, z);
          if (chunk == null || !level.areEntitiesLoaded(chunk.getPos().toLong())) continue;
          var gulls = site(chunk, CliffSite.Kind.GULLS);
          if (inRange(level, observer, gulls) && visited.add(gulls.anchor().asLong())) {
            visitGulls(level, chunk, gulls);
          }
          var bats = site(chunk, CliffSite.Kind.BATS);
          if (inRange(level, observer, bats) && visited.add(bats.anchor().asLong())) {
            visitBats(level, chunk, bats);
          }
        }
      }
    }
  }

  private static boolean inRange(ServerLevel level, Vec3 observer, @Nullable CliffSite site) {
    return site != null
        && site.anchor().closerToCenterThan(observer, VISIT_RANGE)
        && level.isPositionEntityTicking(site.anchor());
  }

  public static void visit(ServerLevel level, LevelChunk chunk, CliffSite site) {
    if (site.kind() == CliffSite.Kind.BATS) {
      visitBats(level, chunk, site);
    } else if (site.kind() == CliffSite.Kind.GULLS) {
      visitGulls(level, chunk, site);
    }
  }

  private static void visitBats(ServerLevel level, LevelChunk chunk, CliffSite site) {
    if (site.stockedDay() < 0) {
      site = resolveRoost(level, chunk, site);
      if (site == null) return;
    }
    long day = Math.floorDiv(level.getDayTime(), DAY_TICKS);
    long time = Math.floorMod(level.getDayTime(), DAY_TICKS);
    if (time >= LAST_DUSK_TICK || site.flightDay() == day) return;
    boolean dusk = time >= FIRST_DUSK_TICK;
    if (!dusk && site.stockedDay() == day) return;

    var home = site.anchor();
    var bats =
        level.getEntitiesOfClass(Bat.class, new AABB(home).inflate(20), bat -> atHome(bat, home));
    if (bats.isEmpty() && site.stockedDay() != day) {
      for (int offset = -1; offset <= 1; offset++) {
        var position = site.ledge(offset);
        if (!CliffHabitatFeature.batRest(level, position)) continue;
        var bat = EntityType.BAT.create(level);
        if (bat != null && spawn(level, bat, position, site, offset)) {
          if (dusk) launchBat(level, bat, site.facing());
          bats.add(bat);
        }
      }
      if (!bats.isEmpty())
        store(chunk, dusk ? site.staffedOn(day).flownOn(day) : site.staffedOn(day));
      return;
    }

    if (dusk && !bats.isEmpty()) {
      for (Bat bat : bats) if (bat.isResting()) launchBat(level, bat, site.facing());
      store(chunk, site.flownOn(day));
    }
  }

  private static void launchBat(ServerLevel level, Bat bat, Direction facing) {
    bat.setResting(false);
    bat.setDeltaMovement(facing.getStepX() * 0.32, -0.13, facing.getStepZ() * 0.32);
    level.levelEvent(null, 1025, bat.blockPosition(), 0);
  }

  private static void visitGulls(ServerLevel level, LevelChunk chunk, CliffSite site) {
    if (site.stockedDay() < 0) {
      if (!validateColony(level, chunk, site)) return;
      var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(GULL).orElse(null);
      if (entityType == null) return;
      boolean any = false;
      for (int offset = -1; offset <= 1; offset++) {
        var position = site.ledge(offset);
        if (!CliffHabitatFeature.perch(level, position)) continue;
        if (entityType.create(level) instanceof Mob gull
            && gull instanceof FowlCliffFlightAccess
            && spawn(level, gull, position, site, offset)) {
          gull.setPersistenceRequired();
          any = true;
        }
      }
      if (any) store(chunk, site.staffedOn(0));
      return;
    }

    for (Mob gull :
        level.getEntitiesOfClass(
            Mob.class,
            new AABB(site.anchor()).inflate(24),
            mob -> mob instanceof FowlCliffFlightAccess && atHome(mob, site.anchor()))) {
      guideGull(level, gull, site);
    }
  }

  private static boolean spawn(
      ServerLevel level, Mob animal, BlockPos position, CliffSite site, int offset) {
    animal.moveTo(
        position.getX() + 0.5, position.getY(), position.getZ() + 0.5, site.facing().toYRot(), 0);
    if (!level.noCollision(animal)
        || !EventHooks.checkSpawnPosition(animal, level, MobSpawnType.STRUCTURE)) return false;
    animal.getPersistentData().putLong(HOME, site.anchor().asLong());
    animal.getPersistentData().putInt(SLOT, offset);
    EventHooks.finalizeMobSpawn(
        animal, level, level.getCurrentDifficultyAt(position), MobSpawnType.STRUCTURE, null);
    return level.addFreshEntity(animal);
  }

  private static boolean atHome(Mob animal, BlockPos anchor) {
    var data = animal.getPersistentData();
    return data.contains(HOME) && data.getLong(HOME) == anchor.asLong();
  }

  private static void guideGull(ServerLevel level, Mob gull, CliffSite site) {
    if (!available(gull) || gull.getPersistentData().getLong(STARTLED) > level.getGameTime())
      return;
    var flight = (FowlCliffFlightAccess) gull;
    int slot = gull.getPersistentData().getInt(SLOT);
    BlockPos perch = site.ledge(slot);
    if (!CliffHabitatFeature.perch(level, perch)) return;

    int phase = Math.floorMod(level.getGameTime() + site.anchor().asLong() + slot * 40L, 800);
    if (phase < 200) {
      var waypoint = perch.relative(site.facing(), phase < 100 ? 8 : 5).above(phase < 100 ? 5 : 8);
      if (open(level, waypoint)) flyTo(gull, flight, waypoint);
    } else if (!perch.closerToCenterThan(gull.position(), 1.1)) {
      var approach = perch.relative(site.facing(), 2).above();
      if (approach.closerToCenterThan(gull.position(), 3.5)) {
        flyTo(gull, flight, perch);
      } else if (open(level, approach)) {
        flyTo(gull, flight, approach);
      }
    } else if (flight.aeroscapes$cliffIsFlying()) {
      flight.aeroscapes$cliffStopFlying();
    }
  }

  private static boolean available(Mob gull) {
    var brain = gull.getBrain();
    return gull.isAlive()
        && !gull.isPassenger()
        && gull.getTarget() == null
        && !brain.isActive(Activity.AVOID)
        && !brain.isActive(Activity.FIGHT)
        && !brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
  }

  public static boolean startle(Mob gull, Vec3 source) {
    if (!(gull instanceof FowlCliffFlightAccess flight)
        || !(gull.level() instanceof ServerLevel level)
        || !GULL.equals(BuiltInRegistries.ENTITY_TYPE.getKey(gull.getType()))
        || !available(gull)
        || gull.getPersistentData().getLong(STARTLED) > level.getGameTime()) return false;
    var away = new Vec3(gull.getX() - source.x, 0, gull.getZ() - source.z).normalize();
    var far = BlockPos.containing(gull.position().add(away.scale(8)).add(0, 4, 0));
    var near = gull.blockPosition().above(4);
    BlockPos refuge = open(level, far) ? far : open(level, near) ? near : null;
    if (refuge == null || !flyTo(gull, flight, refuge)) return false;
    gull.getPersistentData().putLong(STARTLED, level.getGameTime() + 100);
    return true;
  }

  private static boolean open(ServerLevel level, BlockPos position) {
    if (!level.isInWorldBounds(position) || !level.isInWorldBounds(position.above())) return false;
    var chunk =
        level
            .getChunkSource()
            .getChunkNow(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()));
    return chunk != null
        && chunk.getBlockState(position).isAir()
        && chunk.getBlockState(position.above()).isAir();
  }

  private static boolean flyTo(Mob gull, FowlCliffFlightAccess flight, BlockPos waypoint) {
    if (!flight.aeroscapes$cliffIsFlying()) {
      if (!flight.aeroscapes$cliffCanStartFlying()) return false;
      flight.aeroscapes$cliffStartFlying();
      gull.addDeltaMovement(new Vec3(0, 0.22, 0));
    }
    gull.getBrain()
        .setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(waypoint, 1.0F, 0), 65);
    return true;
  }
}
