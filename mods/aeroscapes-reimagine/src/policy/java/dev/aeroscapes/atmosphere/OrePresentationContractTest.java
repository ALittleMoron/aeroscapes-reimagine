package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;

/** Exercises native glyph advances and tooltip targets without starting a client. */
public final class OrePresentationContractTest {
  private OrePresentationContractTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    List<GlyphProvider> providers = new ArrayList<>();
    try {
      loadNativeBitmaps(providers);
      frequencyBounds(nativeFont(providers));
    } finally {
      providers.forEach(GlyphProvider::close);
    }
    nativeTooltipBoundaries();
    oilRockColors();
    System.out.println(
        "Ore presentation: native EN/RU font bounds and tooltip boundary bubbling passed");
  }

  private static void oilRockColors() throws IOException {
    try (var stream =
        OrePresentationContractTest.class.getResourceAsStream(
            "/assets/aeroscapes_reimagine/textures/block/oil_stain.png")) {
      if (stream == null) throw new IOException("Missing oil stain mask");
      var mask = javax.imageio.ImageIO.read(stream);
      if (mask == null) throw new IOException("Invalid oil stain mask");
      int unchanged = 0;
      int marked = 0;
      for (int y = 0; y < mask.getHeight(); y++)
        for (int x = 0; x < mask.getWidth(); x++) {
          int stain = FastColor.ABGR32.fromArgb32(mask.getRGB(x, y));
          for (int stone : new int[] {0xFF253C9A, 0xFFC6BEB3, 0xFF1B1D25}) {
            int actual = OilRockSprites.blend(stone, stain);
            require(FastColor.ABGR32.alpha(actual) == 255, "Oil-bearing rock stays opaque");
            if (FastColor.ABGR32.alpha(stain) == 0) {
              require(actual == stone, "Unstained pixels preserve the exact host color");
            } else {
              require(actual != stone, "Oil stains remain visible on distinct native palettes");
            }
          }
          if (FastColor.ABGR32.alpha(stain) == 0) unchanged++;
          else marked++;
        }
      require(
          unchanged > mask.getWidth() * mask.getHeight() * .7 && marked > 0,
          "The native rock remains the background around isolated oil stains");
      require(
          OilRockSprites.blend(0xFF253C9A, 0xFF0B121F) == 0xFF0B121F,
          "Opaque stain channels retain their order");
    }
  }

  private static void frequencyBounds(Font font) {
    for (String caption :
        List.of(
            "50 попыток в чанке",
            "Примерно 1 попытка в 20 чанках",
            "Примерно 1 попытка в 180 чанках",
            "От 1 до 20 попыток, примерно в каждом 180-м чанке",
            "50 attempts per chunk",
            "About 1 attempt in 180 chunks",
            "A much longer player-authored resource-pack caption with preserved styling and wording")) {
      Component text = Component.literal(caption);
      var lines = font.split(text, 156);
      int footerHeight = OreRecipeLayout.footerHeight(font, text, 160);
      require(
          footerHeight >= lines.size() * font.lineHeight + 8, "All wrapped lines fit below slots");
      for (var line : lines) {
        int width = font.width(line);
        require(width <= 156 && 158 - width >= 2, "Localized line stays inside recipe bounds");
      }
    }
    require(OreRecipeLayout.footerHeight(font, null, 160) == 0, "No frequency needs no footer");
    require(font.width("WWW") > font.width("iii"), "Native metrics are proportional");
    require(
        font.width("Примерно 1 попытка в 180 чанках") > 160,
        "The original right-aligned row overflows with actual Russian glyph advances");
  }

  private static void loadNativeBitmaps(List<GlyphProvider> providers) throws IOException {
    URL definition =
        OrePresentationContractTest.class
            .getClassLoader()
            .getResource("assets/minecraft/font/include/default.json");
    if (definition == null)
      throw new IOException("Minecraft font resources are missing from the test classpath");
    try (var reader = new InputStreamReader(definition.openStream(), StandardCharsets.UTF_8)) {
      for (var entry :
          JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers")) {
        var object = entry.getAsJsonObject();
        if (!object.get("type").getAsString().equals("bitmap")) continue;
        var bitmap =
            BitmapProvider.Definition.CODEC.codec().parse(JsonOps.INSTANCE, object).getOrThrow();
        providers.add(bitmap.unpack().left().orElseThrow().load(classpathResources()));
      }
    }
  }

  private static Font nativeFont(List<GlyphProvider> providers) {
    FontSet metrics =
        new FontSet(null, ResourceLocation.withDefaultNamespace("default")) {
          @Override
          public GlyphInfo getGlyphInfo(int codePoint, boolean filterFishyGlyphs) {
            if (codePoint == ' ') return (GlyphInfo.SpaceGlyphInfo) () -> 4;
            for (GlyphProvider provider : providers) {
              GlyphInfo glyph = provider.getGlyph(codePoint);
              if (glyph != null) return glyph;
            }
            throw new AssertionError("No native bitmap glyph for " + codePoint);
          }
        };
    return new Font(ignored -> metrics, false);
  }

  private static ResourceManager classpathResources() {
    return new ResourceManager() {
      @Override
      public Set<String> getNamespaces() {
        return Set.of("minecraft");
      }

      @Override
      public Optional<Resource> getResource(ResourceLocation location) {
        URL resource =
            OrePresentationContractTest.class
                .getClassLoader()
                .getResource("assets/" + location.getNamespace() + "/" + location.getPath());
        return resource == null
            ? Optional.empty()
            : Optional.of(new Resource(null, resource::openStream));
      }

      @Override
      public List<Resource> getResourceStack(ResourceLocation location) {
        return getResource(location).stream().toList();
      }

      @Override
      public Map<ResourceLocation, Resource> listResources(
          String path, Predicate<ResourceLocation> predicate) {
        return Map.of();
      }

      @Override
      public Map<ResourceLocation, List<Resource>> listResourceStacks(
          String path, Predicate<ResourceLocation> predicate) {
        return Map.of();
      }

      @Override
      public Stream<PackResources> listPacks() {
        return Stream.empty();
      }
    };
  }

  private static void nativeTooltipBoundaries() throws ReflectiveOperationException {
    Class<?> manager = Class.forName("com.stalemated.sts.scroll.TooltipScrollManager");
    Field target = field(manager, "targetScroll");
    Field start = field(manager, "startScroll");
    Field maximum = field(manager, "maxScroll");
    Field lastRender = field(manager, "lastRenderTime");
    Method scroll = manager.getDeclaredMethod("scroll", double.class);
    for (var sample :
        List.of(
            new ScrollCase(0, 90, 1.0, 0, false),
            new ScrollCase(90, 90, -1.0, 90, false),
            new ScrollCase(45, 90, 1.0, 30, true),
            new ScrollCase(45, 90, -1.0, 60, true),
            new ScrollCase(45, 90, 0.01, 45, false),
            new ScrollCase(45, 90, -0.01, 45, false),
            new ScrollCase(45, 90, 0.2, 42, true),
            new ScrollCase(45, 90, -0.2, 48, true),
            new ScrollCase(45, 90, 100.0, 0, true),
            new ScrollCase(0, 0, -1.0, 0, false))) {
      target.setInt(null, sample.before());
      start.setInt(null, sample.before());
      maximum.setInt(null, sample.maximum());
      lastRender.setLong(null, System.currentTimeMillis());
      boolean consumed =
          TooltipScrollBoundary.scroll(
              sample.amount(), () -> readTarget(target), amount -> invokeScroll(scroll, amount));
      require(consumed == sample.consumed(), "Only native movement consumes the wheel: " + sample);
      require(
          target.getInt(null) == sample.after(),
          "Native direction and fractional deltas are preserved");
    }
    target.setInt(null, 45);
    start.setInt(null, 45);
    maximum.setInt(null, 90);
    lastRender.setLong(null, System.currentTimeMillis() - 1000);
    require(
        !TooltipScrollBoundary.scroll(
            1.0, () -> readTarget(target), amount -> invokeScroll(scroll, amount)),
        "Expired tooltip does not consume container scrolling");
    require(target.getInt(null) == 45, "Expired tooltip position is unchanged");
  }

  private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static int readTarget(Field target) {
    try {
      return target.getInt(null);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static boolean invokeScroll(Method scroll, double amount) {
    try {
      return Boolean.TRUE.equals(scroll.invoke(null, amount));
    } catch (IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record ScrollCase(int before, int maximum, double amount, int after, boolean consumed) {}
}
