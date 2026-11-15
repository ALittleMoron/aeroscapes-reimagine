package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Exercises source masks, surface motion, ingredient volume, vapor and the native vertex consumer.
 */
public final class BucketContentRenderingTest {
  private static final BucketContentRendering.Rect CONTENT =
      new BucketContentRendering.Rect(4, 4, 12, 10);
  private static final BucketContentRendering.Rect BASE =
      new BucketContentRendering.Rect(4, 4, 7, 6);

  private BucketContentRenderingTest() {}

  public static void main(String[] arguments) throws Throwable {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    profiles();
    try (var sprite = new Sprite("contents", 16);
        var highlight = new Sprite("highlight", 1)) {
      motion(sprite);
      plainLiquidMaterials(sprite, highlight);
      masks(sprite);
      solids(sprite);
      ingredients(sprite);
      nativeSoupGarnish();
      steam(sprite, highlight);
      emission(sprite);
      fallback(sprite);
    }
    System.out.println(
        "Native bucket contents: validated profiles, masked materials, liquid motion, ingredient"
            + " volume, frame-specific vapor and emitted light passed");
  }

  private static void profiles() {
    String source =
        """
        {"profiles":{"test:bucket":{"sprite":"test:item/bucket","kind":"steam",
        "mask":[[4,4,12,10]],"steam":[[6,0,9,2]],"rect":[4,4,7,6],
        "tint":4294967295,"light":11,"steam_frames":[[[6,0,7,1]],[[8,1,9,2]]],
        "frame_ticks":5}}}
        """;
    var parsed = BucketContentRendering.parse(new StringReader(source));
    var profile = parsed.get(ResourceLocation.parse("test:bucket"));
    require(
        profile.tint() == -1 && profile.light() == 11 && profile.frameTicks() == 5,
        "ARGB, light and native frame duration survive the resource boundary");
    require(
        profile.steamFrames().get(1).getFirst().left() == 8,
        "Vapor retains its individual native frame mask");
    invalid(source.replace("\"light\":11", "\"light\":16"));
    invalid(source.replace("\"frame_ticks\":5", "\"frame_ticks\":0"));
    invalid(source.replace("\"kind\":\"steam\"", "\"kind\":\"unknown\""));
    invalid(source.replace("[4,4,12,10]", "[4,4,4,10]"));
    invalid(source.replace("\"light\":11", "\"light\":11.5"));
    invalid(source.replace("\"mask\":[[4,4,12,10]]", "\"mask\":true"));
  }

  private static void invalid(String source) {
    try {
      BucketContentRendering.parse(new StringReader(source));
      throw new AssertionError("Invalid resource was accepted");
    } catch (IllegalArgumentException expected) {
      require(expected.getMessage().contains("test:bucket"), "Invalid profile identifies its item");
    }
  }

  private static void motion(Sprite sprite) {
    for (var kind :
        List.of(
            "smooth",
            "molten",
            "viscous",
            "suspension",
            "garnish",
            "mound",
            "chunks",
            "steam",
            "iridescent",
            "gel")) {
      var profile = profile(kind);
      var first = quads(create(sprite, profile, 0));
      var later = quads(create(sprite, profile, 10));
      require(changed(first, later, IQuadTransformer.POSITION + 1), kind + " moves in height");
      if (kind.equals("smooth") || kind.equals("molten"))
        require(
            changed(first, later, IQuadTransformer.UV0)
                || changed(first, later, IQuadTransformer.UV0 + 1),
            kind + " moves its native color fields in two dimensions");
    }
    var fluid = create(sprite, profile("smooth"), 0);
    require(
        fluid.getRenderPasses(null, false).equals(List.of(fluid))
            && fluid.getRenderPasses(null, true).equals(List.of(fluid)),
        "Both render pipelines retain the generated contents pass");
    require(!fluid.usesBlockLight(), "Contents retain the native fluid item lighting convention");
  }

