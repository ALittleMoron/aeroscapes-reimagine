package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.ClassTraits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BowItem.class)
abstract class ClassBowMixin {
  @ModifyArgs(
      method = "releaseUsing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/BowItem;shoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$classBow(Args args) {
    if (args.get(1) instanceof ServerPlayer player) {
      args.set(5, ClassTraits.bowVelocity(player, args.get(7), args.get(5)));
      args.set(6, ClassTraits.bowSpread(player, args.get(6)));
    }
  }
}
