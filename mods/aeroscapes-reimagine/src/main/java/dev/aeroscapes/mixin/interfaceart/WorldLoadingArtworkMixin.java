package dev.aeroscapes.mixin.interfaceart;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("PMD.UnusedPrivateMethod")
@Mixin(Screen.class)
public abstract class WorldLoadingArtworkMixin {
  @WrapOperation(
      method = "renderBackground",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/Screen;renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"))
  private void aeroscapes$loadingAtlas(
      Screen screen, GuiGraphics graphics, Operation<Void> original) {
    if (screen instanceof LevelLoadingScreen && InterfaceArt.available(InterfaceArt.ATLAS)) {
      InterfaceArt.drawAtlas(graphics, graphics.guiWidth(), graphics.guiHeight(), 1.0F);
    } else {
      original.call(screen, graphics);
    }
  }
}
