package dev.aeroscapes.mixin.trees;

import dev.pandasystems.fallingtrees.FallingTreesRegistriesKt;
import dev.pandasystems.fallingtrees.entity.TreeEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TreeEntity.class, remap = false)
public abstract class PandaTreeSaveMixin {
  @Shadow @Final private static EntityDataAccessor<Map<BlockPos, BlockState>> BLOCKS;
  @Shadow @Final private static EntityDataAccessor<List<ItemStack>> DROPS;
  @Shadow @Final private static EntityDataAccessor<BlockPos> ORIGIN_POS;
  @Shadow @Final private static EntityDataAccessor<Direction> FALL_DIRECTION;
  @Shadow @Final private static EntityDataAccessor<String> TREE_TYPE_LOCATION;

  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native Panda 0.14.0 has empty entity persistence.
  private void aeroscapes$save(CompoundTag tag, CallbackInfo callback) {
    TreeEntity entity = (TreeEntity) (Object) this;
    tag.putInt("AeroscapesTreeAge", entity.tickCount);
    tag.putLong("AeroscapesTreeOrigin", entity.getOriginPos().asLong());
    tag.putInt("AeroscapesTreeDirection", entity.getDirection().get3DDataValue());
    tag.putString("AeroscapesTreeType", entity.getEntityData().get(TREE_TYPE_LOCATION));
    ListTag blocks = new ListTag();
    for (var entry : entity.getBlocks().entrySet()) {
      CompoundTag block = new CompoundTag();
      block.putLong("Position", entry.getKey().asLong());
      block.put("State", NbtUtils.writeBlockState(entry.getValue()));
      blocks.add(block);
    }
    tag.put("AeroscapesTreeBlocks", blocks);
    ListTag drops = new ListTag();
    for (ItemStack stack : entity.getEntityData().get(DROPS)) {
      if (!stack.isEmpty()) drops.add(stack.saveOptional(entity.registryAccess()));
    }
    tag.put("AeroscapesTreeDrops", drops);
  }

  @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Restore one pending drop batch and its animation age.
  private void aeroscapes$load(CompoundTag tag, CallbackInfo callback) {
    TreeEntity entity = (TreeEntity) (Object) this;
    String type = tag.getString("AeroscapesTreeType");
    var identifier = ResourceLocation.tryParse(type);
    if (identifier == null || FallingTreesRegistriesKt.getTree(identifier) == null) {
      throw new IllegalArgumentException("Unknown saved falling tree type: " + type);
    }
    entity.setTreeType(FallingTreesRegistriesKt.getTree(identifier));
    entity.tickCount = Math.max(0, tag.getInt("AeroscapesTreeAge"));
    entity.getEntityData().set(TREE_TYPE_LOCATION, type);
    entity.getEntityData().set(ORIGIN_POS, BlockPos.of(tag.getLong("AeroscapesTreeOrigin")));
    entity
        .getEntityData()
        .set(FALL_DIRECTION, Direction.from3DDataValue(tag.getInt("AeroscapesTreeDirection")));
    Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    ListTag savedBlocks = tag.getList("AeroscapesTreeBlocks", CompoundTag.TAG_COMPOUND);
    for (int index = 0; index < savedBlocks.size(); index++) {
      CompoundTag block = savedBlocks.getCompound(index);
      blocks.put(
          BlockPos.of(block.getLong("Position")),
          NbtUtils.readBlockState(
              entity.registryAccess().lookupOrThrow(Registries.BLOCK), block.getCompound("State")));
    }
    entity.getEntityData().set(BLOCKS, blocks);
    List<ItemStack> drops = new ArrayList<>();
    ListTag savedDrops = tag.getList("AeroscapesTreeDrops", CompoundTag.TAG_COMPOUND);
    for (int index = 0; index < savedDrops.size(); index++) {
      ItemStack stack =
          ItemStack.parseOptional(entity.registryAccess(), savedDrops.getCompound(index));
      if (!stack.isEmpty()) drops.add(stack);
    }
    entity.getEntityData().set(DROPS, drops);
  }
}
