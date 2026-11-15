package dev.aeroscapes.mixin.navigation;

import dev.aeroscapes.navigation.MapLayerSelection;
import dev.aeroscapes.navigation.VerticalMapNavigation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.MapProcessor;
import xaero.map.config.util.WorldMapClientConfigUtils;
import xaero.map.world.MapWorld;

@Mixin(value = MapProcessor.class, remap = false)
public abstract class WorldMapLayersMixin {
  @Shadow private ClientLevel world;
  @Shadow private MapWorld mapWorld;
  @Shadow private int currentCaveLayer;

  @Inject(method = "updateCaveStart", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Invoked before Xaero selects the active cache layer.
  private void aeroscapes$selectLayer(CallbackInfo callback) {
    var view = VerticalMapNavigation.view();
    if (!aeroscapes$usesLayers(view)) return;
    currentCaveLayer = view.ceiling() >> 4;
    mapWorld
        .getCurrentDimension()
        .getLayeredMapRegions()
        .getLayer(currentCaveLayer)
        .setCaveStart(view.ceiling());
    callback.cancel();
  }

  @Inject(method = "getCaveModeDepthConfig", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Supplies the native writer's bounded scan depth.
  private void aeroscapes$layerDepth(CallbackInfoReturnable<Integer> callback) {
    if (aeroscapes$usesLayers(VerticalMapNavigation.view()))
      callback.setReturnValue(MapLayerSelection.DEPTH);
  }

  private boolean aeroscapes$usesLayers(VerticalMapNavigation.View view) {
    return VerticalMapNavigation.usesLayers(world, view)
        && mapWorld != null
        && mapWorld.getCurrentDimension() != null
        && mapWorld.getCurrentDimension().getDimId() == Level.OVERWORLD
        && WorldMapClientConfigUtils.getEffectiveCaveModeAllowed();
  }
}
