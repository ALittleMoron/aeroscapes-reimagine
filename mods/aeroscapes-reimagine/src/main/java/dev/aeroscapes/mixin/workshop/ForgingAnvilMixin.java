package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.ManualForgeAnvil;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import dev.aeroscapes.progression.PracticeActions;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity",
    remap = false)
abstract class ForgingAnvilMixin implements ManualForgeAnvil {
  @Shadow
  public abstract Optional<RecipeHolder<?>> getCurrentRecipeHolder();

  @Override
  public boolean aeroscapes$prepareStrike(Player actor) {
    var level = ((BlockEntity) (Object) this).getLevel();
    if (level == null || level.isClientSide()) return true;
    var recipe = getCurrentRecipeHolder();
    if (recipe.isPresent()
        && !WorkshopManufacture.allowed(
            actor, recipe.get().value().getResultItem(level.registryAccess()), true)) {
      WorkshopManufacture.explain(actor);
      return false;
    }
    ((ForgePlayerAccess) this).aeroscapes$forgePlayer(actor);
    return true;
  }

  @Inject(
      method = {"increaseForgingProgress", "craftItem"},
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$requireTraining(CallbackInfo callback) {
    var level = ((BlockEntity) (Object) this).getLevel();
    if (level == null || level.isClientSide()) return;
    var recipe = getCurrentRecipeHolder();
    if (recipe.isEmpty()) return;
    var actor = ((ForgePlayerAccess) this).aeroscapes$forgePlayer();
    if (WorkshopManufacture.allowed(
        actor, recipe.get().value().getResultItem(level.registryAccess()), true)) return;
    WorkshopManufacture.explain(actor);
    callback.cancel();
  }

  @ModifyArg(
      method = "craftItem",
      at =
          @At(
              value = "INVOKE",
              ordinal = 0,
              target =
                  "Lnet/stirdrem/overgeared/util/items/ItemStackHandler;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$completeWorkshopItem(ItemStack result) {
    var actor = ((ForgePlayerAccess) this).aeroscapes$forgePlayer();
    if (WorkshopManufacture.finishedEquipment(result)) PracticeActions.awardWorkshop(actor);
    WorkshopQuality.finish(actor, result);
    return result;
  }
}
