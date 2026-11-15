package dev.aeroscapes.mixin.navigation;

import dev.aeroscapes.navigation.MapLayerSelection;
import dev.aeroscapes.navigation.VerticalMapNavigation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.config.util.MinimapConfigClientUtils;

@Mixin(value = MinimapWriter.class, remap = false)
public abstract class MinimapLayersMixin {
  @Shadow private int loadingCaveMapsDepth;
  @Shadow private int loadingCaving;
  @Shadow private ResourceKey<Level> loadingDimension;

  @Inject(method = "getCaving", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the pinned native scan selection.
  private void aeroscapes$selectLayer(
      double playerX,
      double playerY,
      double playerZ,
      Level level,
      CallbackInfoReturnable<Integer> callback) {
    var view = VerticalMapNavigation.view();
    if (!VerticalMapNavigation.usesLayers(level, view)
        || !MinimapConfigClientUtils.getEffectiveCaveModeAllowed()) return;
    if (view != null) callback.setReturnValue(view.ceiling());
  }

  @Redirect(
      method = "writeChunk",
      at =
          @At(
              value = "FIELD",
              target = "Lxaero/common/minimap/write/MinimapWriter;loadingCaveMapsDepth:I",
              opcode = Opcodes.PUTFIELD))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Redirects the native layer-depth field write.
  private void aeroscapes$layerDepth(MinimapWriter writer, int configuredDepth) {
    var view = VerticalMapNavigation.view();
    loadingCaveMapsDepth =
        view != null
                && view.mode() != MapLayerSelection.Mode.NATIVE
                && loadingDimension == Level.OVERWORLD
                && loadingCaving != Integer.MAX_VALUE
            ? MapLayerSelection.DEPTH
            : configuredDepth;
  }
}
