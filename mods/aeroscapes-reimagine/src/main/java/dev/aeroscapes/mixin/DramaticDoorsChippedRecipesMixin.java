package dev.aeroscapes.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Guards Dramatic Doors 3.3.2 conversions against absent Chipped 4.0.2 variants. */
@Pseudo
@Mixin(targets = "com.fizzware.dramaticdoors.neoforge.compat.DDCompatRecipe", remap = false)
public abstract class DramaticDoorsChippedRecipesMixin {
  @ModifyArg(
      method =
          "createTallDoorRecipe(Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)V",
      at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned Mixin argument injection.
  private static Object aeroscapesReimagine$requireChippedItem(
      Object recipe, @Local(argsOnly = true) ResourceLocation ingredient) {
    if (!ingredient.getNamespace().equals("chipped") || !(recipe instanceof JsonObject json)) {
      return recipe;
    }
    // Registration is incomplete here; NeoForge evaluates the condition during recipe loading.
    JsonArray conditions = json.getAsJsonArray("neoforge:conditions");
    if (conditions == null) {
      conditions = new JsonArray();
      json.add("neoforge:conditions", conditions);
    }
    JsonObject condition = new JsonObject();
    condition.addProperty("type", "neoforge:item_exists");
    condition.addProperty("item", ingredient.toString());
    conditions.add(condition);
    return recipe;
  }
}
