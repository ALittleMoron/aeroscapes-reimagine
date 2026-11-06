package dev.aeroscapes.mixin.construction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.construction.LittleSableColliderAccess;
import dev.aeroscapes.construction.LittleSableColliders;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.littletiles.common.block.mc.BlockTile;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class LittleSableRapierContextMixin implements LittleSableColliderAccess {
  @Shadow @Final private ServerLevel level;
  @Unique private final LittleSableColliders aeroscapes$tileColliders = new LittleSableColliders();

  @WrapOperation(
      method =
          "handleChunkSectionAddition(Lnet/minecraft/world/level/chunk/LevelChunkSection;IIIZ)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery;getPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private @Nullable RapierVoxelColliderData aeroscapes$uploadedTile(
      RapierVoxelColliderBakery bakery,
      BlockState state,
      Operation<RapierVoxelColliderData> original,
      @Local(index = 12) BlockPos position) {
    return aeroscapes$collider(bakery, state, original, position);
  }

  @WrapOperation(
      method =
          "handleBlockChange(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;IIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery;getPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;",
              ordinal = 0),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private @Nullable RapierVoxelColliderData aeroscapes$neighborTile(
      RapierVoxelColliderBakery bakery,
      BlockState state,
      Operation<RapierVoxelColliderData> original,
      @Local(index = 13) BlockPos position) {
    return aeroscapes$collider(bakery, state, original, position);
  }

  @WrapOperation(
      method =
          "handleBlockChange(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;IIILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery;getPhysicsDataForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;",
              ordinal = 1),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private @Nullable RapierVoxelColliderData aeroscapes$changedTile(
      RapierVoxelColliderBakery bakery,
      BlockState state,
      Operation<RapierVoxelColliderData> original,
      @Local(index = 8) BlockPos position) {
    return aeroscapes$collider(bakery, state, original, position);
  }

  @Unique
  private @Nullable RapierVoxelColliderData aeroscapes$collider(
      RapierVoxelColliderBakery bakery,
      BlockState state,
      Operation<RapierVoxelColliderData> original,
      BlockPos position) {
    if (state.getBlock() instanceof BlockTile)
      return aeroscapes$tileColliders.get(level, position, state);
    aeroscapes$tileColliders.release(position);
    return original.call(bakery, state);
  }

  @Inject(method = "handleChunkSectionRemoval(III)V", at = @At("RETURN"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$releaseSection(int x, int y, int z, CallbackInfo callback) {
    aeroscapes$tileColliders.releaseSection(x, y, z);
  }

  @Inject(method = "dispose()V", at = @At("RETURN"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$releasePipeline(CallbackInfo callback) {
    aeroscapes$tileColliders.clear();
  }

  @Override
  public List<AABB> aeroscapes$colliderBoxes(BlockPos position) {
    return aeroscapes$tileColliders.boxes(position);
  }
}
