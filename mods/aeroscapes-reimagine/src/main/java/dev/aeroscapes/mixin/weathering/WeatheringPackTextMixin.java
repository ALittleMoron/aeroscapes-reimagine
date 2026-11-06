package dev.aeroscapes.mixin.weathering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Pack.class)
public abstract class WeatheringPackTextMixin {
  @Shadow
  public abstract String getId();

  @ModifyReturnValue(
      method = {"getTitle", "getDescription"},
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Optional-pack metadata uses two literal captions.
  private Component aeroscapesReimagine$packCaption(Component original) {
    if (!getId().equals("immersive_weathering:visual_waxed_iron_items")) return original;
    String key =
        switch (original.getString()) {
          case "Visual Waxed Iron Items" ->
              "aeroscapes_reimagine.weathering.visual_waxed_iron_items";
          case "Wax overlay on Waxed Iron block items." ->
              "aeroscapes_reimagine.weathering.visual_waxed_iron_items.description";
          default -> null;
        };
    return key == null ? original : Component.translatable(key).withStyle(original.getStyle());
  }
}
