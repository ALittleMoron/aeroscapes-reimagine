package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.ImmersiveWeathering;
import com.ordana.immersive_weathering.dynamicpack.ClientDynamicResourcesHandler;
import dev.aeroscapes.weathering.WeatheringTextures;
import java.io.FileNotFoundException;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ClientDynamicResourcesHandler.class, remap = false)
public abstract class WeatheringTextureMixin {
  @Redirect(
      method = {"lambda$regenerateDynamicAssets$1", "lambda$regenerateDynamicAssets$3"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/mehvahdjukaar/moonlight/api/resources/RPUtils;findFirstBlockTextureLocation(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/world/level/block/Block;Ljava/util/function/Predicate;)Lnet/minecraft/resources/ResourceLocation;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Both thin models and heavy textures must select an existing PNG.
  private ResourceLocation aeroscapesReimagine$leafTexture(
      ResourceManager manager, Block block, Predicate<String> nativePredicate)
      throws FileNotFoundException {
    try {
      return WeatheringTextures.find(manager, block, nativePredicate);
    } catch (FileNotFoundException failure) {
      if (block == Blocks.OAK_LEAVES) throw failure;
      ImmersiveWeathering.LOGGER.warn(
          "No usable leaf texture for {}; using oak for its leaf pile", block);
      return WeatheringTextures.find(manager, Blocks.OAK_LEAVES, candidate -> true);
    }
  }

  @Redirect(
      method = "lambda$regenerateDynamicAssets$6",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/mehvahdjukaar/moonlight/api/resources/RPUtils;findFirstBlockTextureLocation(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/world/level/block/Block;Ljava/util/function/Predicate;)Lnet/minecraft/resources/ResourceLocation;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Bark generation has the same model-alias boundary.
  private ResourceLocation aeroscapesReimagine$barkTexture(
      ResourceManager manager, Block block, Predicate<String> nativePredicate)
      throws FileNotFoundException {
    return WeatheringTextures.find(manager, block, nativePredicate);
  }
}
