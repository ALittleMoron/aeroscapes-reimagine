package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.ArmorSpecialties;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class ArmorMobilityReforgeMixin {
  @WrapOperation(
      method = "travel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$iceGrip(
      BlockState block,
      LevelReader level,
      BlockPos position,
      Entity entity,
      Operation<Float> original) {
    float friction = original.call(block, level, position, entity);
    if (block.is(BlockTags.ICE) && entity instanceof Player player && player.onGround()) {
      return ArmorSpecialties.iceFriction(player, friction);
    }
    return friction;
  }

  @WrapOperation(
      method = "travel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
              ordinal = 0),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$anchorAtBottom(
      LivingEntity entity, float speed, Vec3 input, Operation<Void> original) {
    if (entity instanceof Player player) ArmorSpecialties.steadyAtBottom(player);
    original.call(entity, speed, input);
  }

  @WrapOperation(
      method = "aiStep",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V"),
      require = 3,
      allow = 3)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$swimUp(LivingEntity entity, FluidType fluid, Operation<Void> original) {
    original.call(entity, fluid);
    if (fluid == NeoForgeMod.WATER_TYPE.value() && entity instanceof Player player) {
      ArmorSpecialties.swimUp(player);
    }
  }
}
