package dev.aeroscapes.mixin.husbandry;

import dev.aeroscapes.husbandry.TroughAutomation;
import dev.aeroscapes.husbandry.TroughWaterAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FeedingTroughBlockEntity.class, remap = false)
abstract class HusbandryTroughMixin implements TroughWaterAccess {
  @Unique private int aeroscapes$waterRemainder;

  @Override
  public int aeroscapes$waterRemainder() {
    return aeroscapes$waterRemainder;
  }

  @Override
  public void aeroscapes$waterRemainder(int remainder) {
    aeroscapes$waterRemainder = Math.clamp(remainder, 0, 999);
  }

  public boolean canPlaceItem(int slot, ItemStack stack) {
    return slot == 0
        && TroughAutomation.food(stack)
        && TroughAutomation.waterUnits((FeedingTroughBlockEntity) (Object) this) == 0;
  }

  @Inject(method = "saveAdditional", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$saveRemainder(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
    tag.putInt("AeroscapesWaterRemainder", aeroscapes$waterRemainder);
  }

  @Inject(method = "loadAdditional", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$loadRemainder(
      CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
    aeroscapes$waterRemainder(tag.getInt("AeroscapesWaterRemainder"));
  }
}
