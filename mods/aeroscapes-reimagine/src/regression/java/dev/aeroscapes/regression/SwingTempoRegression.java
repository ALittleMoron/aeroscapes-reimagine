package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.equipment.combat.SwingTempo;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.bettercombat.network.Packets.C2S_AttackRequest;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class SwingTempoRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);

  private SwingTempoRegression() {}

  static void verify(ServerLevel level) {
    Vec3 pos = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 24, 0);
    ServerLevelData clock = level.getServer().getWorldData().overworldData();
    long initialTime = clock.getGameTime();
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "SwingFixture", pos, null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      var attacker = new Zombie(EntityType.ZOMBIE, level);
      var other = new Zombie(EntityType.ZOMBIE, level);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        var ranger = CharacterClass.RANGER;
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                ranger,
                ranger.starters().getFirst(),
                Profession.BLACKSMITH,
                "material_repair"),
            "Choose an ordinary character for melee tempo");
        for (int tick = 0; tick < 60; tick++) player.tick();
        attacker.setNoAi(true);
        other.setNoAi(true);
        attacker.setPos(pos.add(0, 0, 1.2));
        other.setPos(pos.add(0.7, 0, 1.3));
        require(
            level.addFreshEntity(attacker) && level.addFreshEntity(other), "Spawn tempo targets");
        verifyParry(session, clock, attacker, other);
        verifyHeavy(session, attacker);
        verifyWetlandDagger(player, other);
      } finally {
        MartialTechniques.reset(player);
        ProgressionRegression.listPlayer(level, player, false);
        attacker.discard();
        other.discard();
      }
    } finally {
      clock.setGameTime(initialTime);
    }
  }

  private static void verifyParry(
      TestShipPlayerSession session, ServerLevelData clock, Zombie attacker, Zombie other) {
    ServerPlayer player = session.player;
    player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());
    var knife =
        BuiltInRegistries.ITEM
            .get(ResourceLocation.parse("farmersdelight:iron_knife"))
            .getDefaultInstance();
    player.setItemInHand(InteractionHand.OFF_HAND, knife);
    player.doTick();
    var offhand = PlayerAttackHelper.getCurrentAttack(player, 1);
    require(
        offhand != null
            && offhand.isOffHand()
            && MartialTechniques.isLightWeapon(offhand.itemStack()),
        "Native dual wield selects the light off hand after a sword swing");

    int target = attacker.getId();
    long warriorXp = APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player);
    long rangerXp = APIUtils.getXp(ProgressionBridge.RANGER_PRACTICE, player);
    require(swing(player, attacker, 0, 20, target, target) > 0, "The sword swing hits");
    require(
        APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player) > warriorXp
            && APIUtils.getXp(ProgressionBridge.RANGER_PRACTICE, player) == rangerXp,
        "Better Combat's main-hand sword funds Warrior alone");
    warriorXp = APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player);
    require(swing(player, attacker, 1, 20, target, target) > 0, "The knife swing hits");
    require(
        APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player) == warriorXp
            && APIUtils.getXp(ProgressionBridge.RANGER_PRACTICE, player) > rangerXp,
        "Better Combat's light off hand funds Ranger alone");

    int replyTicks = 4;
    float ordinary = swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId());
    float normalRecovery = player.getCurrentItemAttackStrengthDelay();
    var ready = defend(session, attacker, other);
    begin(session, ready);
    clock.setGameTime(clock.getGameTime() + 4);
    rangerXp = APIUtils.getXp(ProgressionBridge.RANGER_PRACTICE, player);
    float spedUp =
        swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId(), other.getId());
    require(
        spedUp > ordinary,
        "After a delayed request the parry reply gains charge in the selected hand");
    require(
        APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player) == warriorXp
            && APIUtils.getXp(ProgressionBridge.RANGER_PRACTICE, player) > rangerXp,
        "A damaging off-hand parry reply also funds Ranger alone");
    require(
        other.getHealth() == other.getMaxHealth(),
        "An accelerated reply cannot sweep an extra target");
    require(
        player.getCurrentItemAttackStrengthDelay() == normalRecovery,
        "Reply does not change later swing recovery");
    begin(session, ready);
    float repeated = swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId());
    require(
        Math.abs(repeated - ordinary) < 0.001F,
        "The same opportunity cannot accelerate a second attack");

    begin(session, readyParry(session, attacker, clock.getGameTime() + 24));
    float attackerHealth = attacker.getHealth();
    float switched =
        swing(player, other, 1, replyTicks, other.getId(), attacker.getId(), other.getId());
    require(
        switched == 0 && attacker.getHealth() == attackerHealth,
        "Changing the target during the reply misses both entities");

    begin(session, readyParry(session, attacker, clock.getGameTime() + 24));
    float ambiguous = swing(player, attacker, 1, replyTicks, -1, attacker.getId(), other.getId());
    require(ambiguous == 0, "An empty cursor cannot pick with multiple Better Combat targets");
    begin(session, readyParry(session, attacker, clock.getGameTime() + 24));
    float soleTarget = swing(player, attacker, 1, replyTicks, -1, attacker.getId());
    require(
        Math.abs(soleTarget - spedUp) < 0.001F,
        "The sole native Better Combat target remains eligible off the main-hand cursor");

    begin(session, readyParry(session, attacker, clock.getGameTime() + 3));
    clock.setGameTime(clock.getGameTime() + 4);
    float committed = swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId());
    require(
        Math.abs(committed - spedUp) < 0.001F,
        "A reply accepted before expiration can finish its windup");

    var late = readyParry(session, attacker, clock.getGameTime() + 3);
    clock.setGameTime(clock.getGameTime() + 4);
    begin(session, late);
    float expired = swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId());
    require(expired == 0, "A reply cannot start after its synchronized window expires");

    Vec3 nearby = attacker.position();
    try {
      begin(session, readyParry(session, attacker, clock.getGameTime() + 24));
      attacker.setPos(nearby.add(0, 0, 8));
      float tooFar = swing(player, attacker, 1, replyTicks, attacker.getId(), attacker.getId());
      require(tooFar == 0, "Accelerated timing preserves native server reach checks");
    } finally {
      attacker.setPos(nearby);
    }
  }

  private static void verifyWetlandDagger(ServerPlayer player, Zombie enemy) {
    var dagger =
        BuiltInRegistries.ITEM
            .get(ResourceLocation.parse("wetland_whimsy:dagger"))
            .getDefaultInstance();
    player.setItemInHand(InteractionHand.MAIN_HAND, dagger);
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    player.doTick();
    int id = enemy.getId();
    float ordinary = swing(player, enemy, 0, 5, id, id);
    receiveMelee(player, enemy);
    require(player.getLastAttacker() == enemy, "The enemy opens the native Wetland dagger bonus");
    float reply = swing(player, enemy, 0, 5, id, id);
    require(reply > ordinary, "The native Wetland dagger bonus deals extra damage on a server");
  }

  private static SwingTempo.Window defend(
      TestShipPlayerSession session, Zombie attacker, Zombie other) {
    ServerPlayer player = session.player;
    player.setYRot(0);
    SkillsMod.getInstance().tryUnlockSkill(player, RANGER, "duelist_parry", true);
    float health = player.getMaxHealth();
    player.setHealth(health);
    player.invulnerableTime = 0;
    player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
    float ordinary = health - player.getHealth();
    require(ordinary > 0.0F, "A frontal hit lands without parrying");
    MartialTechniques.activate(player, MartialTechnique.DUELIST_PARRY);
    var arrow = new Arrow(EntityType.ARROW, player.level());
    player.setHealth(health);
    player.invulnerableTime = 0;
    player.hurt(player.damageSources().arrow(arrow, attacker), 4.0F);
    require(health - player.getHealth() > 3.0F, "An arrow passes through the parry window");
    Vec3 otherPosition = other.position();
    try {
      other.setPos(player.position().add(0, 0, -1.2));
      player.setHealth(health);
      player.invulnerableTime = 0;
      player.hurt(player.damageSources().mobAttack(other), 4.0F);
      require(Math.abs(health - player.getHealth() - ordinary) < 0.001F, "Rear hit bypasses parry");
    } finally {
      other.setPos(otherPosition);
    }
    player.setHealth(health);
    player.invulnerableTime = 0;
    player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
    require(
        Math.abs(health - player.getHealth() - ordinary * 0.55F) < 0.001F,
        "A frontal melee hit is softened by the armed parry");
    var window = session.takePayload(SwingTempo.Window.class);
    require(
        window.target() == attacker.getId(), "The actual attacker opens the synchronized reply");
    return window;
  }

  private static void verifyHeavy(TestShipPlayerSession session, Zombie attacker) {
    ServerPlayer player = session.player;
    player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_AXE.getDefaultInstance());
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    player.doTick();
    double baseRecovery = player.getCurrentItemAttackStrengthDelay();
    int shorterCharge = (int) Math.ceil(baseRecovery * 0.70);
    int fullCharge = (int) Math.ceil(baseRecovery);
    int target = attacker.getId();
    float normal = swing(player, attacker, 0, shorterCharge, target, target);
    float normalDanger = receiveMelee(player, attacker);
    SkillsMod.getInstance().tryUnlockSkill(player, COMBAT, "offensive_stance", true);
    PreparedAbilities.edit(player, 0, MartialTechnique.OFFENSIVE_STANCE.id());
    PreparedAbilities.select(player, 0);
    PreparedAbilities.cast(player);
    require(MartialTechniques.offensiveStance(player), "Learn and activate the heavy stance");
    require(
        Math.abs(receiveMelee(player, attacker) - normalDanger * 1.25F) < 0.001F,
        "The active stance increases damage taken");
    float full = swing(player, attacker, 0, fullCharge, target, target);
    var first = session.takePayload(SwingTempo.Window.class);
    require(
        first.style() == SwingTempo.Style.HEAVY && first.ticks() > 0,
        "A fully charged heavy hit opens one synchronized opportunity");
    begin(session, first);
    require(
        swing(player, attacker, 0, shorterCharge, -1) == 0,
        "An empty Better Combat request is a miss");
    begin(session, first);
    float afterMiss = swing(player, attacker, 0, shorterCharge, target, target);
    require(Math.abs(afterMiss - normal) < 0.001F, "Missing consumes the heavy acceleration");

    swing(player, attacker, 0, fullCharge, target, target);
    var second = session.takePayload(SwingTempo.Window.class);
    begin(session, second);
    float accelerated = swing(player, attacker, 0, shorterCharge, target, target);
    require(
        accelerated > normal && Math.abs(accelerated - full) < 0.001F,
        "A faster heavy swing reaches full damage at the shortened recovery");
    var renewed = session.takePayload(SwingTempo.Window.class);
    require(
        renewed.style() == SwingTempo.Style.HEAVY && renewed.token() != second.token(),
        "A landed fully charged accelerated attack opens a new heavy opportunity");
    PreparedAbilities.edit(player, 0, "");
    require(
        Math.abs(receiveMelee(player, attacker) - normalDanger * 1.25F) < 0.001F,
        "Removing the prepared ability leaves the fixed vulnerability in force");
    begin(session, renewed);
    require(
        swing(player, attacker, 0, shorterCharge, target, target) == 0.0F,
        "An acceleration request already in flight is declined after removing the ability");
    float afterRemoval = swing(player, attacker, 0, shorterCharge, target, target);
    require(Math.abs(afterRemoval - normal) < 0.001F, "Ordinary swings retain their recovery");
    var clock = player.serverLevel().getServer().getWorldData().overworldData();
    clock.setGameTime(clock.getGameTime() + 100);
    require(!MartialTechniques.offensiveStance(player), "The stance ends at its fixed deadline");
    require(
        Math.abs(receiveMelee(player, attacker) - normalDanger) < 0.001F,
        "Incoming damage returns to normal after the stance expires");
  }

  private static float receiveMelee(ServerPlayer player, Zombie attacker) {
    float health = player.getMaxHealth();
    player.setHealth(health);
    player.invulnerableTime = 0;
    player.hurt(player.damageSources().mobAttack(attacker), 4.0F);
    return health - player.getHealth();
  }

  private static SwingTempo.Window readyParry(
      TestShipPlayerSession session, Zombie target, long deadline) {
    SwingTempo.grantParry(session.player, target, deadline);
    var window = session.takePayload(SwingTempo.Window.class);
    require(
        window.style() == SwingTempo.Style.PARRY && window.target() == target.getId(),
        "Parry synchronizes the real attacker identity");
    return window;
  }

  private static void begin(TestShipPlayerSession session, SwingTempo.Window window) {
    SwingTempo.acceptAttempt(session.player, new SwingTempo.Attempt(window.token(), true));
  }

  private static float swing(
      ServerPlayer player, Zombie target, int combo, int elapsedTicks, int cursor, int... targets) {
    target.setHealth(target.getMaxHealth());
    target.invulnerableTime = 0;
    target.setDeltaMovement(Vec3.ZERO);
    player.setOnGround(true);
    ((LivingEntityAccessor) player).setLastAttackedTicks(elapsedTicks);
    var server = player.serverLevel().getServer();
    var request =
        new C2S_AttackRequest(combo, false, player.getInventory().selected, cursor, targets);
    var handled = new AtomicBoolean();
    server.doRunTask(
        new TickTask(
            server.getTickCount(),
            () -> {
              ServerNetwork.handleAttackRequest(request, server, player, player.connection);
              server.tell(new TickTask(server.getTickCount(), () -> handled.set(true)));
            }));
    long limit = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    server.managedBlock(() -> handled.get() || System.nanoTime() >= limit);
    require(handled.get(), "Deferred native attack completed within the fixture deadline");
    return target.getMaxHealth() - target.getHealth();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
