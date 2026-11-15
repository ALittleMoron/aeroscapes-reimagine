package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import punchy.client.renderer.layer.ArmToolTuning;

/** Proves resolved native states reach a volumetric hand child at the real camera boundary. */
public final class AllBucketHeldRenderingTest {
  private AllBucketHeldRenderingTest() {}

  public static void main(String[] arguments) throws IOException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    for (var item :
        List.of(
            Items.BUCKET,
            Items.WATER_BUCKET,
            Items.LAVA_BUCKET,
            Items.MILK_BUCKET,
            Items.POWDER_SNOW_BUCKET,
            Items.COD_BUCKET,
            Items.SALMON_BUCKET,
            Items.TROPICAL_FISH_BUCKET,
            Items.PUFFERFISH_BUCKET,
            Items.AXOLOTL_BUCKET,
            Items.TADPOLE_BUCKET))
      require(BucketPresentation.physical(item), "Physical vanilla bucket: " + item);
    require(!BucketPresentation.physical(Items.COD), "Bare creatures are not vessels");
    routing();
    tint();
    milk();
    anglerLight();
    require(
        BucketCreatures.hop(false, 15) == 0 && BucketCreatures.hop(true, 15) <= .035F,
        "Only excited slime uses the bounded native signal");
    measurement();
    nativeBoundary();
    System.out.println(
        "All bucket families: physical classification, resolved-state camera route, tint and native boundary passed");
  }

  private static void routing() {
    var nativeCalls = new AtomicInteger();
    var heldCalls = new AtomicInteger();
    var nativeState = fixture(List.of(), nativeCalls, false);
    var volume = fixture(List.of(), heldCalls, true);
    for (var context : ItemDisplayContext.values()) {
      var selected = BucketHeldRendering.select(nativeState, true, false, context, () -> volume);
      var drawn = ClientHooks.handleCameraTransforms(new PoseStack(), selected, context, false);
      require(
          drawn == (HeldEquipmentRendering.held(context) ? volume : nativeState),
          "Actual camera consumer retains native state only outside hands: " + context);
      require(drawn.isGui3d() == HeldEquipmentRendering.held(context), "Hand volume: " + context);
      require(
          BucketHeldRendering.select(nativeState, true, true, context, () -> volume) == nativeState,
          "Existing authored state graph retained: " + context);
      require(
          BucketHeldRendering.select(nativeState, false, false, context, () -> volume)
              == nativeState,
          "Unrelated items retained: " + context);
    }
    require(
        heldCalls.get() == 4 && nativeCalls.get() == ItemDisplayContext.values().length - 4,
        "One camera transform per draw; four hand contexts, including Punchy third-person slots");
    var profile = new ArmToolTuning.Profile(1, .1F, .2F, .3F, 1, 2, 3);
    var adjusted = BucketHeldRendering.armProfile(profile);
    require(
        adjusted != profile && profile.offY == .2F && Math.abs(adjusted.offY + .32F) < 1E-6F,
        "Arm and item share the lift without mutating a native/user profile");
    require(
        adjusted.scale == profile.scale
            && adjusted.rotZ == profile.rotZ
            && adjusted.offX == profile.offX,
        "Other profile fields retained");
  }

  private static void tint() {
    int[] vertices = new int[IQuadTransformer.STRIDE * 4];
    java.util.Arrays.fill(vertices, -1);
    var quad = new BakedQuad(vertices, 1, Direction.UP, null, true, false);
    var source = fixture(List.of(quad), new AtomicInteger(), false);
    var painted = BucketHeldRendering.tinted(source, 0x804488CC);
    var result =
        painted.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null).getFirst();
    require(result.getTintIndex() == -1, "Native item tint is not applied twice to generic fluids");
    require(
        result.getVertices()[IQuadTransformer.COLOR] == 0x80CC8844,
        "Fluid ARGB becomes vertex ABGR, preserving alpha");
    require(vertices[IQuadTransformer.COLOR] == -1, "Shared source remains immutable");
    require(
        painted.getRenderPasses(null, true).equals(List.of(painted)),
        "Fabulous retains recolored fluid pass");
  }

  private static void measurement() {
    var bounds = new BucketCreatures.Bounds();
    var buffers = bounds.buffers();
    var first = buffers.getBuffer(null);
    var second = buffers.getBuffer(null);
    require(first != second, "Each native layer has a distinct consumer, including foil delegates");
    var foil = VertexMultiConsumer.create(first, second);
    foil.addVertex(-3, 7, 9);
    foil.addVertex(-2, 8, 10);
    require(
        bounds.minimum.equals(new Vector3f(-3, 7, 9))
            && bounds.maximum.equals(new Vector3f(-2, 8, 10)),
        "Native composite layers aggregate actual vertices without duplicate-delegate failure");
  }

  private static void milk() {
    int[] expected = {2500, 1250, 0};
    for (int stage = 0; stage < 3; stage++) {
      var name = ResourceLocation.parse("eatinganimation:item/milk_bucket_drinking_" + stage);
      try (var sprite = new Sprite(name)) {
        var quad =
            new BakedQuad(
                new int[IQuadTransformer.STRIDE * 4], -1, Direction.UP, sprite, true, false);
        var nativeState = fixture(List.of(quad), new AtomicInteger(), false);
        require(
            BucketHeldRendering.milkAmount(nativeState) == expected[stage],
            "Resolved drinking state retains diminishing milk volume: " + stage);
      }
    }
    require(
        BucketHeldRendering.milkAmount(fixture(List.of(), new AtomicInteger(), false)) == 5000,
        "Unstarted milk bucket remains full");
  }

  private static void anglerLight() {
    var data = new CompoundTag();
    require(
        BucketHeldRendering.anglerLight(data) == 4,
        "An ordinary inflated captured angler emits bulb light");
    data.putShort("Air", (short) 295);
    require(
        BucketHeldRendering.anglerLight(data) == 4,
        "Raw air data ignored by the native bucket loader cannot invent a deflated state");
    data.putShort("Air", (short) 296);
    require(
        BucketHeldRendering.anglerLight(data) == 4, "Ordinary captured anglers retain bulb light");
    data.putFloat("Health", 0);
    require(
        BucketHeldRendering.anglerLight(data) == 0, "A dead captured angler does not emit light");
  }

  private static void nativeBoundary() throws IOException {
    ClassNode renderer = new ClassNode();
    try (var stream =
        AllBucketHeldRenderingTest.class.getResourceAsStream(
            "/net/minecraft/client/renderer/entity/ItemRenderer.class")) {
      if (stream == null) throw new IOException("Missing exact ItemRenderer");
      new ClassReader(stream).accept(renderer, 0);
    }
    var render =
        renderer.methods.stream()
            .filter(
                method ->
                    method.name.equals("render")
                        && method.desc.endsWith(
                            "Lnet/minecraft/client/resources/model/BakedModel;)V"))
            .findFirst()
            .orElseThrow();
    int cameras = 0, camera = -1, custom = -1, draw = -1, pop = -1, index = 0;
    for (var instruction : render.instructions) {
      if (instruction instanceof MethodInsnNode call) {
        if (call.name.equals("handleCameraTransforms")) {
          camera = index;
          cameras++;
        }
        if (call.name.equals("isCustomRenderer")) custom = index;
        if (call.name.equals("renderModelLists")) draw = index;
        if (call.name.equals("popPose")) pop = index;
      }
      index++;
    }
    require(
        cameras == 1 && camera < custom && custom < draw && draw < pop,
        "Pinned native seam selects once before custom dispatch, all passes and creature draw");
  }

  private static BakedModel fixture(
      List<BakedQuad> faces, AtomicInteger transforms, boolean volume) {
    return (BakedModel)
        Proxy.newProxyInstance(
            AllBucketHeldRenderingTest.class.getClassLoader(),
            new Class<?>[] {BakedModel.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getQuads" -> arguments[1] == null ? faces : List.of();
                  case "applyTransform" -> {
                    transforms.incrementAndGet();
                    yield proxy;
                  }
                  case "getRenderPasses" -> List.of(proxy);
                  case "isGui3d" -> volume;
                  case "equals" -> proxy == arguments[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "toString" -> "bucket fixture";
                  default -> method.getReturnType() == boolean.class ? false : null;
                });
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
    private Sprite(ResourceLocation name) {
      super(
          name,
          new SpriteContents(
              name, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY),
          16,
          16,
          0,
          0);
    }

    @Override
    public void close() {
      contents().close();
    }
  }
}
