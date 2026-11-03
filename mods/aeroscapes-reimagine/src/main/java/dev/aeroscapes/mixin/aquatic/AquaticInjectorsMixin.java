package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.aquatic.AquaticWater;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(
    targets = "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource",
    remap = false)
public abstract class AquaticInjectorsMixin {
  @ModifyVariable(method = "applyInjectors", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Other providers retain their original injector instances.
  private <T> Map<ResourceLocation, T> aeroscapes$boundedHabitats(
      Map<ResourceLocation, T> injectors) {
    if (!AquaticWater.ownedSource(((AquaticInjectorAccess) this).aeroscapes$delegate()))
      return injectors;
    Map<ResourceLocation, T> selected = new HashMap<>(injectors);
    selected.keySet().removeIf(key -> "hybrid_aquatic".equals(key.getNamespace()));
    return selected;
  }
}
