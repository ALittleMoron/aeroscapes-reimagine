package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.RodReforges;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
abstract class VanillaRodReforgeMixin implements RodReforges.VanillaCast {
  private static final String RANDOM_INT =
      "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I";

  @Unique private String aeroscapes$rodProfile = "";

  @Override
  public void aeroscapes$rememberRodProfile(String profile) {
    aeroscapes$rodProfile = profile;
  }

  @ModifyExpressionValue(
      method = "catchingFish",
      at = @At(value = "INVOKE", target = RANDOM_INT, ordinal = 0))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$biteWindow(int ticks) {
    return RodReforges.biteWindow(ticks, aeroscapes$rodProfile);
  }

  @ModifyExpressionValue(
      method = "catchingFish",
      at = @At(value = "INVOKE", target = RANDOM_INT, ordinal = 2))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$biteWait(int ticks) {
    return RodReforges.biteWait(ticks, aeroscapes$rodProfile);
  }

  @WrapOperation(
      method = "retrieve",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
              ordinal = 0))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$salvage(
      Level level,
      Entity entity,
      Operation<Boolean> original,
      @Local(argsOnly = true) ItemStack rod,
      @Share("aeroscapes$salvageRolled") LocalBooleanRef rolled) {
    var hook = (FishingHook) (Object) this;
    if (!rolled.get()
        && entity instanceof ItemEntity drop
        && aeroscapes$rodProfile.equals("rod_legendary_salvager")
        && ReforgeProfiles.has(rod, aeroscapes$rodProfile)
        && hook.getPlayerOwner() instanceof ServerPlayer player
        && ToolReforges.isSurvivalPlayer(player)) {
      rolled.set(RodReforges.trySalvage(drop, hook.getRandom()));
    }
    return original.call(level, entity);
  }
}
