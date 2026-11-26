package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.ManualForgeAnvil;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import dev.aeroscapes.progression.PracticeActions;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
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
    var anvil = (BlockEntity) (Object) this;
    if (!result.isEmpty()
        && actor instanceof ServerPlayer
        && !(actor instanceof FakePlayer)
        && anvil.getLevel() instanceof ServerLevel level) {
      var pos = anvil.getBlockPos();
      level.sendParticles(
          ParticleTypes.SMALL_FLAME,
          pos.getX() + 0.5,
          pos.getY() + 0.9,
          pos.getZ() + 0.5,
          6,
          0.17,
          0.09,
          0.17,
          0.018);
    }
    return result;
  }
}