  private static void masks(Sprite sprite) {
    sprite.sampled.clear();
    var rendered = quads(create(sprite, profile("smooth"), 0));
    require(
        sprite.sampled.stream().allMatch(point -> CONTENT.contains(point.x(), point.y())),
        "Material inspection never samples the native bucket metal");
    for (var quad : rendered) {
      var data = quad.getVertices();
      for (int vertex = 0; vertex < 4; vertex++) {
        int start = vertex * IQuadTransformer.STRIDE;
        int x =
            (int)
                (sprite.getUOffset(Float.intBitsToFloat(data[start + IQuadTransformer.UV0])) * 16);
        int y =
            (int)
                (sprite.getVOffset(Float.intBitsToFloat(data[start + IQuadTransformer.UV0 + 1]))
                    * 16);
        require(
            CONTENT.contains(x, y), "Mapped UV remains on actual content, including clipped cells");
        float height = Float.intBitsToFloat(data[start + IQuadTransformer.POSITION + 1]);
        if (Math.abs(height - 1.8F / 16) < 1E-6F) {
          float horizontal = Float.intBitsToFloat(data[start + IQuadTransformer.POSITION]) - .5F;
          float depth = Float.intBitsToFloat(data[start + IQuadTransformer.POSITION + 2]) - .5F;
          require(
              Math.hypot(horizontal, depth) <= 3.7F / 16 + 1E-6F,
              "The fluid floor stays inside the vessel's tapered lower wall");
        }
      }
    }
    var top = rendered.stream().filter(quad -> quad.getDirection() == Direction.UP).toList();
    var rows = new HashSet<Integer>();
    for (var quad : top) {
      var vertices = quad.getVertices();
      float z = Float.intBitsToFloat(vertices[IQuadTransformer.POSITION + 2]);
      if (Math.abs(z - .5F) < .12F) rows.add(vertices[IQuadTransformer.UV0 + 1]);
    }
    require(rows.size() > 2, "Smooth material fields do not collapse to two stretched crop rows");
  }

  private static void plainLiquidMaterials(Sprite sprite, Sprite highlight) {
    for (var kind :
        List.of(
            "smooth",
            "molten",
            "viscous",
            "suspension",
            "garnish",
            "mound",
            "chunks",
            "steam",
            "iridescent",
            "gel"))
      for (double ticks : new double[] {0, 10, 20}) {
        var model =
            BucketContentRendering.create(
                fixture(), sprite, profile(kind), 1, -1, false, ticks, highlight);
        require(
            quads(model).stream().allMatch(quad -> quad.getSprite() == sprite),
            "Plain " + kind + " liquid keeps its native material without a rotating white overlay");
      }
  }

  private static void solids(Sprite sprite) {
    var profile = profile("solid");
    var first = quads(create(sprite, profile, 0));
    var later = quads(create(sprite, profile, 10));
    require(!changed(first, later, IQuadTransformer.POSITION + 1), "Solid contents stay still");
    float minimum = Float.POSITIVE_INFINITY;
    float maximum = Float.NEGATIVE_INFINITY;
    float highest = 0;
    for (var quad : first)
      for (int vertex = 0; vertex < 4; vertex++) {
        int start = vertex * IQuadTransformer.STRIDE;
        float x = Float.intBitsToFloat(quad.getVertices()[start + IQuadTransformer.POSITION]);
        minimum = Math.min(minimum, x);
        maximum = Math.max(maximum, x);
        highest =
            Math.max(
                highest,
                Float.intBitsToFloat(quad.getVertices()[start + IQuadTransformer.POSITION + 1]));
      }
    require(
        Math.abs(minimum - .2F) < 1E-6F && Math.abs(maximum - .8F) < 1E-6F,
        "A solid fills the complete 4.8-pixel mouth radius");
    require(highest > .64F, "Solid contents form a broad raised mound above the mouth");
  }

