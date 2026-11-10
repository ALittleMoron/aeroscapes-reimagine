package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CommonHooks.class, remap = false)
abstract class MiningDropCollectorMixin {
  @WrapOperation(
      method = "handleBlockDrops",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$collectDrops(
      ServerLevel level,
      Entity entity,
      Operation<Boolean> original,
      @Local(argsOnly = true) Entity breaker,
      @Local(argsOnly = true) ItemStack tool,
      @Local(argsOnly = true) BlockPos pos,
      @Local(argsOnly = true) BlockState state) {
    if (!(entity instanceof ItemEntity drop)
        || !(breaker instanceof ServerPlayer player)
        || state.hasBlockEntity()
        || !ToolReforges.isSurvivalPlayer(player)) return original.call(level, entity);
    int delay = ((MiningDropPickupDelayAccess) drop).aeroscapes$pickupDelay();
    if (delay > 10 || drop.getTarget() != null) return original.call(level, entity);
    String profile = ReforgeProfiles.path(tool);
    if (profile.equals("underwater_tool_legendary_bottom_collector")
        && ToolReforges.isSubmerged(player)
        && aeroscapes$nextToWater(level, pos)) {
      drop.setPos(player.getEyePosition());
      drop.setDeltaMovement(Vec3.ZERO);
    }
    boolean added = original.call(level, entity);
    if (added && drop.isAlive() && profile.equals("pick_legendary_collector")) {
      drop.setNoPickUpDelay();
      try {
        drop.playerTouch(player);
      } finally {
        if (drop.isAlive()) drop.setPickUpDelay(delay);
      }
    }
    return added;
  }

  @Unique
  private static boolean aeroscapes$nextToWater(ServerLevel level, BlockPos pos) {
    for (Direction side : Direction.values()) {
      BlockPos adjacent = pos.relative(side);
      if (level
                  .getChunkSource()
                  .getChunkNow(
                      SectionPos.blockToSectionCoord(adjacent.getX()),
                      SectionPos.blockToSectionCoord(adjacent.getZ()))
              != null
          && level.getFluidState(adjacent).is(FluidTags.WATER)) return true;
    }
    return false;
  }
}
