package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.AreaToolActions;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.violetmoon.quark.content.tweaks.module.SimpleHarvestModule", remap = false)
abstract class QuarkReaperMixin {
  @WrapOperation(
      method = "click",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/violetmoon/quark/content/tweaks/module/HoeHarvestingModule;getRange(Lnet/minecraft/world/item/ItemStack;)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$extendReaping(
      ItemStack stack,
      Operation<Integer> original,
      @Local(argsOnly = true) Player player,
      @Share("baseHarvestRange") LocalIntRef baseRange) {
    int range = original.call(stack);
    if (!ReforgeProfiles.has(stack, "hoe_legendary_reaper")
        || !ToolReforges.isSurvivalPlayer(player)) return range;
    if (player.isShiftKeyDown()) return 1;
    if (range >= 4) return range;
    baseRange.set(range);
    return range + 1;
  }

  @WrapOperation(
      method = "click",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/violetmoon/quark/content/tweaks/module/SimpleHarvestModule;tryHarvestOrClickCrop(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Z)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$guardExtraReaping(
      Level level,
      BlockPos pos,
      LivingEntity entity,
      InteractionHand hand,
      boolean simpleOnly,
      Operation<Boolean> original,
      @Local(argsOnly = true) BlockPos origin,
      @Share("baseHarvestRange") LocalIntRef baseRange) {
    int range = baseRange.get();
    boolean extra =
        range > 0
            && (Math.abs(origin.getX() - pos.getX()) >= range
                || Math.abs(origin.getZ() - pos.getZ()) >= range);
    if (extra
        && (!(entity instanceof Player player)
            || !AreaToolActions.canReapExtra(player, level, pos))) return false;
    return original.call(level, pos, entity, hand, simpleOnly);
  }
}
