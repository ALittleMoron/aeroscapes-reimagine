package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.SwitchLinkPearlReforge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "wile.rsgauges.detail.SwitchLink", remap = false)
abstract class SwitchLinkPearlReforgeMixin implements SwitchLinkPearlReforge.StoredGrade {
  @Unique private String aeroscapes$grade = "";

  @Shadow
  public abstract int distance(BlockPos position);

  @Override
  public String aeroscapes$getGrade() {
    return aeroscapes$grade;
  }

  @Override
  public void aeroscapes$setGrade(String grade) {
    aeroscapes$grade = grade;
  }

  @ModifyExpressionValue(
      method =
          "trigger(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Lwile/rsgauges/detail/SwitchLink$RequestResult;",
      at =
          @At(
              value = "INVOKE",
              target = "Lwile/rsgauges/detail/SwitchLink;isTooFar(Lnet/minecraft/core/BlockPos;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$manualRange(
      boolean original,
      @Local(argsOnly = true) BlockPos source,
      @Local(argsOnly = true) Player player) {
    if (player == null) return original;
    int nativeLimit = GaugesLinkConfigAccess.aeroscapes$maxLinkDistance();
    int limit = SwitchLinkPearlReforge.manualDistanceLimit(player.getMainHandItem(), nativeLimit);
    return limit == nativeLimit ? original : distance(source) > limit;
  }

  @Inject(method = "fromNbt", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$readGrade(
      CompoundTag linkData, CallbackInfoReturnable<SwitchLinkPearlReforge.StoredGrade> callback) {
    if (linkData == null) return;
    String grade =
        SwitchLinkPearlReforge.knownGrade(linkData.getString(SwitchLinkPearlReforge.GRADE_TAG));
    if (!grade.isEmpty()) callback.getReturnValue().aeroscapes$setGrade(grade);
  }

  @Inject(method = "toSwitchLinkPearl", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$restoreGrade(CallbackInfoReturnable<ItemStack> callback) {
    SwitchLinkPearlReforge.restoreGrade(callback.getReturnValue(), aeroscapes$grade);
  }
}
