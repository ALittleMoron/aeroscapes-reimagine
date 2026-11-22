package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.fauna.DeerHabituation;
import dev.aeroscapes.fauna.WildlifeReactions;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.world.entity.animal.deer.Deer", remap = false)
abstract class NaturalistDeerHabituationMixin implements WildlifeReactions.DeerReaction {
  @Unique private DeerHabituation aeroscapes$habituation;

  @WrapOperation(
      method = "registerGoals",
      at =
          @At(
              value = "NEW",
              target =
                  "(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;",
              ordinal = 0),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native player-avoidance goal constructor.
  private AvoidEntityGoal<?> aeroscapes$playerAvoidance(
      PathfinderMob deer,
      Class<? extends LivingEntity> species,
      float range,
      double walk,
      double sprint,
      Predicate<LivingEntity> nativePredicate,
      Operation<AvoidEntityGoal<?>> original) {
    var habituation = new DeerHabituation(deer);
    aeroscapes$habituation = habituation;
    return original.call(
        deer,
        species,
        range,
        walk,
        sprint,
        (Predicate<LivingEntity>)
            player -> nativePredicate.test(player) && habituation.avoidNormally(player));
  }

  @Inject(method = "registerGoals", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Adds a distinct movement goal.
  private void aeroscapes$addRetreat(CallbackInfo callback) {
    ((Mob) (Object) this).goalSelector.addGoal(2, aeroscapes$habituation.startleGoal());
  }

  @Inject(method = "customServerAiStep", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native deer tick.
  private void aeroscapes$observePlayers(CallbackInfo callback) {
    if (((Mob) (Object) this).level() instanceof ServerLevel level) {
      aeroscapes$habituation.observe(level);
    }
  }

  @Override
  public boolean aeroscapes$startle(Vec3 source) {
    return aeroscapes$habituation != null && aeroscapes$habituation.startle(source);
  }
}
