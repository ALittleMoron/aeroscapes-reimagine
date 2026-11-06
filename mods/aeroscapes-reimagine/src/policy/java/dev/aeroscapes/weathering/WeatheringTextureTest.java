package dev.aeroscapes.weathering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.mehvahdjukaar.moonlight.api.client.TextureCache;
import net.mehvahdjukaar.moonlight.api.resources.RPUtils;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.LoadingModList;

/** Exercise native model-texture selection with the alias shape that broke frosted leaf piles. */
public final class WeatheringTextureTest {
  private WeatheringTextureTest() {}

  public static void main(String[] arguments) throws IOException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path pack = Files.createTempDirectory("weathering-texture-contract-");
    try {
      write(
          pack,
          "minecraft/blockstates/acacia_leaves.json",
          "{\"variants\":{\"\":{\"model\":\"chipped:block/frosted_acacia_leaves\"}}}");
      write(
          pack,
          "chipped/models/block/frosted_acacia_leaves.json",
          """
          {"textures":{
            "all":"chipped:block/acacia_leaves/frosted_acacia_leaves",
            "top":"chipped:block/acacia_leaves/frosted_acacia_leaves_top",
            "bottom":"#top"}}
          """);
      copyTexture(
          pack,
          "chipped/textures/block/acacia_leaves/frosted_acacia_leaves.png",
          "/assets/chipped/textures/block/acacia_leaves/frosted_acacia_leaves.png");
      copyTexture(
          pack,
          "chipped/textures/block/acacia_leaves/frosted_acacia_leaves_top.png",
          "/assets/chipped/textures/block/acacia_leaves/frosted_acacia_leaves_top.png");
      write(
          pack,
          "minecraft/blockstates/oak_log.json",
          "{\"variants\":{\"\":{\"model\":\"regression:block/aliased_log\"}}}");
      write(
          pack,
          "regression/models/block/aliased_log.json",
          """
          {"textures":{"side":"regression:block/log_side","particle":"#side"}}
          """);
      copyTexture(
          pack,
          "regression/textures/block/log_side.png",
          "/assets/minecraft/textures/block/oak_log.png");
      var location =
          new PackLocationInfo(
              "weathering-texture-regression",
              Component.literal("Weathering texture regression"),
              PackSource.BUILT_IN,
              Optional.empty());
      try (var manager =
          new MultiPackResourceManager(
              PackType.CLIENT_RESOURCES, List.of(new PathPackResources(location, pack)))) {
        Predicate<String> leaves =
            candidate -> {
              String path = ResourceLocation.parse(candidate).getPath();
              return !path.contains("_bushy")
                  && !path.contains("_snow")
                  && !path.contains("_overlay");
            };
        TextureCache.clear();
        boolean nativeMissing;
        try {
          var selected =
              RPUtils.findFirstBlockTextureLocation(manager, Blocks.ACACIA_LEAVES, leaves);
          nativeMissing = manager.getResource(ResType.TEXTURES.getPath(selected)).isEmpty();
        } catch (ResourceLocationException failure) {
          nativeMissing = true;
        }
        require(nativeMissing, "The unguarded native alias/guess reproduces the missing texture");
        TextureCache.clear();
        ResourceLocation selected = WeatheringTextures.find(manager, Blocks.ACACIA_LEAVES, leaves);
        require(
            selected.getPath().startsWith("block/acacia_leaves/frosted_acacia_leaves"),
            "The selected leaf texture retains its original species");
        require(
            manager.getResource(ResType.TEXTURES.getPath(selected)).isPresent(),
            "The selected leaf texture exists");
        decodes(manager, selected);
        TextureCache.clear();
        var bark =
            WeatheringTextures.find(
                manager, Blocks.OAK_LOG, candidate -> !candidate.contains("top"));
        require(
            bark.equals(ResourceLocation.parse("regression:block/log_side")),
            "Bark uses the real side texture instead of a model alias or guessed path");
        decodes(manager, bark);
      }
    } finally {
      TextureCache.clear();
      try (var paths = Files.walk(pack)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
    }
    System.out.println("Weathering texture aliases, real PNG selection and bark sides passed");
  }

  private static void write(Path pack, String relative, String contents) throws IOException {
    Path file = pack.resolve("assets").resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, contents);
  }

  private static void copyTexture(Path pack, String relative, String original) throws IOException {
    Path file = pack.resolve("assets").resolve(relative);
    Files.createDirectories(file.getParent());
    try (var stream = WeatheringTextureTest.class.getResourceAsStream(original)) {
      if (stream == null) throw new IOException("Missing pinned source texture: " + original);
      Files.copy(stream, file);
    }
  }

  private static void decodes(MultiPackResourceManager manager, ResourceLocation texture)
      throws IOException {
    try (var stream = manager.open(ResType.TEXTURES.getPath(texture))) {
      var image = ImageIO.read(stream);
      require(image != null && image.getWidth() > 0 && image.getHeight() > 0, "Usable native PNG");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
