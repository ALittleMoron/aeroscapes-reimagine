package dev.aeroscapes.mixin.progression;

import com.agricraft.agricraft.api.crop.AgriCrop;
import com.agricraft.agricraft.common.block.CropBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.races.ProfessionEffects;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CropBlock.class, remap = false)
abstract class ProfessionAgriCropMixin {
  @WrapOperation(
      method = {"useWithoutItem", "rightClickLogic"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/agricraft/agricraft/api/crop/AgriCrop;harvest(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/LivingEntity;)Z"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$handHarvest(
      AgriCrop crop, Consumer<ItemStack> drop, LivingEntity actor, Operation<Boolean> original) {
    if (!crop.isFullyGrown()) return original.call(crop, drop, actor);
    var produced = new ArrayList<ItemStack>();
    boolean harvested = original.call(crop, (Consumer<ItemStack>) produced::add, actor);
    if (harvested && !produced.isEmpty()) {
      PracticeActions.harvest(actor);
      if (ProfessionEffects.extraHarvest(actor)) produced.getFirst().grow(1);
    }
    produced.forEach(drop);
    return harvested;
  }

  @WrapOperation(
      method = "getDrops",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/agricraft/agricraft/api/crop/AgriCrop;getHarvestProducts(Ljava/util/function/Consumer;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$brokenHarvest(
      AgriCrop crop,
      Consumer<ItemStack> drop,
      Operation<Void> original,
      @Local(argsOnly = true) LootParams.Builder context) {
    var actor = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
    if (!crop.isFullyGrown() || !ProfessionEffects.extraHarvest(actor)) {
      original.call(crop, drop);
      return;
    }
    var produced = new ArrayList<ItemStack>();
    original.call(crop, (Consumer<ItemStack>) produced::add);
    if (!produced.isEmpty()) produced.getFirst().grow(1);
    produced.forEach(drop);
  }
}
