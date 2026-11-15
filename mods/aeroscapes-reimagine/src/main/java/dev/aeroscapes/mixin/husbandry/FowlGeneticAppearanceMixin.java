package dev.aeroscapes.mixin.husbandry;

import dev.aeroscapes.husbandry.ChickenGeneticAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "aqario.fowlplay.client.render.entity.CustomChickenRenderer", remap = false)
abstract class FowlGeneticAppearanceMixin {
  @Inject(
      method =
          "getTextureLocation(Lnet/minecraft/world/entity/animal/Chicken;)Lnet/minecraft/resources/ResourceLocation;",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$inheritedAppearance(
      Chicken chicken, CallbackInfoReturnable<ResourceLocation> callback) {
    ResourceLocation texture = ChickenGeneticAppearance.texture(chicken);
    if (texture != null) callback.setReturnValue(texture);
  }
}
