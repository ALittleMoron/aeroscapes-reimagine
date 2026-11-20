package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.aeroscapes.sky.fauna.CloudJelly;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import dev.aeroscapes.sky.fauna.SkyRay;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class FaunaRegression {
  private static final BlockPos LANDMARK = new BlockPos(72, 296, 72);
  private static final Vec3 OBSERVER = new Vec3(72.5, 260, 72.5);
  private static final UUID JELLY_ID = UUID.fromString("5553511a-9e66-44e7-b918-e2ea80707f68");
  private final ServerLevel level;
  private final boolean reload;
  private int ticks;
  private ServerSubLevel ship;
  private Vec3 firstPose;
  private SkyRay ray;
  private TestShipPlayerSession session;
  private float healthBefore;
  private Cow crossing;
  private CloudJelly fixedJelly;

  private FaunaRegression(ServerLevel level, boolean reload) {
    this.level = level;
    this.reload = reload;
  }

  static void start(ServerLevel level, String phase) {
    if (!phase.equals("prepare") && !phase.equals("reload")) {
      throw new IllegalArgumentException("Unknown sky fauna phase " + phase);
    }
    var test = new FaunaRegression(level, phase.equals("reload"));
    if (!test.reload) FaunaEncounterRegression.warm(level, OBSERVER);
    NeoForge.EVENT_BUS.addListener(test::tick);
  }

  private void tick(ServerTickEvent.Post event) {
    if (!reload && ticks == 24 && !FaunaEncounterRegression.ready(level, OBSERVER)) return;
    ticks++;
    if (ticks == 25) {
      require(
          level.getBlockState(LANDMARK).is(Blocks.CHISELED_COPPER),
          "Previous pack landmark missing");
      if (reload) {
        for (var form : CloudJellyAnatomy.Form.values()) {
          require(
              level.getEntity(savedId(form)) instanceof CloudJelly saved
                  && saved.bellDiameter() == 12.5F
                  && saved.form() == form
                  && Math.abs(
                          saved.getBbHeight()
                              - 12.5
                                  * CloudJellyAnatomy.of(form).height()
                                  / CloudJellyAnatomy.SIZE_UNITS)
                      < 0.0001,
              "Persisted giant lost its size or anatomy: " + form);
        }
        LogUtils.getLogger().info("Sky fauna regression passed: reload");
        return;
      }
      CloudJellySwimmingRegression.verify(level, OBSERVER);
      SkyRayFlightRegression.verify(level, OBSERVER);
      FaunaEncounterRegression.verify(level, OBSERVER);
      createCabin();
    }
    if (reload) return;
    if (ticks == 37) {
      checkCabin();
      WhaleEscortRegression.verify(level, ship);
      SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
      observeRay();
      prepareCrossing();
    }
    if (ticks == 43) {
      require(crossing.getHealth() == crossing.getMaxHealth(), "Distant jelly hurt a bystander");
      crossing.setPos(crossing.position().add(8, 0, 0));
    }
    if (ticks == 46) {
      require(
          crossing.getHealth() < crossing.getMaxHealth(),
          "Ordinary ticking missed a fast crossing");
      crossing.discard();
      fixedJelly.discard();
    }
    if (ticks == 100) {
      require(session.jellyShelterHints() == 0, "Premature jelly shelter hint");
      checkShelterHint();
    }
    if (ticks == 114) {
      ray.moveTo(session.player.getX(), session.player.getY(), session.player.getZ() + 1.2);
    }
    if (ticks == 115) {
      require(ray.getTarget() == null && !ray.isDefending(), "Ray attacked a peaceful observer");
      var wing = ray.getParts()[2];
      Vec3 target = wing.getBoundingBox().getCenter();
      Vec3 above = target.add(0, 2, 0);
      Vec3 below = target.add(0, -2, 0);
      var hit =
          ProjectileUtil.getEntityHitResult(
              level,
              session.player,
              above,
              below,
              new net.minecraft.world.phys.AABB(above, below).inflate(.25),
              part -> part == wing);
      require(
          hit != null
              && hit.getEntity().hurt(level.damageSources().playerAttack(session.player), 1),
          "Native wing pick did not forward direct damage");
      require(ray.getTarget() == session.player, "Ray failed to recognize its attacker");
      require(
          !session.player.isInvulnerableTo(level.damageSources().mobAttack(ray)),
          "Observer still in character creation");
      healthBefore = session.player.getHealth();
    }
    if (ticks == 140) {
      require(session.player.getHealth() < healthBefore, "Provoked ray never retaliated");
      session.player.setPos(OBSERVER.x + 50, OBSERVER.y, OBSERVER.z);
    }
    if (ticks == 158) {
      require(ray.getTarget() == null, "Ray kept pursuing after disengagement");
      ray.discard();
      session.player.setPos(OBSERVER);
      checkShelterHint();
      session.close();
      for (var form : CloudJellyAnatomy.Form.values()) {
        CloudJelly saved = jelly(12.5F);
        saved.setForm(form);
        saved.setUUID(savedId(form));
        saved.setNoAi(true);
        int index = form.ordinal();
        saved.moveTo(
            LANDMARK.getX() + .5 + (index % 2) * 16,
            LANDMARK.getY() - 4 - saved.getBbHeight(),
            LANDMARK.getZ() + .5 + (index / 2) * 16);
        saved.setPersistenceRequired();
        require(level.addFreshEntity(saved), "Could not save giant " + form);
      }
      LogUtils.getLogger().info("Sky fauna regression passed: prepare");
    }
  }

  private static UUID savedId(CloudJellyAnatomy.Form form) {
    return new UUID(
        JELLY_ID.getMostSignificantBits(), JELLY_ID.getLeastSignificantBits() + form.ordinal());
  }

  static void centerBell(CloudJelly jelly, Vec3 destination) {
    jelly.setPos(jelly.position().add(destination.subtract(jelly.bellCenter())));
  }

  private void checkShelterHint() {
    var jelly = jelly(1);
    Vec3 center = session.player.getBoundingBox().getCenter();
    centerBell(jelly, center);
    require(jelly.stingAlong(session.player, center, jelly.position()), "Survival sting failed");
    require(session.jellyShelterHints() == 1, "Expected one shelter hint across successful stings");
  }

  private void createCabin() {
    BlockPos start = new BlockPos(64, 280, 64);
    var blocks = new ArrayList<BlockPos>();
    for (BlockPos cell : BlockPos.betweenClosed(start, start.offset(4, 4, 4))) {
      int dx = cell.getX() - start.getX();
      int dy = cell.getY() - start.getY();
      int dz = cell.getZ() - start.getZ();
      if (dx == 0 || dy == 0 || dz == 0 || dx == 4 || dy == 4 || dz == 4) {
        blocks.add(cell.immutable());
        level.setBlockAndUpdate(cell, Blocks.OAK_PLANKS.defaultBlockState());
      } else level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
    }
    ship =
        SubLevelAssemblyHelper.assembleBlocks(
            level, start, blocks, new BoundingBox3i(64, 280, 64, 69, 285, 69));
    SubLevelContainer.getContainer(level)
        .addForceLoadTicket(ship, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
    firstPose = worldCenter();
  }

  private Vec3 worldCenter() {
    var bounds = ship.getPlot().getBoundingBox();
    return ship.logicalPose()
        .transformPosition(new Vec3(bounds.minX() + 2.5, bounds.minY() + 2.5, bounds.minZ() + 2.5));
  }

  private void checkCabin() {
    Vec3 center = worldCenter();
    require(center.distanceToSqr(firstPose) > .01, "Sable fixture did not move");
    Cow victim = EntityType.COW.create(level);
    require(victim != null, "Missing living target");
    victim.moveTo(center.x, center.y - victim.getBbHeight() / 2, center.z);
    require(
        !dev.aeroscapes.sky.fauna.FaunaEncounters.clear(
            level, new net.minecraft.world.phys.AABB(center, center).inflate(.25)),
        "Ship interior admitted a spawn");
    CloudJelly inside = jelly(1);
    centerBell(inside, center);
    require(
        !inside.stingAlong(victim, victim.getBoundingBox().getCenter(), inside.position()),
        "Anatomy wholly within the moving cabin bypassed shelter");
    CloudJelly jelly = jelly(8);
    jelly.setForm(CloudJellyAnatomy.Form.LANTERN);
    var frame = jelly.swimFrame(level.getGameTime());
    var shape = frame.shape();
    var longest =
        shape.strands().stream()
            .max(Comparator.comparingDouble(strand -> strand.segments().getLast().bottom()))
            .orElseThrow();
    var tip =
        frame.segment(shape.strands().indexOf(longest), longest.segments().size() - 1).getCenter();
    Vec3 filament =
        frame
            .basis
            .apply(tip.multiply(1, -1, 1))
            .scale(jelly.getBbWidth() / CloudJellyAnatomy.SIZE_UNITS)
            .add(0, jelly.getBbHeight(), 0);
    jelly.setPos(center.subtract(filament));
    float health = victim.getHealth();
    require(
        !jelly.stingAlong(victim, victim.getBoundingBox().getCenter(), jelly.position())
            && victim.getHealth() == health,
        "Tentacle stung through a closed moving cabin");
    var roof =
        ship.logicalPose()
            .transformPosition(
                new Vec3(
                    ship.getPlot().getBoundingBox().minX() + 2.5,
                    ship.getPlot().getBoundingBox().minY() + 5.1,
                    ship.getPlot().getBoundingBox().minZ() + 2.5));
    victim.moveTo(roof.x, roof.y, roof.z);
    Vec3 exposed = victim.getBoundingBox().getCenter();
    jelly.setPos(exposed.subtract(filament));
    require(
        jelly.stingAlong(victim, exposed, jelly.position()) && victim.getHealth() < health,
        "Exposed deck failed to register the same filament");
    Cow deck = EntityType.COW.create(level);
    require(deck != null, "Missing deck target");
    deck.moveTo(roof.x + 4, roof.y, roof.z);
    ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
            deck)
        .sable$setTrackingSubLevel(ship);
    CloudJelly skim = jelly(1);
    centerBell(skim, exposed);
    require(
        skim.stingAlong(deck, deck.getBoundingBox().getCenter().add(-8, 0, 0), skim.position()),
        "Moving-deck traversal through the bell missed contact");
    CloudJelly other = jelly(8);
    other.setForm(jelly.form());
    centerBell(other, exposed);
    victim.invulnerableTime = 0;
    require(
        !other.stingAlong(victim, exposed, other.position()),
        "Overlapping jelly bypassed shared cooldown");
    Cow crossing = EntityType.COW.create(level);
    require(crossing != null, "Missing crossing target");
    crossing.moveTo(92, 283, 92);
    Vec3 end = crossing.getBoundingBox().getCenter();
    CloudJelly small = jelly(1);
    centerBell(small, end.add(-4, 0, 0));
    require(
        small.stingAlong(crossing, end.add(-8, 0, 0), small.position()),
        "Fast crossing skipped a small jelly");
    FaunaHistoricalCoverRegression.verify(level, ship);
  }

  private void observeRay() {
    session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("27dafe08-7192-4882-8f28-52a948d704c3"),
            "RayObserver",
            OBSERVER,
            null);
    com.cyberday1.neoorigins.event.PlayerLifecycleEvents.onPlayerLogin(
        new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(
            session.player));
    require(
        RaceIdentity.choose(
            session.player,
            Race.HUMAN,
            0,
            CharacterClass.WARRIOR,
            "quick_block",
            Profession.BLACKSMITH,
            "material_repair"),
        "Survival observer must finish character creation");
    session.player.setNoGravity(true);
    ray = AeroFauna.SKY_RAY.get().create(level);
    require(ray != null, "Missing ray");
    ray.setPersistenceRequired();
    ray.moveTo(OBSERVER.x, OBSERVER.y, OBSERVER.z + 1.2);
    require(level.addFreshEntity(ray) && ray.getTarget() == null, "Ray not neutral before attack");
  }

  private void prepareCrossing() {
    fixedJelly = jelly(1);
    fixedJelly.setNoAi(true);
    fixedJelly.setPersistenceRequired();
    crossing = EntityType.COW.create(level);
    require(crossing != null, "Missing sweep target");
    crossing.setNoAi(true);
    crossing.setNoGravity(true);
    crossing.moveTo(79, 273.5, 82);
    centerBell(fixedJelly, crossing.getBoundingBox().getCenter().add(4, 0, 0));
    require(
        level.addFreshEntity(crossing) && level.addFreshEntity(fixedJelly),
        "Could not register crossing");
  }

  private CloudJelly jelly(float size) {
    CloudJelly jelly = AeroFauna.CLOUD_JELLY.get().create(level);
    require(jelly != null, "Missing jelly");
    jelly.initSize(size);
    return jelly;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
