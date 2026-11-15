package dev.aeroscapes.mixin.husbandry;

import dev.aeroscapes.husbandry.CareClock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Animal.class, priority = 900)
abstract class AnimalCareClockMixin {
  @Unique private boolean aeroscapes$clockStarted;
  @Unique private long aeroscapes$lastDay;
  @Unique private long aeroscapes$lastGameTick;
  @Unique private long aeroscapes$savedCareElapsed;

  @Inject(method = "aiStep", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$loadedCareTime(CallbackInfo callback) {
    Animal animal = (Animal) (Object) this;
    if (!(animal.level() instanceof ServerLevel level) || !aeroscapes$managed(animal)) return;
    var care = ModAttachments.getCareData(animal);
    long now = level.getDayTime();
    if (!aeroscapes$clockStarted) {
      care.lastTickTime = now - aeroscapes$savedCareElapsed;
      aeroscapes$clockStarted = true;
    } else {
      long elapsed =
          CareClock.elapsed(
              aeroscapes$lastDay - care.lastTickTime,
              aeroscapes$lastDay,
              now,
              level.getGameTime() - aeroscapes$lastGameTick,
              care.isAnyOwnerOnline(level));
      care.lastTickTime = now - elapsed;
    }
    aeroscapes$lastDay = now;
    aeroscapes$lastGameTick = level.getGameTime();
    ModAttachments.setCareData(animal, care);
  }

  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$saveCareTime(CompoundTag tag, CallbackInfo callback) {
    Animal animal = (Animal) (Object) this;
    if (aeroscapes$managed(animal) && !animal.level().isClientSide()) {
      long elapsed =
          aeroscapes$clockStarted
              ? Math.max(0, aeroscapes$lastDay - ModAttachments.getCareData(animal).lastTickTime)
              : aeroscapes$savedCareElapsed;
      tag.putLong("AeroscapesLoadedCareElapsed", elapsed);
    }
  }

  @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$readCareTime(CompoundTag tag, CallbackInfo callback) {
    aeroscapes$savedCareElapsed = Math.max(0, tag.getLong("AeroscapesLoadedCareElapsed"));
    aeroscapes$clockStarted = false;
  }

  @Unique
  private static boolean aeroscapes$managed(Animal animal) {
    var type = animal.getType();
    return type == EntityType.COW
        || type == EntityType.SHEEP
        || type == EntityType.PIG
        || type == EntityType.CHICKEN
        || type == EntityType.GOAT
        || type == EntityType.RABBIT;
  }
}
