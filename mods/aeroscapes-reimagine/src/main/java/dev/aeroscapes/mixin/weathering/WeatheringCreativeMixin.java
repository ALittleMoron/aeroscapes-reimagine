package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.reg.ModCreativeTab;
import com.ordana.immersive_weathering.reg.ModItems;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModCreativeTab.class, remap = false)
public abstract class WeatheringCreativeMixin {
  @Inject(method = "addItems", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The registered maintenance tool was omitted upstream.
  private static void aeroscapesReimagine$steelWool(
      RegHelper.ItemToTabEvent event, CallbackInfo callback) {
    var tab =
        ModCreativeTab.MOD_TAB == null
            ? CreativeModeTabs.TOOLS_AND_UTILITIES
            : ModCreativeTab.MOD_TAB.getKey();
    event.add(tab, ModItems.STEEL_WOOL.get());
  }
}
