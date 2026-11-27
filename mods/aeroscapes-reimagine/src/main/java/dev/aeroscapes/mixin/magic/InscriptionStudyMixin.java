package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.PreparedAbilities;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InscriptionTableMenu.class, remap = false)
abstract class InscriptionStudyMixin implements PreparedAbilities.StudyTable {
  @Shadow @Final private Player player;
  @Shadow @Final protected ContainerLevelAccess access;

  @Override
  public boolean aeroscapes$canFirstStudy(ServerPlayer actor) {
    return actor == player
        && access.evaluate((level, pos) -> level == actor.level(), false)
        && ((InscriptionTableMenu) (Object) this).stillValid(actor);
  }

  @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$study(
      Player actor, int button, CallbackInfoReturnable<Boolean> callback) {
    if (button < 0)
      callback.setReturnValue(
          actor instanceof ServerPlayer serverPlayer
              && actor == player
              && actor.containerMenu == (Object) this
              && PreparedAbilities.study(serverPlayer, (InscriptionTableMenu) (Object) this));
  }

  @Inject(method = "doInscription", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$directStudy(int index, CallbackInfo callback) {
    if (player instanceof ServerPlayer serverPlayer && player.containerMenu == (Object) this)
      PreparedAbilities.study(serverPlayer, (InscriptionTableMenu) (Object) this);
    callback.cancel();
  }

  @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$noExtraction(CallbackInfo callback) {
    var result = ((InscriptionTableMenu) (Object) this).getResultSlot();
    if (result != null) result.set(ItemStack.EMPTY);
    callback.cancel();
  }
}
