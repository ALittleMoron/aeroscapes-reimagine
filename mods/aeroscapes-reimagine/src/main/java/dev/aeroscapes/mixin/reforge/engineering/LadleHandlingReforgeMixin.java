package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.LadleReforge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fr.lucreeper74.createmetallurgy.content.entities.ladle.LadleItem", remap = false)
abstract class LadleHandlingReforgeMixin {
  @Inject(
      method = "open",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/fluids/FluidFX;getFluidParticle(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/core/particles/ParticleOptions;"),
      cancellable = true,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$clientParticlesOnly(
      Level level,
      Player player,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback,
      @Local(name = "ladle") ItemStack ladle) {
    // Native fluid draining and bucket use already ran; only client particles remain.
    if (!level.isClientSide()) callback.setReturnValue(InteractionResultHolder.success(ladle));
  }

  @WrapOperation(
      method = "open",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;setRemainingFireTicks(I)V"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$safeHandling(
      Player target,
      int ticks,
      Operation<Void> original,
      Level level,
      Player player,
      InteractionHand hand) {
    if (LadleReforge.applyNativeBurn(player.getItemInHand(hand), player.getRandom()))
      original.call(target, ticks);
  }
}
