package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.engineering.SwitchLinkPearlReforge;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "wile.rsgauges.blocks.SwitchBlock$SwitchTileEntity", remap = false)
abstract class SwitchLinkGradePersistenceMixin {
  @Shadow protected ArrayList<SwitchLinkPearlReforge.StoredGrade> links_;

  @Inject(
      method = "assignSwitchLink",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lwile/rsgauges/detail/SwitchLink;initializeTarget(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the successful assignment has appended a link.
  private void aeroscapes$rememberGrade(
      Level level, BlockPos source, ItemStack pearl, CallbackInfoReturnable<?> callback) {
    links_.getLast().aeroscapes$setGrade(SwitchLinkPearlReforge.grade(pearl));
  }

  @WrapOperation(
      method = "write",
      at =
          @At(
              value = "INVOKE",
              target = "Lwile/rsgauges/detail/SwitchLink;toNbt()Lnet/minecraft/nbt/CompoundTag;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private CompoundTag aeroscapes$writeGrade(@Coerce Object link, Operation<CompoundTag> original) {
    CompoundTag linkData = original.call(link);
    String grade = ((SwitchLinkPearlReforge.StoredGrade) link).aeroscapes$getGrade();
    if (!grade.isEmpty()) linkData.putString(SwitchLinkPearlReforge.GRADE_TAG, grade);
    return linkData;
  }
}
