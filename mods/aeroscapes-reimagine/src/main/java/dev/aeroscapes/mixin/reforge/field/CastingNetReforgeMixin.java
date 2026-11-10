package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.field.FieldReforges;
import dev.aeroscapes.equipment.field.NetReforges;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.ninni.spawn.server.item.CastingNetItem", remap = false)
abstract class CastingNetReforgeMixin {
  @Unique
  private static final ResourceLocation aeroscapes$CAUGHT =
      ResourceLocation.fromNamespaceAndPath("spawn", "caught_entities");

  @ModifyConstant(method = "use", constant = @Constant(doubleValue = 0.9))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$widenAim(
      double aim,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) InteractionHand hand) {
    return player instanceof ServerPlayer serverPlayer
        ? NetReforges.castAim(serverPlayer, player.getItemInHand(hand), aim)
        : aim;
  }

  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private List<Mob> aeroscapes$visibleTargets(
      ServerLevel level,
      Class<Mob> type,
      AABB bounds,
      Predicate<Mob> filter,
      Operation<List<Mob>> original,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) InteractionHand hand) {
    if (player instanceof ServerPlayer serverPlayer
        && NetReforges.castAim(serverPlayer, player.getItemInHand(hand), 0.9) < 0.9) {
      return original.call(level, type, bounds, filter.and(player::hasLineOfSight));
    }
    return original.call(level, type, bounds, filter);
  }

  @Inject(method = "use", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$recoverCast(
      Level level,
      Player player,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> result) {
    if (!(player instanceof ServerPlayer serverPlayer)) return;
    ItemStack stack = player.getItemInHand(hand);
    Object data = FieldReforges.component(stack, aeroscapes$CAUGHT);
    if (data instanceof CustomData captured && captured.copyTag().contains("CapturedMobs", 9)) {
      if (result.getReturnValue().getResult().consumesAction())
        NetReforges.landed(serverPlayer, stack);
    } else if (!(data instanceof CustomData)) {
      NetReforges.missed(serverPlayer, stack);
    }
  }
}
