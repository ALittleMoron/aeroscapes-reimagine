package dev.aeroscapes.regression;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.AnimalLure;
import harmonised.pmmo.api.APIUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.puffish.skillsmod.SkillsMod;

final class AnimalLureRegression {
  private AnimalLureRegression() {}

  static void verify(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var floor =
        new BlockPos(
            spawn.getX() + 160,
            Math.min(spawn.getY() + 65, level.getMaxBuildHeight() - 12),
            spawn.getZ() + 104);
    for (var pos : BlockPos.betweenClosed(floor.offset(-17, -1, -7), floor.offset(17, 2, 7))) {
      level.setBlockAndUpdate(
          pos, (pos.getY() < floor.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }
    var origin = Vec3.atBottomCenterOf(floor);
    var clock = (ServerLevelData) level.getLevelData();
    long start = clock.getGameTime();
    var animals = new ArrayList<Animal>();
    try (var ownerSession =
            TestShipPlayerSession.create(level, UUID.randomUUID(), "LureFarmer", origin, null);
        var otherSession =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "LureNeighbor", origin.add(0, 0, 1), null)) {
      var owner = ownerSession.player;
      var other = otherSession.player;
      ProgressionRegression.listPlayer(level, owner, true);
      ProgressionRegression.listPlayer(level, other, true);
      try {
        var group =
            List.of(
                spawn(level, EntityType.COW, origin.add(-5, 0, 0), animals),
                spawn(level, EntityType.SHEEP, origin.add(-4, 0, 2), animals),
                spawn(level, EntityType.PIG, origin.add(4, 0, 3), animals),
                spawn(level, EntityType.CHICKEN, origin.add(-5, 0, -2), animals),
                spawn(level, EntityType.RABBIT, origin.add(5, 0, -3), animals),
                spawn(
                    level,
                    BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("naturalist:deer")),
                    origin.add(6, 0, 0),
                    animals));
        var farther = spawn(level, EntityType.COW, origin.add(13, 0, 0), animals);
        var leashed = spawn(level, EntityType.COW, origin.add(-4, 0, -3), animals);
        leashed.setLeashedTo(other, false);
        var killer = (Rabbit) spawn(level, EntityType.RABBIT, origin.add(5, 0, 0), animals);
        killer.setVariant(Rabbit.Variant.EVIL);
        verifyPreparation(owner, other);
        require(
            following(group.getFirst()) && group.getFirst().getNavigation().getPath() != null,
            "Mixed herd uses the cow's native ground navigation without held feed");
        require(
            group.stream().allMatch(AnimalLureRegression::following),
            "Vanilla farm stock, rabbits and Naturalist deer follow together");
        require(
            !following(farther) && !following(leashed) && !following(killer),
            "Base radius and leash and hostile behavior limits are respected");
        require(
            AnimalLure.cooldownTicks(other) == 0 && !AnimalLure.activate(other),
            "A second farmer cannot take over a claimed herd");
        require(AnimalLure.cooldownTicks(other) == 0, "A failed competing lure has no cooldown");

        var late = spawn(level, EntityType.SHEEP, origin.add(4, 0, -1), animals);
        tick(level, clock, start + 20);
        require(!following(late), "The lure only includes animals present when cast");
        PreparedAbilities.cast(owner);
        require(
            AnimalLure.cooldownTicks(owner) == 2_380
                && group.getFirst().getNavigation().getPath() == null
                && group.stream().noneMatch(AnimalLureRegression::following),
            "Manual release stops the herd immediately without refreshing cooldown");
        require(!AnimalLure.activate(owner), "Cooldown blocks a new lure after release");
        require(AnimalLure.activate(other), "The released herd can follow another farmer");
        require(AnimalLure.activate(other), "The second farmer can release the herd");

        tick(level, clock, start + 2_400);
        require(AnimalLure.activate(owner), "Base lure can start again after 120 seconds");
        tick(level, clock, start + 3_599);
        require(
            AnimalLure.reason(owner).equals(AnimalLure.FOLLOWING),
            "Base lure is active before 60 seconds");
        tick(level, clock, start + 3_600);
        require(AnimalLure.reason(owner).equals("cooldown"), "The base lure expires at 60 seconds");

        learn(owner, "animal_lure_radius", "animal_lure_duration");
        tick(level, clock, start + 4_800);
        require(
            AnimalLure.activate(owner) && following(farther),
            "The radius upgrade reaches 13 blocks");
        tick(level, clock, start + 6_000);
        require(
            AnimalLure.reason(owner).equals(AnimalLure.FOLLOWING),
            "The duration upgrade exceeds 60 seconds");
        tick(level, clock, start + 6_600);
        require(
            !following(farther) && AnimalLure.reason(owner).equals("cooldown"),
            "The upgraded lure expires at 90 seconds");

        tick(level, clock, start + 7_200);
        require(
            AnimalLure.activate(owner) && following(farther), "Prepare an active lure for travel");
        NeoForge.EVENT_BUS.post(
            new PlayerEvent.PlayerChangedDimensionEvent(owner, Level.OVERWORLD, Level.NETHER));
        require(
            !following(farther) && AnimalLure.cooldownTicks(owner) == 2_400,
            "Dimension change clears the herd immediately with its original cooldown");
      } finally {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(other));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
        ProgressionRegression.listPlayer(level, other, false);
        ProgressionRegression.listPlayer(level, owner, false);
        animals.forEach(Entity::discard);
      }
    } finally {
      clock.setGameTime(start);
    }
  }

  private static void verifyPreparation(ServerPlayer owner, ServerPlayer other) {
    PreparedAbilities.edit(owner, 1, 0, AnimalLure.ID);
    require(
        !PreparedAbilities.loadout(owner).contains(AnimalLure.ID),
        "A farmer must learn Animal Lure before preparing it");
    for (var player : List.of(owner, other)) {
      APIUtils.setLevel("farming", player, 20);
      ProgressionBridge.sync(player);
      learn(player, "animal_lure");
    }
    PreparedAbilities.edit(owner, 1, 0, AnimalLure.ID);
    PreparedAbilities.select(owner, 1, 0);
    require(
        AnimalLure.ID.equals(PreparedAbilities.preparation(owner).slots().getFirst())
            && owner.getMainHandItem().isEmpty()
            && owner.getOffhandItem().isEmpty(),
        "Animal Lure prepares on a normal wheel and requires neither hand to hold an item");
    PreparedAbilities.cast(owner);
    require(
        AnimalLure.reason(owner).equals(AnimalLure.FOLLOWING)
            && AnimalLure.cooldownTicks(owner) == 2_400,
        "The prepared action starts the lure and its ordinary cooldown");
  }

  private static void learn(ServerPlayer player, String... talents) {
    for (var talent : talents) {
      SkillsMod.getInstance()
          .tryUnlockSkill(player, ProfessionDiscipline.FARMING.category(), talent, false);
    }
  }

  private static Animal spawn(
      ServerLevel level, EntityType<?> type, Vec3 position, List<Animal> animals) {
    if (!(type.create(level) instanceof Animal animal))
      throw new AssertionError("Missing test animal: " + type);
    animal.setPos(position);
    animal.setOnGround(true);
    require(level.addFreshEntity(animal), "Register test animal " + type);
    animals.add(animal);
    return animal;
  }

  private static boolean following(Animal animal) {
    animal.goalSelector.tick();
    return animal.goalSelector.getAvailableGoals().stream()
        .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof TemptGoal);
  }

  private static void tick(ServerLevel level, ServerLevelData clock, long time) {
    clock.setGameTime(time);
    NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, level));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
