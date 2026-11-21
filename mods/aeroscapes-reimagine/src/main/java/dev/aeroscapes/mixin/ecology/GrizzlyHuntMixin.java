package dev.aeroscapes.mixin.ecology;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.teamabnormals.buzzier_bees.common.entity.animal.GrizzlyBear", remap = false)
public abstract class GrizzlyHuntMixin extends Animal {
  @Unique
  private static final TagKey<EntityType<?>> AEROSCAPES_TURKEYS =
      TagKey.create(
          Registries.ENTITY_TYPE, ResourceLocation.parse("aeroscapes_reimagine:turkey_prey"));

  protected GrizzlyHuntMixin(EntityType<? extends Animal> type, Level level) {
    super(type, level);
  }

  @Inject(method = "registerGoals", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native melee and target priorities stay in effect.
  private void aeroscapes$huntTurkeys(CallbackInfo callback) {
    targetSelector.addGoal(
        4,
        new NearestAttackableTargetGoal<>(
            this,
            AgeableMob.class,
            60,
            true,
            true,
            prey -> !isBaby() && !isLeashed() && prey.getType().is(AEROSCAPES_TURKEYS)));
  }
}
