package dev.aeroscapes.races;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProfessionDiscipline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class AnimalLure {
  public static final String ID = "aeroscapes_reimagine:animal_lure";
  public static final String GROUP = "aeroscapes_reimagine:farming";
  public static final String UNLEARNED = "animal_lure_unlearned";
  public static final String FOLLOWING = "animal_lure_active";
  private static final String UNAVAILABLE = "animal_lure_unavailable";
  private static final String COOLDOWN = "animal_lure_ready_at";
  private static final int COOLDOWN_TICKS = 120 * 20;
  private static final Map<UUID, Lure> ACTIVE = new HashMap<>();

  private AnimalLure() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(AnimalLure::tick);
    NeoForge.EVENT_BUS.addListener(AnimalLure::unload);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AnimalLure::death);
    NeoForge.EVENT_BUS.addListener(AnimalLure::logout);
    NeoForge.EVENT_BUS.addListener(AnimalLure::dimension);
  }

  public static boolean learned(ServerPlayer player) {
    return ProfessionProgression.hasTalent(player, ProfessionDiscipline.FARMING, "animal_lure");
  }

  public static int cooldownTicks(ServerPlayer player) {
    return Math.clamp(
        RaceIdentity.data(player).getLong(COOLDOWN) - time(player), 0, COOLDOWN_TICKS);
  }

  public static String reason(ServerPlayer player) {
    if (!learned(player)) return UNLEARNED;
    if (!available(player)) return UNAVAILABLE;
    if (active(player) != null) return FOLLOWING;
    return cooldownTicks(player) > 0 ? "cooldown" : "";
  }

  public static boolean activate(ServerPlayer player) {
    String reason = reason(player);
    if (reason.equals(FOLLOWING)) {
      clear(player);
      message(player, "animal_lure_released");
      return true;
    }
    clear(player);
    if (!reason.isEmpty()) {
      SpellKnowledge.message(player, reason);
      return false;
    }

    ServerLevel level = player.serverLevel();
    int radius = upgrade(player, "animal_lure_radius") ? 16 : 10;
    int duration = (upgrade(player, "animal_lure_duration") ? 90 : 60) * 20;
    var lure = new Lure(player, level, radius, time(player) + duration, new ArrayList<>());
    for (var animal :
        level.getEntitiesOfClass(
            Animal.class,
            player.getBoundingBox().inflate(radius),
            candidate -> eligible(candidate) && lure.inRange(candidate))) {
      int priority = temptationPriority(animal);
      if (priority == Integer.MAX_VALUE) continue;
      var follower = new Follower(lure, animal);
      animal.goalSelector.addGoal(priority, follower);
      lure.followers().add(follower);
    }

    if (lure.followers().isEmpty()) {
      SpellKnowledge.message(player, "animal_lure_no_targets");
      return false;
    }
    ACTIVE.put(player.getUUID(), lure);
    RaceIdentity.data(player).putLong(COOLDOWN, time(player) + COOLDOWN_TICKS);
    message(player, "animal_lure_started", lure.followers().size());
    return true;
  }

  private static boolean upgrade(ServerPlayer player, String node) {
    return ProfessionProgression.hasTalent(player, ProfessionDiscipline.FARMING, node);
  }

  private static boolean available(ServerPlayer player) {
    return player.isAlive()
        && !player.isSpectator()
        && !player.hasDisconnected()
        && !(player instanceof FakePlayer);
  }

  private static long time(ServerPlayer player) {
    return player.server.overworld().getGameTime();
  }

  private static void message(ServerPlayer player, String key, Object... arguments) {
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.abilities." + key, arguments), true);
  }

  private static boolean eligible(Animal animal) {
    return animal.isAlive()
        && !animal.isNoAi()
        && !animal.isLeashed()
        && !animal.isPassenger()
        && !animal.isVehicle()
        && animal.getTarget() == null
        && animal.getNavigation() instanceof GroundPathNavigation
        && !(animal instanceof Enemy || animal instanceof NeutralMob)
        && !(animal instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
        && !(animal instanceof TamableAnimal pet && (pet.isTame() || pet.isOrderedToSit()))
        && !(animal instanceof AbstractHorse horse && horse.isTamed())
        && !(animal instanceof Rabbit rabbit && rabbit.getVariant() == Rabbit.Variant.EVIL)
        && !(animal instanceof Panda panda && panda.isAggressive());
  }

  private static int temptationPriority(Animal animal) {
    var goals = animal.goalSelector.getAvailableGoals();
    if (goals.stream().anyMatch(goal -> goal.getGoal() instanceof Follower)
        || goals.stream().noneMatch(goal -> goal.getGoal() instanceof BreedGoal))
      return Integer.MAX_VALUE;
    return goals.stream()
        .filter(goal -> goal.getGoal().getClass() == TemptGoal.class)
        .mapToInt(WrappedGoal::getPriority)
        .min()
        .orElse(Integer.MAX_VALUE);
  }

  private static Lure active(ServerPlayer player) {
    var lure = ACTIVE.get(player.getUUID());
    return lure != null && lure.owner() == player && lure.valid() ? lure : null;
  }

  private static void clear(ServerPlayer player) {
    var lure = ACTIVE.get(player.getUUID());
    if (lure != null && lure.owner() == player) {
      ACTIVE.remove(player.getUUID());
      lure.release();
    }
  }

  private static void tick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)) return;
    var lures = ACTIVE.values().iterator();
    while (lures.hasNext()) {
      var lure = lures.next();
      if (lure.level() != level) continue;
      if (!lure.valid() || !learned(lure.owner())) lure.release();
      else lure.followers().removeIf(Follower::releaseIfLost);
      if (lure.followers().isEmpty()) lures.remove();
    }
  }

  private static void death(LivingDeathEvent event) {
    if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  private static void unload(LevelEvent.Unload event) {
    ACTIVE
        .values()
        .removeIf(
            lure -> {
              if (lure.level() != event.getLevel()) return false;
              lure.release();
              return true;
            });
  }

  private record Lure(
      ServerPlayer owner, ServerLevel level, int radius, long expires, List<Follower> followers) {
    private boolean valid() {
      return owner.level() == level && available(owner) && time(owner) < expires;
    }

    private boolean inRange(Animal animal) {
      return animal.level() == level && animal.distanceToSqr(owner) <= radius * radius;
    }

    private void release() {
      followers.forEach(Follower::release);
      followers.clear();
    }
  }

  private static final class Follower extends TemptGoal {
    private final Lure lure;
    private final Animal animal;

    private Follower(Lure lure, Animal animal) {
      super(animal, 1.1, item -> false, false);
      this.lure = lure;
      this.animal = animal;
    }

    @Override
    public boolean canUse() {
      if (!lure.valid() || !lure.inRange(animal) || !eligible(animal)) return false;
      player = lure.owner();
      return true;
    }

    private boolean releaseIfLost() {
      if (lure.inRange(animal) && eligible(animal)) return false;
      release();
      return true;
    }

    private void release() {
      animal.goalSelector.removeGoal(this);
    }
  }
}
