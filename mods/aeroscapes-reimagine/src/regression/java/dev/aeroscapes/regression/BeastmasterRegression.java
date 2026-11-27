package dev.aeroscapes.regression;

import dev.aeroscapes.equipment.combat.BeastmasterCompanions;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import dev.aeroscapes.travel.TravelIntegration;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

final class BeastmasterRegression {
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
  private static final String FOX = "beastmaster_fox";
  private static final String WOLF = "beastmaster_wolf";
  private static final String BEAR = "beastmaster_bear";
  private static final String RECALL = "beastmaster_recall";
  private static final String FOCUS = "beastmaster_focus_attack";

  private BeastmasterRegression() {}

  static void verify(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    int y = Math.min(spawn.getY() + 60, level.getMaxBuildHeight() - 12);
    var origin = new BlockPos(spawn.getX() + 96, y, spawn.getZ() + 96);
    var destination = origin.offset(48, 0, 0);
    platform(level, origin, 6, Blocks.GRASS_BLOCK);
    platform(level, destination, 6, Blocks.STONE);
    var clock = (ServerLevelData) level.getLevelData();
    long start = clock.getGameTime();
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "BeastmasterFixture", at(origin), null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        var magic = MagicData.getPlayerMagicData(player);
        magic.setServerPlayer(player);
        magic.getSyncedData();
        player.setGameMode(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                player,
                Race.GOBLIN,
                0,
                CharacterClass.RANGER,
                "bow_stride",
                Profession.FARMER,
                "extra_harvest"),
            "Prepared summoning uses a survival character past the creation screen");
        verifyPurchase(session);
        verifyCommands(level, player);
        verifyReplacement(session, clock, origin);
        verifyTravel(level, session, origin, destination, clock);
      } finally {
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        require(BeastmasterCompanions.groupCount(player) == 0, "Death clears the saved group");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        ProgressionRegression.listPlayer(level, player, false);
      }
    } finally {
      clock.setGameTime(start);
    }
  }

  private static void verifyPurchase(TestShipPlayerSession session) {
    var player = session.player;
    var provider = SkillsMod.getInstance();
    require(!BeastmasterCompanions.learned(player, FOX), "The first fox requires its talent");
    APIUtils.setLevel("ranger", player, 3);
    ProgressionBridge.sync(player);
    int spent = provider.getSpentPoints(player, RANGER).orElseThrow();
    provider.tryUnlockSkill(player, RANGER, FOX, false);
    require(
        provider.getSkillState(player, RANGER, FOX).orElseThrow() == Skill.State.UNLOCKED
            && provider.getSpentPoints(player, RANGER).orElseThrow() == spent + 1,
        "One ordinary Ranger point buys the first fox");
    learn(player, WOLF, BEAR);
    verifyModeFeedback(session);
    summon(session, FOX, 1);
    require(
        BeastmasterCompanions.cooldownTicks(player, WOLF) == 3_000,
        "A successful call starts the same 150-second timer for every species");
  }

  private static void verifyModeFeedback(TestShipPlayerSession session) {
    var player = session.player;
    player.setGameMode(GameType.CREATIVE);
    for (String species : List.of(FOX, WOLF, BEAR)) {
      cast(session, species);
      var entry =
          session.abilities().entries().stream()
              .filter(ability -> ability.id().equals("aeroscapes_reimagine:" + species))
              .findFirst()
              .orElseThrow();
      require(
          entry.level() == 1
              && !entry.usable()
              && entry.reason().equals("beastmaster_mode")
              && "beastmaster_mode".equals(session.takeAbilityReason())
              && BeastmasterCompanions.groupCount(player) == 0
              && BeastmasterCompanions.cooldownTicks(player, species) == 0,
          "The wheel and action bar explain the mode restriction for " + species);
    }
    player.setGameMode(GameType.ADVENTURE);
    require(BeastmasterCompanions.reason(player, FOX).isEmpty(), "Adventure retains companion use");
    player.setGameMode(GameType.SURVIVAL);
  }

  private static void verifyCommands(ServerLevel level, ServerPlayer player) {
    var fox = onlyCompanion(player);
    require(fox.getType() == EntityType.FOX, "The first call uses a native fox");
    var target = EntityType.ZOMBIE.create(level);
    require(target != null, "Hostile command target exists");
    target.setNoAi(true);
    target.setPos(player.position().add(0, 0, 4));
    require(level.addFreshEntity(target), "Add the hostile command target");
    try {
      long ranger = APIUtils.getXp("ranger", player);
      long combat = APIUtils.getXp("combat", player);
      require(fox.doHurtTarget(target), "Independent companion delivers a native melee hit");
      require(
          APIUtils.getXp("ranger", player) > ranger && APIUtils.getXp("combat", player) == combat,
          "An independent companion hit funds Ranger only");
      learn(player, RECALL, FOCUS);
      player.setXRot(0);
      player.setYRot(0);
      require(BeastmasterCompanions.activate(player, FOCUS), "Focus selects the hostile target");
      require(fox.getTarget() == target, "Focus orders the active fox to attack");
      require(BeastmasterCompanions.activate(player, RECALL), "Recall works while aiming at a foe");
      require(fox.getTarget() == null, "Recall disengages the active fox");
    } finally {
      target.discard();
    }
  }

  private static void verifyReplacement(
      TestShipPlayerSession session, ServerLevelData clock, BlockPos origin) {
    var player = session.player;
    var original = onlyCompanion(player);
    learn(player, WOLF, BEAR, "beastmaster_persistence", "beastmaster_fox_count");
    clock.setGameTime(clock.getGameTime() + 3_001);
    summon(session, FOX, 3);
    require(original.isRemoved(), "A successful repeat removes the old group");
    List<Mob> foxes = companions(player);
    require(foxes.size() == 3, "Only the trained fox group is present");

    clock.setGameTime(clock.getGameTime() + 3_001);
    player.setPos(at(origin).add(16, 9, 0));
    cast(session, WOLF);
    require(
        "beastmaster_no_space".equals(session.takeAbilityReason()),
        "The server reports nearby terrain cannot fit the group");
    require(
        BeastmasterCompanions.groupCount(player) == 3
            && foxes.stream().noneMatch(Mob::isRemoved)
            && BeastmasterCompanions.cooldownTicks(player, FOX) == 0,
        "A failed call preserves the old animals and spends no cooldown");
    player.setPos(at(origin));
    verifySpawnRefusal(session, foxes);
    summon(session, WOLF, 1);
    require(
        foxes.stream().allMatch(Mob::isRemoved)
            && onlyCompanion(player).getType() == EntityType.WOLF,
        "The first wolf replaces every fox and starts at one");

    learn(player, "beastmaster_wolf_count", "beastmaster_cooldown");
    clock.setGameTime(clock.getGameTime() + 3_001);
    summon(session, WOLF, 2);
    require(BeastmasterCompanions.cooldownTicks(player, WOLF) == 2_400, "Cooldown is now 120s");
    clock.setGameTime(clock.getGameTime() + 2_401);
    summon(session, BEAR, 1);
    require(
        BuiltInRegistries.ENTITY_TYPE
            .getKey(onlyCompanion(player).getType())
            .equals(ResourceLocation.parse("naturalist:bear")),
        "The strongest group includes exactly one native brown bear");
  }

  private static void verifySpawnRefusal(TestShipPlayerSession session, List<Mob> foxes) {
    var player = session.player;
    Consumer<EntityJoinLevelEvent> rejectWolf =
        event -> {
          if (event.getLevel() == player.level() && event.getEntity().getType() == EntityType.WOLF)
            event.setCanceled(true);
        };
    NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, rejectWolf);
    try {
      cast(session, WOLF);
      require(
          "beastmaster_spawn_refused".equals(session.takeAbilityReason())
              && BeastmasterCompanions.groupCount(player) == 3
              && foxes.stream().noneMatch(Mob::isRemoved)
              && BeastmasterCompanions.cooldownTicks(player, WOLF) == 0,
          "The world refusal has its own feedback and keeps the existing group and timer");
    } finally {
      NeoForge.EVENT_BUS.unregister(rejectWolf);
    }
  }

  private static void verifyTravel(
      ServerLevel level,
      TestShipPlayerSession session,
      BlockPos origin,
      BlockPos destination,
      ServerLevelData clock) {
    ServerPlayer player = session.player;
    var source = WaystonesAPI.placeWaystone(level, origin, WaystoneStyles.DEFAULT).orElseThrow();
    var target = WaystonesAPI.placeWaystone(level, destination, WaystoneStyles.MOSSY).orElseThrow();
    player.getInventory().add(new ItemStack(TravelIntegration.WAYSTONE_SIGIL.get(), 2));
    player.setPos(at(destination));
    PlayerWaystoneManager.activateWaystone(player, target);
    player.setPos(at(origin));
    PlayerWaystoneManager.activateWaystone(player, source);

    var bear = onlyCompanion(player);
    var identity = BeastmasterCompanions.companionIdentity(bear);
    require(identity != null, "The live bear has a stable companion identity");
    bear.setHealth(bear.getMaxHealth() / 2);
    float health = bear.getHealth();
    int cooldown = BeastmasterCompanions.cooldownTicks(player, BEAR);
    long beforePayment = TravelIntegration.experiencePoints(player);
    require(!travel(player, source, target), "Travel without payment is rejected");
    require(
        onlyCompanion(player) == bear
            && TravelIntegration.experiencePoints(player) == beforePayment,
        "A failed trip preserves the existing companion and experience");

    player.giveExperiencePoints(120);
    require(travel(player, source, target), "Physical paid travel succeeds");
    session.acknowledgeTeleport();
    require(TravelIntegration.experiencePoints(player) == beforePayment, "Travel charges 120 XP");
    var arrived = onlyCompanion(player);
    require(
        identity.equals(BeastmasterCompanions.companionIdentity(arrived))
            && arrived.distanceTo(player) <= 6
            && arrived.getHealth() == health
            && BeastmasterCompanions.cooldownTicks(player, BEAR) == cooldown,
        "Paid travel preserves the living companion, its health and the running clock");

    narrowLanding(level, origin);
    player.setPos(at(destination));
    player.giveExperiencePoints(120);
    require(travel(player, target, source), "Player can return when the bear cannot land");
    session.acknowledgeTeleport();
    require(
        BeastmasterCompanions.pending(player)
            && BeastmasterCompanions.groupCount(player) == 1
            && companions(player).isEmpty(),
        "Blocked arrival leaves the existing bear pending without a second live copy");
    platform(level, origin, 6, Blocks.STONE);
    clock.setGameTime(clock.getGameTime() + 10);
    player.tickCount += 10 - player.tickCount % 10;
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    var returned = onlyCompanion(player);
    require(
        identity.equals(BeastmasterCompanions.companionIdentity(returned))
            && returned.getHealth() == health
            && !BeastmasterCompanions.pending(player),
        "The same wounded bear arrives once the destination is safe");
    require(
        BeastmasterCompanions.cooldownTicks(player, BEAR) == cooldown - 10,
        "The pending trip leaves the shared cooldown running");
  }

  private static boolean travel(ServerPlayer player, Waystone source, Waystone target) {
    return WaystoneTeleportManager.tryTeleport(WaystonesRegression.context(player, source, target))
        .isSuccessful();
  }

  private static void cast(TestShipPlayerSession session, String species) {
    String id = "aeroscapes_reimagine:" + species;
    session.receivePlayPayload(new AbilityNetwork.Edit(0, 0, id), "6");
    session.receivePlayPayload(new AbilityNetwork.Select(0, 0, 1), "6");
    session.takeAbilityReason();
    session.receivePlayPayload(new AbilityNetwork.Cast(), "6");
  }

  private static void summon(TestShipPlayerSession session, String species, int count) {
    cast(session, species);
    String refusal = session.takeAbilityReason();
    require(
        refusal == null && BeastmasterCompanions.groupCount(session.player) == count,
        "Expected " + count + " " + species + " after a prepared cast. Refusal " + refusal);
  }

  private static void learn(ServerPlayer player, String... nodes) {
    for (String node : nodes) SkillsMod.getInstance().tryUnlockSkill(player, RANGER, node, true);
  }

  private static List<Mob> companions(ServerPlayer player) {
    return player
        .serverLevel()
        .getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(24),
            mob -> BeastmasterCompanions.isCompanionOf(mob, player));
  }

  private static Mob onlyCompanion(ServerPlayer player) {
    var group = companions(player);
    require(group.size() == 1, "Exactly one living companion is near the player: " + group.size());
    return group.getFirst();
  }

  private static Vec3 at(BlockPos stone) {
    return Vec3.atBottomCenterOf(stone.south());
  }

  private static void platform(ServerLevel level, BlockPos stone, int radius, Block ground) {
    for (BlockPos pos :
        BlockPos.betweenClosed(
            stone.offset(-radius, -1, -radius), stone.offset(radius, 3, radius))) {
      if (pos.equals(stone) || pos.equals(stone.above())) continue;
      level.setBlockAndUpdate(
          pos, (pos.getY() < stone.getY() ? ground : Blocks.AIR).defaultBlockState());
    }
  }

  private static void narrowLanding(ServerLevel level, BlockPos stone) {
    for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-6, -3, -6), stone.offset(6, -1, 6))) {
      if (pos.equals(stone.below()) || pos.equals(stone.south().below())) continue;
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
