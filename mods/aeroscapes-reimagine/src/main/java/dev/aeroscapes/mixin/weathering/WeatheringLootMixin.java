package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.events.ModLootInjects;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ModLootInjects.class, remap = false)
public abstract class WeatheringLootMixin {
  @ModifyArg(
      method = "onLootInject",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/resources/ResourceLocation;equals(Ljava/lang/Object;)Z"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Entity loot IDs became ResourceKeys in 1.21.
  private static Object aeroscapesReimagine$lootLocation(Object compared) {
    return compared instanceof ResourceKey<?> key ? key.location() : compared;
  }
}
