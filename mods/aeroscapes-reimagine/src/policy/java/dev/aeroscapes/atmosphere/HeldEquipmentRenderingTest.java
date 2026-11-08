package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Exercises the custom-renderer pass actually consumed by NeoForge's item renderer. */
public final class HeldEquipmentRenderingTest {
  private HeldEquipmentRenderingTest() {}

  public static void main(String[] arguments) {
    for (var context : ItemDisplayContext.values())
      require(
          HeldEquipmentRendering.held(context) == context.name().endsWith("_HAND"),
          "Only hand contexts replace native inventory geometry: " + context);
    BakedQuad housing = quad(),
        scaleA = quad(),
        scaleB = quad(),
        scaleC = quad(),
        scaleD = quad(),
        heldHousing = quad();
    AtomicInteger transforms = new AtomicInteger();
    BakedModel original = fixture(List.of(housing, scaleA, scaleB, scaleC, scaleD), transforms);
    BakedModel replacement =
        HeldEquipmentRendering.altimeter(
            original,
            fixture(List.of(housing), new AtomicInteger()),
            fixture(List.of(heldHousing), new AtomicInteger()));
    var quads = replacement.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null);
    require(
        quads.equals(List.of(scaleA, scaleB, scaleC, scaleD, heldHousing)),
        "Replace the static housing while preserving every moving scale quad");
    require(
        replacement.getRenderPasses(null, false).equals(List.of(replacement)),
        "NeoForge must render the wrapper rather than its unmodified delegate");
    require(
        replacement.applyTransform(
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new PoseStack(), false)
                == replacement
            && transforms.get() == 1,
        "Apply the native transform once without discarding the held pass");
    require(
        HeldEquipmentRendering.altimeter(ItemDisplayContext.GUI, original) == original,
        "The GUI keeps its complete native instrument model");
    sandpaper();
    System.out.println(
        "Held custom renderers: contexts, moving scales, render passes and transforms passed");
  }

  @SuppressWarnings("deprecation") // Reproduce the pinned 1.21.1 native renderer/model contract.
  private static void sandpaper() {
    BakedModel icon = fixture(List.of(quad()), new AtomicInteger());
    BakedModel body = fixture(List.of(quad()), new AtomicInteger());
    var perspectives =
        com.google.common.collect.ImmutableMap.<ItemDisplayContext, BakedModel>builder();
    for (var context : ItemDisplayContext.values())
      if (HeldEquipmentRendering.held(context)) perspectives.put(context, body);
    var separate =
        new net.neoforged.neoforge.client.model.SeparateTransformsModel.Baked(
            false,
            true,
            false,
            null,
            net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY,
            icon,
            perspectives.build());
    var nativeModel =
        new com.simibubi.create.foundation.item.render.CustomRenderedItemModel(separate);
    for (var context : ItemDisplayContext.values()) {
      PoseStack outer = new PoseStack();
      nativeModel.applyTransform(context, outer, false);
      var matrix = new org.joml.Matrix4f(outer.last().pose());
      BakedModel inner = HeldEquipmentRendering.sandpaper(context, nativeModel.getOriginalModel());
      BakedModel rendered = inner.applyTransform(ItemDisplayContext.NONE, new PoseStack(), false);
      require(
          rendered == (HeldEquipmentRendering.held(context) ? body : icon),
          "Create's inner NONE render selects the held sheet only in hands: " + context);
      require(
          outer.last().pose().equals(matrix),
          "Body selection does not apply a second outer hand transform");
      require(nativeModel.isCustomRenderer(), "Keep Create's polishing renderer and item overlay");
    }
  }

  private static BakedQuad quad() {
    return new BakedQuad(new int[32], -1, Direction.SOUTH, null, true);
  }

  private static BakedModel fixture(List<BakedQuad> quads, AtomicInteger transforms) {
    return (BakedModel)
        Proxy.newProxyInstance(
            HeldEquipmentRenderingTest.class.getClassLoader(),
            new Class<?>[] {BakedModel.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getQuads" -> quads;
                  case "applyTransform" -> {
                    transforms.incrementAndGet();
                    yield proxy;
                  }
                  case "getRenderPasses" -> List.of(proxy);
                  case "equals" -> proxy == arguments[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new AssertionError("Unexpected model call " + method.getName());
                });
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
