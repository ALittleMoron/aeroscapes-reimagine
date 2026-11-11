package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = {
      "rbasamoyai.createbigcannons.equipment.manual_loading.RamRodItem",
      "rbasamoyai.createbigcannons.equipment.manual_loading.WormItem"
    },
    remap = false)
abstract class CannonLoadingReforgeMixin {
  @ModifyArg(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
      index = 0,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$stationaryLoadingCost(
      float exhaustion, @Local(argsOnly = true) UseOnContext context) {
    return aeroscapes$loadingCost(context.getPlayer(), context.getItemInHand(), exhaustion);
  }

  @ModifyArg(
      method = "onUseOnCannon",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
      index = 0,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$mountedLoadingCost(
      float exhaustion, @Local(argsOnly = true) Player player) {
    // The mounted cannon dispatch accepts the main hand only.
    return aeroscapes$loadingCost(player, player.getMainHandItem(), exhaustion);
  }

  @Unique
  private static float aeroscapes$loadingCost(Player player, ItemStack tool, float exhaustion) {
    if (exhaustion <= 0.0F || !(player instanceof ServerPlayer) || player instanceof FakePlayer) {
      return exhaustion;
    }
    return exhaustion
        * switch (ReforgeProfiles.basePath(tool)) {
          case "cannon_loading_poor" -> 1.20F;
          case "cannon_loading_fine" -> 0.90F;
          case "cannon_loading_exceptional" -> 0.80F;
          case "cannon_loading_epic" -> 0.70F;
          case "cannon_loading_legendary" -> 0.60F;
          default -> 1.0F;
        };
  }
}
