package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.CaveVines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CaveVines.class)
interface ProfessionGlowBerryMixin {
  @ModifyArg(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;<init>(Lnet/minecraft/world/level/ItemLike;I)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$ripePick(int amount, @Local(argsOnly = true) Entity actor) {
    return ProfessionEffects.extraHarvest(actor) ? amount + 1 : amount;
  }
}
