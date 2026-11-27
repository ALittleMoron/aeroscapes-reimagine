package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.MartialArchery;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BowItem.class)
abstract class TripleShotBowMixin {
  @WrapOperation(
      method = "releaseUsing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/BowItem;draw(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static List<ItemStack> aeroscapes$tripleDraw(
      ItemStack bow,
      ItemStack ammo,
      LivingEntity shooter,
      Operation<List<ItemStack>> original,
      @Local float power) {
    if (power == 1.0F && shooter instanceof ServerPlayer player) {
      ItemStack first = MartialArchery.drawTriple(player, bow, ammo);
      if (!first.isEmpty()) return List.of(first);
    }
    return original.call(bow, ammo, shooter);
  }

  @Inject(method = "releaseUsing", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$finishRelease(
      ItemStack bow, Level level, LivingEntity shooter, int remaining, CallbackInfo callback) {
    if (shooter instanceof ServerPlayer player) MartialArchery.finishRelease(player);
  }
}
