package dev.aeroscapes.mixin.inventory;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "tschipp.carryon.common.carry.CarryOnData", remap = false)
abstract class CarryOnRopeDataMixin {
  @ModifyExpressionValue(
      method = "setBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/entity/BlockEntity;saveWithId(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private CompoundTag aeroscapes$stripRopeFromCarrySnapshot(CompoundTag tag) {
    return aeroscapes$withoutRope(tag, false);
  }

  @ModifyArg(
      method = "getBlockEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/entity/BlockEntity;loadStatic(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/level/block/entity/BlockEntity;"),
      index = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private CompoundTag aeroscapes$stripPreviouslyCarriedRope(CompoundTag tag) {
    return aeroscapes$withoutRope(tag, true);
  }

  @Unique
  private static CompoundTag aeroscapes$withoutRope(CompoundTag tag, boolean copy) {
    String id = tag.getString("id");
    if (!id.equals("simulated:rope_connector") && !id.equals("simulated:rope_winch")) return tag;
    CompoundTag detached = copy ? tag.copy() : tag;
    detached.remove("OwnStrand");
    detached.remove("HasRopeAttached");
    detached.remove("Strand");
    return detached;
  }
}
