package dev.aeroscapes.mixin.startup;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.createmod.catnip.render.StitchedSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StitchedSprite.class, remap = false)
abstract class PonderSpriteShiftMixin {
  @Shadow @Final protected ResourceLocation atlasLocation;
  @Shadow @Final protected ResourceLocation location;

  @Unique
  private static final Map<ResourceLocation, Map<ResourceLocation, TextureAtlasSprite>>
      AEROSCAPES$STITCHED_TEXTURES = new ConcurrentHashMap<>();

  @Redirect(
      method =
          "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Object aeroscapes$synchronizedAtlasList(
      Map<Object, List<?>> atlases, Object atlas, Function<Object, List<?>> createEntries) {
    synchronized (atlases) {
      // The constructor appends after this lookup, so the list also needs synchronization.
      return atlases.computeIfAbsent(
          atlas, key -> Collections.synchronizedList(createEntries.apply(key)));
    }
  }

  @Redirect(
      method = "onTextureStitchPost(Lnet/minecraft/client/renderer/texture/TextureAtlas;)V",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Object aeroscapes$atlasSnapshot(Map<?, ? extends List<?>> atlases, Object atlas) {
    List<?> entries;
    synchronized (atlases) {
      entries = atlases.get(atlas);
    }
    // The synchronized list copies its entries under its own monitor before sprite loading.
    return entries == null ? null : List.copyOf(entries);
  }

  @Inject(
      method = "onTextureStitchPost(Lnet/minecraft/client/renderer/texture/TextureAtlas;)V",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$publishStitchedTextures(
      TextureAtlas atlas, CallbackInfo callback) {
    // NeoForge exposes the immutable sprite map from the completed upload.
    AEROSCAPES$STITCHED_TEXTURES.put(atlas.location(), atlas.getTextures());
  }

  @Inject(
      method = "get()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
      at = @At("RETURN"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$resolveLateSprite(CallbackInfoReturnable<TextureAtlasSprite> callback) {
    if (callback.getReturnValue() != null) return;
    var textures = AEROSCAPES$STITCHED_TEXTURES.get(atlasLocation);
    if (textures != null)
      callback.setReturnValue(
          textures.getOrDefault(location, textures.get(MissingTextureAtlasSprite.getLocation())));
  }
}
