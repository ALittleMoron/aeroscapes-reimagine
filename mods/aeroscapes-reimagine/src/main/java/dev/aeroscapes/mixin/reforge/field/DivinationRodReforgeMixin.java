package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.FieldReforges;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "com.klikli_dev.theurgy.content.item.divinationrod.DivinationRodItem",
    remap = false)
abstract class DivinationRodReforgeMixin {
  @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$heldDuration(int duration, @Local(argsOnly = true) ItemStack rod) {
    return FieldReforges.divinationDuration(rod, duration);
  }

  // The block and tag search paths each read range followed by duration.
  @ModifyExpressionValue(
      method = "use",
      at = {
        @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", ordinal = 1),
        @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", ordinal = 3)
      },
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$scanDuration(int duration, @Local ItemStack rod) {
    return FieldReforges.divinationDuration(rod, duration);
  }

  @ModifyArg(
      method = "finishUsingItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$scanCooldown(int duration, @Local(argsOnly = true) ItemStack rod) {
    return FieldReforges.divinationDuration(rod, duration);
  }

  // Ordinal zero resets damage on break. Only the second call charges for a scan.
  @ModifyArg(
      method = "finishUsingItem",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V",
              ordinal = 1),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$scanWear(
      int damage,
      @Local(argsOnly = true) ItemStack rod,
      @Local(argsOnly = true) LivingEntity user) {
    return damage - 1 + FieldReforges.divinationWear(rod, user);
  }

  @Inject(
      method = "finishUsingItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/klikli_dev/theurgy/content/item/divinationrod/DivinationRodItem;spawnResultParticle(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$showResultDepth(
      ItemStack rod,
      Level level,
      LivingEntity user,
      CallbackInfoReturnable<ItemStack> callback,
      @Local BlockPos result) {
    if (!(user instanceof Player player) || !ReforgeProfiles.has(rod, "divination_legendary_depth"))
      return;
    int offset = result.getY() - player.getBlockY();
    String depth = offset < -2 ? "below" : offset > 2 ? "above" : "level";
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.equipment.divination.depth." + depth), true);
  }
}
