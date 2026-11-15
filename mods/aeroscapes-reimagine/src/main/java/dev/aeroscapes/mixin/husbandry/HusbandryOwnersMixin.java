package dev.aeroscapes.mixin.husbandry;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.athisworkshop.animalhusbandry.care.AnimalCareData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnimalCareData.class, remap = false)
abstract class HusbandryOwnersMixin {
  @Shadow public boolean isDomesticated;
  @Shadow public UUID ownerUUID;
  @Shadow public List<UUID> coOwners;

  @Inject(method = "isAnyOwnerOnline", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$allDimensions(
      ServerLevel level, CallbackInfoReturnable<Boolean> callback) {
    var players = level.getServer().getPlayerList();
    callback.setReturnValue(
        !isDomesticated
            || ownerUUID == null
            || players.getPlayer(ownerUUID) != null
            || coOwners.stream().anyMatch(uuid -> players.getPlayer(uuid) != null));
  }
}
