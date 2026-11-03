package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.client.TideItemModelProperties;
import com.li64.tide.registries.TideItems;
import net.minecraft.client.renderer.item.ItemProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TideItemModelProperties.class)
public abstract class TideRodPropertiesMixin {
  @Inject(method = "registerAll", at = @At("TAIL"), remap = false)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native registration list omits only Midas.
  private static void aeroscapes$midasCast(CallbackInfo callback) {
    ItemProperties.register(
        TideItems.MIDAS_FISHING_ROD,
        TideItemModelProperties.CAST_PROPERTY,
        TideItemModelProperties.CAST_FUNCTION);
  }
}
