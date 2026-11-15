package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Exercises the exact hand-routing and fluid quads without starting a client. */
public final class CopperBucketHeldRenderingTest {
  private CopperBucketHeldRenderingTest() {}

  public static void main(String[] arguments) throws ReflectiveOperationException {
    nativeIcon();
    nativeTargets();
    routing();
    try (var source = new Sprite("source", 32);
        var target = new Sprite("target", 160)) {
      fluid(source, target);
    }
    System.out.println("Copper bucket hand routing, fluid volume, UV, tint, light and gas passed");
  }

  private static void nativeIcon() {
    var icon = fixture(List.of(), new AtomicInteger());
    var body = fixture(List.of(), new AtomicInteger());
    var separate =
        new net.neoforged.neoforge.client.model.SeparateTransformsModel.Baked(
            false,
            true,
            false,
            null,
            net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY,
            icon,
            com.google.common.collect.ImmutableMap.of(
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, body));
    require(separate.isGui3d(), "Native perspective wrapper is 3D before icon selection");
    var selected = CopperBucketHeldRendering.nativeIcon(separate);
    require(
        selected == icon && !selected.isGui3d(),
        "Copper retexturing receives the original flat fluid icon");
    require(
        separate.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new PoseStack(), false)
            == body,
        "Icon selection preserves the volumetric hand child");
    var genuineVolume =
        new BakedModelWrapper<>(icon) {
          @Override
          public boolean isGui3d() {
            return true;
          }
        };
    require(
        CopperBucketHeldRendering.nativeIcon(genuineVolume) == genuineVolume,
        "Native rejection of genuinely volumetric bucket icons is preserved");
  }

  private static void nativeTargets() throws ReflectiveOperationException {
    var provider =
        Class.forName(
            "com.yision.fluidlogistics.content.fluids.copperBucket.client.CopperBucketModel$Overrides",
            false,
            CopperBucketHeldRenderingTest.class.getClassLoader());
    var replacement =
        provider.getDeclaredMethod(
            "createReplacement",
            net.minecraft.world.item.Item.class,
            net.minecraft.client.multiplayer.ClientLevel.class,
            net.minecraft.world.entity.LivingEntity.class,
            int.class);
    require(
        java.lang.reflect.Modifier.isStatic(replacement.getModifiers())
            && replacement.getReturnType() == java.util.Optional.class,
        "Pinned native fluid-icon selection seam");
  }

  private static void routing() {
    var nativeTransforms = new AtomicInteger();
    var bodyTransforms = new AtomicInteger();
    var selections = new AtomicInteger();
    var nativeModel = fixture(List.of(), nativeTransforms);
    var body = fixture(List.of(), bodyTransforms);
    var fill = fixture(List.of(), new AtomicInteger());
    var held = CopperBucketHeldRendering.combine(body, fill, false);
    var routed =
        CopperBucketHeldRendering.route(
            nativeModel,
            () -> {
              selections.incrementAndGet();
              return held;
            });
    for (var context : ItemDisplayContext.values()) {
      var selected = routed.applyTransform(context, new PoseStack(), false);
      require(
          selected == (HeldEquipmentRendering.held(context) ? held : nativeModel),
          "Native state result retained outside hands: " + context);
    }
    require(selections.get() == 4 && bodyTransforms.get() == 4, "Four true hand contexts");
    require(
        nativeTransforms.get() == ItemDisplayContext.values().length - 4,
        "Native presentation applies one transform");
    require(
        held.getRenderPasses(null, false).equals(List.of(held, fill))
            && held.getRenderPasses(null, true).equals(List.of(held, fill)),
        "Both pipelines retain the body wrapper and fluid pass");
    var empty = CopperBucketHeldRendering.combine(body, null, false);
    require(empty.getRenderPasses(null, false).equals(List.of(empty)), "Empty vessel has no fluid");
    var gas = CopperBucketHeldRendering.combine(body, fill, true);
    var pose = new PoseStack();
    require(
        gas.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, false) == gas,
        "Gas transform retains its fluid passes");
    var grip = new Vector3f(0, 6.25F / 16, 0);
    var transformed = pose.last().pose().transformPosition(new Vector3f(grip));
    require(transformed.distance(grip) < 1.0E-6F, "Native gas inversion preserves carrying grip");
    var lower = pose.last().pose().transformPosition(new Vector3f(0, 0, 0));
    require(lower.y > grip.y, "Gas inverts the vessel around its carrying grip");
    var up = pose.last().pose().transformDirection(new Vector3f(0, 1, 0));
    require(
        up.distance(new Vector3f(0, -.81915204F, .57357644F)) < 1.0E-6F,
        "Shared gas tilt keeps the inverted vessel inside both recorded hand viewports");
    require(
        Math.abs(up.length() - 1) < 1.0E-6F
            && gas.getRenderPasses(null, false).equals(List.of(gas, fill)),
        "Gas applies one unscaled carrying pose to the body and fluid passes");
    var nativeBodyWrapper =
        new BakedModelWrapper<>(body) {
          @Override
          public BakedModel applyTransform(
              ItemDisplayContext context, PoseStack bodyPose, boolean leftHand) {
            return originalModel.applyTransform(context, bodyPose, leftHand);
          }
        };
    var nested = CopperBucketHeldRendering.combine(nativeBodyWrapper, fill, false);
    var selected =
        nested.applyTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, new PoseStack(), true);
    require(
        selected != nested && selected.getRenderPasses(null, false).equals(List.of(selected, fill)),
        "A provider's transformed body child retains the fluid wrapper");
  }

  private static void fluid(TextureAtlasSprite source, TextureAtlasSprite target) {
    var vertices = new int[IQuadTransformer.STRIDE * 4];
    for (int vertex = 0; vertex < 4; vertex++) {
      int start = vertex * IQuadTransformer.STRIDE;
      vertices[start + IQuadTransformer.POSITION] = Float.floatToRawIntBits(.8F);
      vertices[start + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(9F / 16);
      vertices[start + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(.5F);
      vertices[start + IQuadTransformer.COLOR] = -1;
      vertices[start + IQuadTransformer.UV0] = Float.floatToRawIntBits(source.getU(.25F));
      vertices[start + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(source.getV(.75F));
    }
    var before = vertices.clone();
    var template = new BakedQuad(vertices, -1, Direction.UP, source, true, false);
    var partial = CopperBucketHeldRendering.fillQuad(template, target, 2500, false);
    var full = CopperBucketHeldRendering.fillQuad(template, target, 5000, false);
    var excess = CopperBucketHeldRendering.fillQuad(template, target, 6000, false);
    var glow = CopperBucketHeldRendering.fillQuad(template, target, 5000, true);
    require(Arrays.equals(vertices, before), "Shared fill template remains immutable");
    require(
        partial.getSprite() == target && partial.getTintIndex() == 1,
        "Actual still sprite uses native CopperBucketColor tint channel");
    var rendered = partial.getVertices();
    require(
        Math.abs(Float.intBitsToFloat(rendered[IQuadTransformer.POSITION + 1]) - 5.4F / 16)
            < 1.0E-6F,
        "Half capacity follows the vessel's bottom-to-mouth range");
    require(
        Math.abs(Float.intBitsToFloat(rendered[IQuadTransformer.POSITION]) - .765625F) < 1.0E-6F,
        "Partial fill stays inside the tapered vessel");
    require(
        Math.abs(Float.intBitsToFloat(rendered[IQuadTransformer.UV0]) - target.getU(.25F)) < 1.0E-6F
            && Math.abs(
                    Float.intBitsToFloat(rendered[IQuadTransformer.UV0 + 1]) - target.getV(.75F))
                < 1.0E-6F,
        "Atlas changes retain local texture coordinates");
    require(
        Arrays.equals(full.getVertices(), excess.getVertices()), "Capacity limits fill geometry");
    require(
        glow.getVertices()[IQuadTransformer.UV2] == 0x00F000F0,
        "Luminous fluids retain NeoForge maximum emissivity");
    var fill =
        CopperBucketHeldRendering.fill(
            fixture(List.of(template), new AtomicInteger()), target, 2500, false);
    require(
        fill.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null).size() == 1,
        "NeoForge quad consumer receives the retargeted fluid");
    require(
        fill.getRenderPasses(null, false).equals(List.of(fill)), "Fluid pass retains its wrapper");
  }

  private static BakedModel fixture(List<BakedQuad> quads, AtomicInteger transforms) {
    return (BakedModel)
        Proxy.newProxyInstance(
            CopperBucketHeldRenderingTest.class.getClassLoader(),
            new Class<?>[] {BakedModel.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getQuads" -> arguments[1] == null ? quads : List.of();
                  case "applyTransform" -> {
                    transforms.incrementAndGet();
                    yield proxy;
                  }
                  case "getRenderPasses" -> List.of(proxy);
                  case "isGui3d" -> false;
                  case "equals" -> proxy == arguments[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new AssertionError("Unexpected model call " + method.getName());
                });
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
    private Sprite(String name, int offset) {
      super(
          ResourceLocation.withDefaultNamespace(name),
          new SpriteContents(
              ResourceLocation.withDefaultNamespace(name),
              new FrameSize(16, 16),
              new NativeImage(16, 16, false),
              ResourceMetadata.EMPTY),
          512,
          512,
          offset,
          48);
    }

    @Override
    public void close() {
      contents().close();
    }
  }
}
