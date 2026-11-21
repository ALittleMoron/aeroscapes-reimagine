package dev.aeroscapes.mixin.hex;

import dev.aeroscapes.interfaceart.InterfaceArt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "at.petrak.hexcasting.client.gui.GuiSpellcasting", remap = false)
abstract class CastingGridPaletteMixin {
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mixin invokes this modifier while drawing the grid.
  @ModifyArgs(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lat/petrak/hexcasting/client/render/RenderLib;drawSpot(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec2;FFFFF)V"))
  private void aeroscapes$colorGuideDots(Args args) {
    if (!InterfaceArt.available(InterfaceArt.ATLAS)) return;
    float brightness = args.get(6);
    args.set(3, .61f + .29f * brightness);
    args.set(4, .47f + .28f * brightness);
    args.set(5, .29f + .21f * brightness);
  }
}
