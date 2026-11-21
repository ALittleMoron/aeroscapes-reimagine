package dev.aeroscapes.mixin.hex;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "at.petrak.hexcasting.client.RegisterClientStuff", remap = false)
abstract class QuenchedBlockModelsMixin {
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin invokes this argument modifier at model bake.
  @ModifyArg(
      method = "onModelBake",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
      index = 0)
  private static Object aeroscapes$resolveRegisteredVariant(Object location) {
    return location instanceof ResourceLocation resource
        ? ModelResourceLocation.standalone(resource)
        : location;
  }
}
