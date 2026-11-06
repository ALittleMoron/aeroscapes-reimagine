package dev.aeroscapes.weathering;

import java.io.FileNotFoundException;
import java.util.function.Predicate;
import net.mehvahdjukaar.moonlight.api.resources.RPUtils;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

/**
 * Reject unresolved model aliases and nonexistent guessed texture paths before native selection.
 */
public final class WeatheringTextures {
  private WeatheringTextures() {}

  public static ResourceLocation find(
      ResourceManager manager, Block block, Predicate<String> nativePredicate)
      throws FileNotFoundException {
    return RPUtils.findFirstBlockTextureLocation(
        manager,
        block,
        candidate -> {
          ResourceLocation location = ResourceLocation.tryParse(candidate);
          return location != null
              && manager.getResource(ResType.TEXTURES.getPath(location)).isPresent()
              && nativePredicate.test(candidate);
        });
  }
}
