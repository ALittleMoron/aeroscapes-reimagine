package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.HorseRecallSnapshots;
import io.github.mortuusars.horseman.world.summoning.StoredBoundHorse;
import io.github.mortuusars.horseman.world.summoning.Summoning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Summoning.class, remap = false)
public abstract class HorseRecallSnapshotMixin {
  @Inject(method = "addOrUpdateBoundHorse", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native save/load/recall paths update every alias.
  private void aeroscapes$updateInstrumentAliases(AbstractHorse horse, CallbackInfo callback) {
    HorseRecallSnapshots.synchronize((Summoning) (Object) this, horse, horse.getUUID());
  }

  @WrapOperation(
      method = "summonHorse",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/github/mortuusars/horseman/world/summoning/Summoning;addOrUpdateBoundHorse(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Carry older instrument records to the recreated UUID.
  private void aeroscapes$followRecreatedHorse(
      Summoning summoning,
      AbstractHorse horse,
      Operation<Void> original,
      @Local(argsOnly = true) StoredBoundHorse previous) {
    original.call(summoning, horse);
    HorseRecallSnapshots.synchronize(summoning, horse, previous.getEntityUuid());
  }

  @Inject(method = "unbindHorse", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Moving one instrument must retain other aliases.
  private void aeroscapes$retainOtherInstruments(
      ServerLevel level, StoredBoundHorse removed, CallbackInfo callback) {
    HorseRecallSnapshots.preserveRemainingBinding((Summoning) (Object) this, level, removed);
  }

  @Inject(method = "onHorseLoaded", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Entity NBT may predate reassignment of one alias.
  private void aeroscapes$restoreRemainingInstrument(
      ServerLevel level, AbstractHorse horse, CallbackInfoReturnable<Boolean> callback) {
    HorseRecallSnapshots.restoreActiveAlias((Summoning) (Object) this, horse);
  }
}
