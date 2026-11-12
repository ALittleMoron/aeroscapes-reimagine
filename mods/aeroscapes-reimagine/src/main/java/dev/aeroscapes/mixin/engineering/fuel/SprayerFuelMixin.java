package dev.aeroscapes.mixin.engineering.fuel;

import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerItem;
import com.jesz.createdieselgenerators.content.tools.FueledToolItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ChemicalSprayerItem.class, remap = false)
public abstract class SprayerFuelMixin {
  @Redirect(
      method = "onUseTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$payForShot(
      Level world, Entity projectile, Level level, LivingEntity user, ItemStack stack, int count) {
    boolean spawned = world.addFreshEntity(projectile);
    if (spawned && !(user instanceof Player player && player.isCreative())) {
      var tool = (FueledToolItem) this;
      var fluid = tool.readFluid(stack);
      fluid.shrink(1);
      tool.writeFluid(stack, fluid);
    }
    return spawned;
  }

  @Redirect(
      method = "onUseTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/jesz/createdieselgenerators/content/tools/ChemicalSprayerItem;writeFluid(Lnet/minecraft/world/item/ItemStack;Lnet/neoforged/neoforge/fluids/FluidStack;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$skipPeriodicDebit(
      ChemicalSprayerItem tool, ItemStack stack, FluidStack fluid) {
    // Successful projectile creation already persisted the debit.
  }
}
