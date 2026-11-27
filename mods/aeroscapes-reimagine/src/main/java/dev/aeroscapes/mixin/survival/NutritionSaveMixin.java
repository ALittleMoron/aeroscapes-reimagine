package dev.aeroscapes.mixin.survival;

import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionDataStorage.class)
abstract class NutritionSaveMixin {
  @Shadow @Final private Map<String, SavedData> cache;

  @Inject(method = "save", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$saveNutrition(CallbackInfo callback) {
    // Native saturation decay changes nutrient values without marking the world record dirty.
    if (cache.get("nutritionalbalance") instanceof PlayerNutritionData nutrition)
      nutrition.setDirty();
  }
}
