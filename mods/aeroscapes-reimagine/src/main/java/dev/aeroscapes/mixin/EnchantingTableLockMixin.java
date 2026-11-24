package dev.aeroscapes.mixin;

import dev.aeroscapes.enchanting.LegacyEnchantingTables.LockedTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.LockCode;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableBlockEntity.class)
public abstract class EnchantingTableLockMixin implements LockedTable {
  @Unique private LockCode aeroscapes$lock = LockCode.NO_LOCK;

  @Override
  public LockCode aeroscapes$lockCode() {
    return aeroscapes$lock;
  }

  @Inject(method = "loadAdditional", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by Mixin after table data loads.
  private void aeroscapes$loadLock(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
    aeroscapes$lock = LockCode.fromTag(tag);
  }

  @Inject(method = "saveAdditional", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by Mixin when table data is saved.
  private void aeroscapes$saveLock(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
    aeroscapes$lock.addToTag(tag);
  }
}
