package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.EnchantingMastery;
import me.alfie.immersiveenchanting.networking.EnchantPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EnchantPacket.class, remap = false)
abstract class LibraryApplicationMixin {
  @Shadow
  public abstract int level();

  @Inject(
      method = "exec",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lme/alfie/immersiveenchanting/util/FxHelper;playEnchantSuccess(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Z)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$awardPractice(IPayloadContext context, CallbackInfo callback) {
    EnchantingMastery.award(context.player(), level());
  }
}
