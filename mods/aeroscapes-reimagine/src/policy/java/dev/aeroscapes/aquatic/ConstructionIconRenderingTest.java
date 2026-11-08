package dev.aeroscapes.aquatic;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.SeparateTransformsModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.creativecore.client.CreativeCoreClient;
import team.creative.creativecore.client.render.model.layer.CreativeBakedLayerModel;
import team.creative.creativecore.client.render.model.layer.CreativeModelLayerLoader;
import team.creative.creativecore.client.render.model.preview.CreativeUnbakedPreviewItemLayer;

/** Native CreativeCore entry dispatch and NeoForge child selection, without a game client. */
final class ConstructionIconRenderingTest {
  private ConstructionIconRenderingTest() {}

  static List<VanillaEquipmentModelTest.Face> heldFaces(Path assets, String item) {
    try (var baker = new IconBaker(assets)) {
      BakedModel held =
          baker.bake(
              ResourceLocation.parse("littletiles:item/equipment/held/" + item),
              BlockModelRotation.X0_Y0);
      return faces(held);
    }
  }

  static List<VanillaEquipmentModelTest.Face> actualCuboidFaces(JsonObject source, Path assets) {
    try (var baker = new IconBaker(assets)) {
      return faces(
          baker.bakeUncached(
              BlockModel.fromString(source.toString()),
              BlockModelRotation.X0_Y0,
              baker.getModelTextureGetter()));
    }
  }

