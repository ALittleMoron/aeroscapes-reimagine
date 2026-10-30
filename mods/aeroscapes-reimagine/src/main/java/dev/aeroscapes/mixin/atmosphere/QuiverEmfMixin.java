package dev.aeroscapes.mixin.atmosphere;

import com.google.gson.Gson;
import dev.aeroscapes.atmosphere.QuiverCompatibility;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.EMFModel_ID;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.utils.EMFDirectoryHandler;

@Mixin(value = EMFJemData.class, remap = false)
public abstract class QuiverEmfMixin {
  @Inject(method = "prepare", at = @At("HEAD"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native EMF prepares each model after loading its JEM.
  private void aeroscapes$composeQuiver(
      EMFDirectoryHandler directory, EMFModel_ID modelId, CallbackInfo callback) {
    String mob = directory.rawFileName;
    if (!mob.equals("skeleton") && !mob.equals("stray") && !mob.equals("bogged")) return;
    var location = ResourceLocation.parse(directory.getFinalFileLocation());
    if (!location.equals(ResourceLocation.withDefaultNamespace("optifine/cem/" + mob + ".jem")))
      return;
    var resources = Minecraft.getInstance().getResourceManager().getResourceStack(location);
    if (resources.size() < 2
        || !resources.getLast().sourcePackId().equals("file/FA+Quivers-v2.2.zip")) return;
    var base = resources.get(resources.size() - 2);
    if (!base.sourcePackId().equals("file/FreshAnimations_v1.11.1_1.20+.zip")) return;
    // FA 1.11 merged family animations and added flags and bogged mushroom parts.
    try (var reader = base.openAsReader()) {
      EMFJemData original = new Gson().fromJson(reader, EMFJemData.class);
      if (original == null || original.models == null || original.models.isEmpty())
        throw new IllegalStateException("Missing Fresh Animations model: " + location);
      QuiverCompatibility.compose((EMFJemData) (Object) this, original, mob);
    } catch (IOException error) {
      throw new IllegalStateException(
          "Cannot compose Quivers with Fresh Animations: " + location, error);
    }
  }
}