  private static void ingredients(Sprite sprite) {
    var top =
        quads(create(sprite, profile("chunks"), 0)).stream()
            .filter(quad -> quad.getDirection() == Direction.UP)
            .toList();
    boolean raisedIngredient = false;
    boolean sampledOutsideBase = false;
    for (var quad : top) {
      var vertices = quad.getVertices();
      int x = (int) (sprite.getUOffset(Float.intBitsToFloat(vertices[IQuadTransformer.UV0])) * 16);
      int y =
          (int) (sprite.getVOffset(Float.intBitsToFloat(vertices[IQuadTransformer.UV0 + 1])) * 16);
      sampledOutsideBase |= !BASE.contains(x, y);
      if (x >= 8 && x < 11 && y >= 6 && y < 9)
        for (int vertex = 0; vertex < 4; vertex++)
          raisedIngredient |=
              Float.intBitsToFloat(
                      vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1])
                  > .57F;
    }
    require(sampledOutsideBase, "Food retains its full source layout beyond the safe base patch");
    require(raisedIngredient, "Native ingredient regions rise above the liquid base");
  }

  private static void nativeSoupGarnish() {
    int[][] nativeMouth = {
      {0x986D4E, 0xAE7838, 0xAADB74, 0x7AB244, 0xD19C5D, 0xAE7838, 0xAE7838, 0x986D4E},
      {0xAE7838, 0xC88D47, 0x7AB244, 0xD19C5D, 0xD19C5D, 0xC88D47, 0xEFA766, 0xD19C5D},
      {0xFF8E09, 0xEFA766, 0xC88D47, 0xC88D47, 0xEFA766, 0xEFA766, 0xC88D47, 0xAE7838}
    };
    try (var sprite = new Sprite("native_chicken_soup", 16)) {
      for (int row = 0; row < nativeMouth.length; row++)
        for (int column = 0; column < nativeMouth[row].length; column++) {
          int rgb = nativeMouth[row][column];
          int abgr = 0xFF000000 | rgb & 0xFF00 | (rgb & 255) << 16 | rgb >> 16;
          sprite.contents().getOriginalImage().setPixelRGBA(column + 4, row + 3, abgr);
        }
      var mouth = new BucketContentRendering.Rect(4, 3, 12, 6);
      var profile =
          new BucketContentRendering.Profile(
              sprite.contents().name(), "chunks", List.of(mouth), List.of(), mouth, -1, 0);
      boolean raised = false;
      for (var quad : quads(create(sprite, profile, 0))) {
        if (quad.getDirection() != Direction.UP) continue;
        var vertices = quad.getVertices();
        int x =
            (int) (sprite.getUOffset(Float.intBitsToFloat(vertices[IQuadTransformer.UV0])) * 16);
        int y =
            (int)
                (sprite.getVOffset(Float.intBitsToFloat(vertices[IQuadTransformer.UV0 + 1])) * 16);
        if ((x == 6 && y == 3) || (x == 7 && y == 3) || (x == 6 && y == 4))
          for (int vertex = 0; vertex < 4; vertex++)
            raised |=
                Float.intBitsToFloat(
                        vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1])
                    > .585F;
      }
      require(
          raised,
          "Native Delightful Creators chicken-soup greens inside the base rectangle rise above"
              + " broth");
    }
  }

  private static void steam(Sprite sprite, Sprite highlight) {
    var frame =
        List.of(
            new BucketContentRendering.Rect(6, 0, 7, 1),
            new BucketContentRendering.Rect(7, 4, 8, 5));
    var next = List.of(new BucketContentRendering.Rect(8, 1, 9, 2));
    var profile =
        new BucketContentRendering.Profile(
            sprite.contents().name(),
            "steam",
            List.of(CONTENT),
            List.of(),
            BASE,
            -1,
            0,
            List.of(frame, next),
            5);
    sprite.sampled.clear();
    var first =
        BucketContentRendering.create(fixture(), sprite, profile, 1, -1, false, 0, highlight);
    var later =
        BucketContentRendering.create(fixture(), sprite, profile, 1, -1, false, 5, highlight);
    var firstSteam = vapor(quads(first), highlight);
    var nextSteam = vapor(quads(later), highlight);
    require(
        firstSteam.size() == 8 && nextSteam.size() == 4,
        "Two crossed plumes retain the exact current native vapor pixel count");
    require(
        changed(firstSteam, nextSteam, IQuadTransformer.POSITION),
        "Frame-specific vapor changes its lateral position");
    require(
        changed(firstSteam, nextSteam, IQuadTransformer.POSITION + 1),
        "Vapor rises above the opening");
    require(
        sprite.sampled.stream().noneMatch(point -> point.y() < CONTENT.top()),
        "Steam uses white geometry rather than asynchronously animated metal-bearing item UVs");
    require(
        !sprite.sampled.contains(new Coordinate(7, 4)),
        "Vapor-origin pixels inside the mouth cannot become white liquid stripes");
    for (var quad : firstSteam)
      for (int vertex = 0; vertex < 4; vertex++) {
        int start = vertex * IQuadTransformer.STRIDE;
        int alpha = quad.getVertices()[start + IQuadTransformer.COLOR] >>> 24;
        int textureAlpha = highlight.getPixelRGBA(0, 0, 0) >>> 24;
        require(
            textureAlpha == 255 && alpha >= 153 && alpha <= 217,
            "An opaque white source leaves visible vapor opacity under vertex control alone");
        require(
            Float.intBitsToFloat(quad.getVertices()[start + IQuadTransformer.POSITION + 1])
                > 10.2F / 16,
            "Vapor stays above the mouth");
      }
    var outerCenters =
        firstSteam.stream()
            .filter(quad -> quad.getDirection() == Direction.WEST)
            .map(quad -> Float.intBitsToFloat(quad.getVertices()[IQuadTransformer.POSITION]))
            .distinct()
            .sorted()
            .toList();
    require(
        outerCenters.size() == 2 && outerCenters.getFirst() < .4F && outerCenters.getLast() > .6F,
        "Opposite mouth sides expose a plume beside the fist in either hand");
    var bent =
        nextSteam.stream()
            .filter(quad -> quad.getDirection() == Direction.SOUTH)
            .sorted(
                java.util.Comparator.comparingDouble(
                    quad -> midpoint(quad, IQuadTransformer.POSITION)))
            .toList();
    var emitters =
        nextSteam.stream()
            .filter(quad -> quad.getDirection() == Direction.WEST)
            .map(quad -> midpoint(quad, IQuadTransformer.POSITION))
            .sorted()
            .toList();
    require(
        midpoint(bent.getFirst(), IQuadTransformer.POSITION) < emitters.getFirst()
            && midpoint(bent.getLast(), IQuadTransformer.POSITION) > emitters.getLast(),
        "The bent native glyph curls outward on either side instead of into the carrying fist");
    var partial =
        vapor(
            quads(
                BucketContentRendering.create(
                    fixture(), sprite, profile, .1F, -1, false, 0, highlight)),
            highlight);
    require(
        partial.stream()
            .allMatch(
                quad ->
                    Float.intBitsToFloat(quad.getVertices()[IQuadTransformer.POSITION + 1])
                        > 10.2F / 16),
        "Steam from a partially filled copper vessel still clears its physical opening");
  }

  private static float midpoint(BakedQuad quad, int coordinate) {
    return (Float.intBitsToFloat(quad.getVertices()[coordinate])
            + Float.intBitsToFloat(quad.getVertices()[2 * IQuadTransformer.STRIDE + coordinate]))
        / 2;
  }

  private static List<BakedQuad> vapor(List<BakedQuad> quads, TextureAtlasSprite highlight) {
    return quads.stream()
        .filter(quad -> quad.getSprite() == highlight && quad.getDirection() != Direction.UP)
        .toList();
  }

  private static void emission(Sprite sprite) throws Throwable {
    var model =
        BucketContentRendering.create(
            fixture(), sprite, profile("molten"), 1, 0xFF4488CC, true, 0, null);
    var expected = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
    require(
        model.getRenderTypes(null, false).equals(List.of(expected))
            && model.getRenderTypes(null, true).equals(List.of(expected)),
        "Emissive contents use the vanilla shader API intercepted by installed Iris");
    List<Integer> colors = new ArrayList<>();
    List<Integer> lights = new ArrayList<>();
    var consumer =
        (VertexConsumer)
            Proxy.newProxyInstance(
                BucketContentRenderingTest.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("addVertex") && arguments.length == 11) {
                    colors.add((Integer) arguments[3]);
                    lights.add((Integer) arguments[7]);
                    return null;
                  }
                  if (method.isDefault())
                    return InvocationHandler.invokeDefault(proxy, method, arguments);
                  return proxy;
                });
    var quads = quads(model);
    for (var quad : quads) {
      require(
          quad.getTintIndex() == -1, "Final material tint cannot be reapplied by the bucket item");
      consumer.putBulkData(new PoseStack().last(), quad, 1, 1, 1, 1, 0, 0, true);
    }
    require(
        colors.size() == quads.size() * 4 && colors.stream().allMatch(color -> color == 0xFF4488CC),
        "The native vertex consumer receives fully opaque correctly ordered material color");
    require(
        lights.stream().allMatch(light -> light == 0x00F000F0),
        "The native vertex consumer retains maximum packed light");
  }

  private static void fallback(Sprite sprite) {
    var model = create(sprite, null, 0);
    require(!quads(model).isEmpty(), "Unprofiled native fluid sprites retain a complete surface");
    var empty =
        BucketContentRendering.create(fixture(), sprite, profile("smooth"), 0, -1, false, 0, null);
    require(quads(empty).isEmpty(), "An empty vessel emits no content geometry");
  }

  private static BucketContentRendering.Profile profile(String kind) {
    return new BucketContentRendering.Profile(
        ResourceLocation.parse("test:item/contents"),
        kind,
        List.of(CONTENT),
        List.of(),
        BASE,
        -1,
        0);
  }

  private static BakedModel create(
      Sprite sprite, BucketContentRendering.Profile profile, double ticks) {
    return BucketContentRendering.create(fixture(), sprite, profile, 1, -1, false, ticks, null);
  }

  private static List<BakedQuad> quads(BakedModel model) {
    return model.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null);
  }

  private static boolean changed(List<BakedQuad> first, List<BakedQuad> second, int field) {
    if (first.size() != second.size()) return true;
    for (int index = 0; index < first.size(); index++)
      for (int vertex = 0; vertex < 4; vertex++) {
        int start = vertex * IQuadTransformer.STRIDE;
        if (first.get(index).getVertices()[start + field]
            != second.get(index).getVertices()[start + field]) return true;
      }
    return false;
  }

  private static BakedModel fixture() {
    return (BakedModel)
        Proxy.newProxyInstance(
            BucketContentRenderingTest.class.getClassLoader(),
            new Class<?>[] {BakedModel.class},
            (proxy, method, arguments) -> {
              throw new AssertionError("Unexpected template call " + method.getName());
            });
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record Coordinate(int x, int y) {}

  private static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
    private final Set<Coordinate> sampled = new HashSet<>();

    private Sprite(String name, int size) {
      super(
          ResourceLocation.parse("test:" + name),
          new SpriteContents(
              ResourceLocation.parse("test:" + name),
              new FrameSize(size, size),
              image(size),
              ResourceMetadata.EMPTY),
          256,
          256,
          32,
          48);
    }

    private static NativeImage image(int size) {
      var image = new NativeImage(size, size, false);
      for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++) {
          int color = size == 1 ? 0xFFFFFFFF : 0xFF888888;
          if (CONTENT.contains(x, y)) color = 0xFF2040A0 + ((x + y) % 3) * 0x00080808;
          if (x >= 8 && x < 11 && y >= 6 && y < 9) color = 0xFF30CC50;
          image.setPixelRGBA(x, y, color);
        }
      return image;
    }

    @Override
    public int getPixelRGBA(int frameIndex, int x, int y) {
      sampled.add(new Coordinate(x, y));
      return super.getPixelRGBA(frameIndex, x, y);
    }

    @Override
    public void close() {
      contents().close();
    }
  }
}