  private static List<VanillaEquipmentModelTest.Face> faces(BakedModel model) {
    return model.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null).stream()
        .map(
            quad ->
                VanillaEquipmentModelTest.face(quad, quad.getSprite().contents().name().toString()))
        .toList();
  }

  static void verify(Path assets) throws IOException {
    CreativeModelLayerLoader.REGISTRY.register(
        "creativecore:preview", CreativeUnbakedPreviewItemLayer.class);
    registerLoaders();
    RenderSystem.initRenderThread();
    NativeEntry entry = new NativeEntry();
    for (String item : ConstructionIconModels.ITEMS) {
      AtomicInteger previews = new AtomicInteger();
      ItemStack template = new ItemStack(Items.STONE);
      CreativeCoreClient.PREVIEW_MODEL_TYPES.register(
          ResourceLocation.fromNamespaceAndPath("littletiles", item),
          stack -> {
            previews.incrementAndGet();
            return template;
          });
      try (var baker = new IconBaker(assets)) {
        BakedModel root =
            baker.bake(
                ResourceLocation.parse("littletiles:item/" + item), BlockModelRotation.X0_Y0);
        require(root instanceof CreativeBakedLayerModel, "CreativeCore sees its root at HEAD");
        CreativeBakedLayerModel creative = (CreativeBakedLayerModel) root;
        require(creative.layers.size() == 2, "Keep native sprite and live preview layers");
        var layer =
            (team.creative.creativecore.client.render.model.layer.CreativeBakedItemLayer
                    .CreativeBakedItemLayerWrapper)
                creative.layers.getFirst();
        require(
            layer.model instanceof SeparateTransformsModel.Baked, "Route only the sprite layer");
        BakedModel nativeSprite = nativeSprite(item, baker);
        BakedModel held =
            baker.bake(
                ResourceLocation.parse("littletiles:item/equipment/held/" + item),
                BlockModelRotation.X0_Y0);
        require(
            !sameQuads(held, nativeSprite), "The actual held model retains its authored volume");
        RecordingRenderer renderer = RecordingRenderer.create(entry, nativeSprite);
        for (var context : ItemDisplayContext.values()) {
          renderer.draws.clear();
          int previewCalls = previews.get();
          renderer.render(
              new ItemStack(Items.STICK), context, false, new PoseStack(), null, 0, 0, root);
          require(
              renderer.customFallbacks == 0,
              "CreativeCore receives the model before vanilla's unrelated custom renderer");
          require(
              renderer.draws.size() == (context == ItemDisplayContext.GUI ? 2 : 1),
              "Native layer renderer draws the sprite and the GUI-only stored template");
          BakedModel child = renderer.draws.getFirst().model();
          BakedModel expected = context.name().endsWith("_HAND") ? held : nativeSprite;
          require(
              sameQuads(child, expected),
              "The real nested renderer selects the held mesh only in hands: "
                  + item
                  + "/"
                  + context);
          require(
              previews.get() - previewCalls == (context == ItemDisplayContext.GUI ? 1 : 0),
              "Native preview resolves once only in GUI: " + item + "/" + context);
          if (context == ItemDisplayContext.GUI)
            require(
                renderer.draws.getLast().stack() == template,
                "The stored native template reaches the renderer unchanged");
        }
        var oldOuter =
            new SeparateTransformsModel.Baked(
                false, false, false, null, ItemOverrides.EMPTY, root, ImmutableMap.of());
        renderer.draws.clear();
        renderer.render(
            new ItemStack(Items.STICK),
            ItemDisplayContext.GUI,
            false,
            new PoseStack(),
            null,
            0,
            0,
            oldOuter);
        require(
            renderer.customFallbacks == 1 && renderer.draws.isEmpty(),
            "The old outer wrapper reproduces the missed CreativeCore entry dispatch");
      }
    }
    System.out.println(
        "Construction icons: native HEAD dispatch, nested camera routing and stored GUI previews passed");
  }

  private static BakedModel nativeSprite(String item, IconBaker baker) throws IOException {
    JsonObject nativeModel = HeldItemModels.nativeModel("littletiles:" + item);
    BlockModel sprite =
        BlockModel.fromString(nativeModel.getAsJsonArray("layers").get(0).toString());
    return baker.bakeUncached(sprite, BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
  }

  private static boolean sameQuads(BakedModel actual, BakedModel expected) {
    var first = actual.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null);
    var second = expected.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null);
    if (first.isEmpty() || first.size() != second.size()) return false;
    for (int index = 0; index < first.size(); index++)
      if (!Arrays.equals(first.get(index).getVertices(), second.get(index).getVertices())
          || first.get(index).getTintIndex() != second.get(index).getTintIndex()
          || !first
              .get(index)
              .getSprite()
              .contents()
              .name()
              .equals(second.get(index).getSprite().contents().name())) return false;
    return true;
  }

  private record Draw(ItemStack stack, BakedModel model) {}

  private static final class RecordingRenderer extends ItemRenderer {
    private NativeEntry entry;
    private BakedModel template;
    private List<Draw> draws;
    private int customFallbacks;

    private RecordingRenderer() {
      super(null, null, null, null, null);
      throw new AssertionError("Offline rendering must not initialize client renderer registries");
    }

    private static RecordingRenderer create(NativeEntry entry, BakedModel template)
        throws IOException {
      try {
        // The fixture overrides every used method; no live ModelManager or client is constructed.
        Class<?> factoryType = Class.forName("sun.reflect.ReflectionFactory");
        Object factory = factoryType.getMethod("getReflectionFactory").invoke(null);
        var constructor =
            (java.lang.reflect.Constructor<?>)
                factoryType
                    .getMethod(
                        "newConstructorForSerialization",
                        Class.class,
                        java.lang.reflect.Constructor.class)
                    .invoke(
                        factory, RecordingRenderer.class, Object.class.getDeclaredConstructor());
        RecordingRenderer renderer = (RecordingRenderer) constructor.newInstance();
        renderer.entry = entry;
        renderer.template = template;
        renderer.draws = new ArrayList<>();
        return renderer;
      } catch (ReflectiveOperationException failure) {
        throw new IOException("Cannot create the client-free native render consumer", failure);
      }
    }

    @Override
    public void render(
        ItemStack stack,
        ItemDisplayContext context,
        boolean left,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay,
        BakedModel original) {
      if (entry.dispatch(this, stack, context, left, pose, buffer, light, overlay, original))
        return;
      BakedModel transformed = ClientHooks.handleCameraTransforms(pose, original, context, left);
      if (transformed.isCustomRenderer()) customFallbacks++;
      else draws.add(new Draw(stack, transformed));
    }

    @Override
    public BakedModel getModel(ItemStack stack, Level level, LivingEntity entity, int seed) {
      return template;
    }
  }

  /**
   * Execute the provider's handler, changing only its receiver into an explicit renderer argument.
   */
  private static final class NativeEntry extends ClassLoader {
    private final Method handler;

    private NativeEntry() throws IOException {
      super(ConstructionIconRenderingTest.class.getClassLoader());
      ClassNode source = readClass("team/creative/creativecore/mixin/ItemRendererMixin");
      var nativeHandler =
          source.methods.stream()
              .filter(method -> method.name.equals("renderByItem"))
              .findFirst()
              .orElseThrow();
      AnnotationNode inject = nativeHandler.visibleAnnotations.getFirst();
      Object points = inject.values.get(inject.values.indexOf("at") + 1);
      if (!(points instanceof List<?> at) || !(at.getFirst() instanceof AnnotationNode point))
        throw new AssertionError("Expected one CreativeCore entry injection");
      require(
          inject.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
              && point.values.contains("HEAD"),
          "CreativeCore dispatch remains at method entry");
      ClassNode renderer = readClass("net/minecraft/client/renderer/entity/ItemRenderer");
      var render =
          renderer.methods.stream()
              .filter(
                  method ->
                      method.name.equals("render")
                          && method.desc.equals(
                              nativeHandler.desc.substring(
                                      0,
                                      nativeHandler.desc.indexOf(
                                          "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"))
                                  + ")V"))
              .findFirst()
              .orElseThrow();
      int camera = -1, custom = -1, fallback = -1, offset = 0;
      for (var instruction : render.instructions) {
        if (instruction instanceof MethodInsnNode call) {
          if (call.name.equals("handleCameraTransforms")) camera = offset;
          if (call.name.equals("isCustomRenderer")) custom = offset;
          if (call.name.equals("getCustomRenderer")) fallback = offset;
        }
        offset++;
      }
      require(
          camera >= 0 && custom > camera && fallback > custom,
          "Pinned ItemRenderer selects camera child before its custom-renderer fallback");
      nativeHandler.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
      nativeHandler.desc =
          "(Lnet/minecraft/client/renderer/entity/ItemRenderer;" + nativeHandler.desc.substring(1);
      nativeHandler.visibleAnnotations = null;
      nativeHandler.localVariables = null;
      ClassNode fixture = new ClassNode();
      fixture.version = Opcodes.V21;
      fixture.access = Opcodes.ACC_PUBLIC;
      fixture.name = "dev/aeroscapes/aquatic/NativeCreativeCoreEntry";
      fixture.superName = "java/lang/Object";
      fixture.methods.add(nativeHandler);
      ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
      fixture.accept(writer);
      byte[] bytes = writer.toByteArray();
      Class<?> adapted = defineClass(fixture.name.replace('/', '.'), bytes, 0, bytes.length);
      handler =
          Arrays.stream(adapted.getMethods())
              .filter(method -> method.getName().equals("renderByItem"))
              .findFirst()
              .orElseThrow();
    }

    private boolean dispatch(
        ItemRenderer renderer,
        ItemStack stack,
        ItemDisplayContext context,
        boolean left,
        PoseStack pose,
        MultiBufferSource buffer,
        int light,
        int overlay,
        BakedModel model) {
      CallbackInfo callback = new CallbackInfo("render", true);
      try {
        handler.invoke(
            null, renderer, stack, context, left, pose, buffer, light, overlay, model, callback);
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError("Native CreativeCore dispatch failed", failure);
      }
      return callback.isCancelled();
    }

    private static ClassNode readClass(String name) throws IOException {
      try (var stream =
          ConstructionIconRenderingTest.class.getResourceAsStream("/" + name + ".class")) {
        if (stream == null) throw new IOException("Missing pinned renderer class: " + name);
        ClassNode node = new ClassNode();
        new ClassReader(stream).accept(node, 0);
        return node;
      }
    }
  }

  private static void registerLoaders() throws IOException {
    try {
      Field loaders = GeometryLoaderManager.class.getDeclaredField("LOADERS");
      loaders.setAccessible(true);
      loaders.set(
          null,
          ImmutableMap.of(
              ResourceLocation.parse("creativecore:layer"),
              new CreativeModelLayerLoader(),
              ResourceLocation.parse("neoforge:separate_transforms"),
              SeparateTransformsModel.Loader.INSTANCE));
    } catch (ReflectiveOperationException failure) {
      throw new IOException("Cannot register pinned geometry loaders for offline baking", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class IconBaker implements ModelBaker, AutoCloseable {
    private final Path assets;
    private final Map<ResourceLocation, BlockModel> models = new HashMap<>();
    private final Map<ResourceLocation, VanillaEquipmentModelTest.Sprite> sprites = new HashMap<>();

    private IconBaker(Path assets) {
      this.assets = assets;
    }

    @Override
    public UnbakedModel getModel(ResourceLocation location) {
      if (location.equals(ResourceLocation.withDefaultNamespace("builtin/generated")))
        return ModelBakery.GENERATION_MARKER;
      return models.computeIfAbsent(location, this::read);
    }

    private BlockModel read(ResourceLocation location) {
      Path path =
          assets.resolve(location.getNamespace() + "/models/" + location.getPath() + ".json");
      String source;
      try {
        if (Files.isRegularFile(path)) source = Files.readString(path);
        else
          try (var stream =
              ConstructionIconRenderingTest.class.getResourceAsStream(
                  "/assets/"
                      + location.getNamespace()
                      + "/models/"
                      + location.getPath()
                      + ".json")) {
            if (stream == null) throw new IOException("Missing native model: " + location);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
          }
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot read model: " + location, failure);
      }
      JsonObject json = JsonParser.parseString(source).getAsJsonObject();
      return BlockModel.fromString(json.toString());
    }

    @Override
    @SuppressWarnings("deprecation") // Implement the pinned ModelBaker interface.
    public BakedModel bake(ResourceLocation location, ModelState state) {
      return bake(location, state, getModelTextureGetter());
    }

    @Override
    public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
      return getModel(location.id());
    }

    @Override
    public BakedModel bake(
        ResourceLocation location,
        ModelState state,
        Function<Material, TextureAtlasSprite> getter) {
      return bakeUncached(getModel(location), state, getter);
    }

    @Override
    public BakedModel bakeUncached(
        UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> getter) {
      model.resolveParents(this::getModel);
      return model.bake(this, getter, state);
    }

    @Override
    public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
      return material ->
          sprites.computeIfAbsent(material.texture(), VanillaEquipmentModelTest.Sprite::new);
    }

    @Override
    public void close() {
      sprites.values().forEach(VanillaEquipmentModelTest.Sprite::close);
    }
  }
}
