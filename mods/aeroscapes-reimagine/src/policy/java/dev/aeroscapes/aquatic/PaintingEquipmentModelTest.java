package dev.aeroscapes.aquatic;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BuiltInModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.SeparateTransformsModel;
import org.joml.Vector3f;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;
import xerca.xercapaint.client.CanvasItemRenderer;

/** Native palette state graph, baked supplies and actual CPU canvas vertex emission. */
public final class PaintingEquipmentModelTest {
  private PaintingEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject previews = new JsonObject();
    for (int state = 0; state <= 16; state++) {
      String item = "item_palette" + (state == 0 ? "" : state);
      JsonObject model = verifyRoot(assets, item);
      var faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      VanillaEquipmentModelTest.noOverlaps(item, faces);
      paletteColors(assets, item, model);
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      preview.add(
          "attachments",
          ConstructionPlacementTest.attachments(model, item, ToolKind.INGREDIENT, 0));
      JsonArray thirdPerson = thirdPersonAttachments(model);
      preview.add("thirdPersonAttachments", thirdPerson);
      paletteScale(faces, preview.getAsJsonArray("attachments"), thirdPerson);
      previews.add("xercapaint:" + item, preview);
    }
    JsonObject easel = verifyRoot(assets, "item_easel");
    var easelFaces = VanillaEquipmentModelTest.bakeObj(easel, assets);
    VanillaEquipmentModelTest.noOverlaps("xercapaint:item_easel", easelFaces);
    JsonObject easelPreview = VanillaEquipmentModelTest.preview(easel, easelFaces);
    easelPreview.add(
        "attachments",
        ConstructionPlacementTest.attachments(easel, "item_easel", ToolKind.INGREDIENT, 0));
    easelPreview.add("thirdPersonAttachments", thirdPersonAttachments(easel));
    previews.add("xercapaint:item_easel", easelPreview);
    for (String item : PaintingEquipmentModels.CANVASES) {
      JsonObject model = verifyRoot(assets, item);
      require(
          model.get("parent").getAsString().equals("minecraft:builtin/entity"),
          "Blank hands select native renderer");
      canvasStateTransforms(item, model);
      dispatch(model);
      boolean large = item.endsWith("large") || item.endsWith("long");
      boolean tall = item.endsWith("large") || item.endsWith("tall");
      int width = large ? 32 : 16, height = tall ? 32 : 16;
      boolean glass = item.contains("glass");
      for (boolean drawn : new boolean[] {false, true}) {
        var capture = new Capture();
        if (drawn) nativePainted(width, height, glass, false, capture);
        else {
          var renderer = new CanvasItemRenderer(null, null);
          var method =
              CanvasItemRenderer.class.getDeclaredMethod(
                  "renderEmptyCanvas",
                  PoseStack.class,
                  MultiBufferSource.class,
                  float.class,
                  float.class,
                  int.class,
                  boolean.class);
          method.setAccessible(true);
          method.invoke(
              renderer, new PoseStack(), capture, (float) width, (float) height, 15728880, glass);
        }
        JsonObject preview = capture.preview(model);
        preview.add(
            "attachments",
            ConstructionPlacementTest.attachments(model, item, ToolKind.INGREDIENT, 0));
        preview.add("thirdPersonAttachments", thirdPersonAttachments(model));
        previews.add("xercapaint:" + item + (drawn ? "/drawn" : "/blank"), preview);
        require(
            capture.vertices.size() == (glass && !drawn ? 16 : 24),
            "Native canvas has expected complete surfaces");
      }
      var sides = new Capture();
      nativePainted(width, height, glass, true, sides);
      require(
          sides.vertices.size() > 24, "Native painted side pixels survive material/type routing");
      JsonObject sidePreview = sides.preview(model);
      sidePreview.add(
          "attachments",
          ConstructionPlacementTest.attachments(model, item, ToolKind.INGREDIENT, 0));
      sidePreview.add("thirdPersonAttachments", thirdPersonAttachments(model));
      previews.add("xercapaint:" + item + "/painted_sides", sidePreview);
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Painting equipment: 10 registered roots / 34 primary native states plus eight painted-edge cases; native canvas CPU renderer and separate hand dispatch passed");
  }

  @SuppressWarnings("deprecation")
  private static void canvasStateTransforms(String item, JsonObject held) throws IOException {
    var root = HeldItemModels.nativeModel("xercapaint:" + item);
    String target =
        root.getAsJsonArray("overrides").get(0).getAsJsonObject().get("model").getAsString();
    BlockModel nativeDrawn =
        BlockModel.fromString(HeldItemModels.nativeModel(target.replace(":item/", ":")).toString());
    BlockModel blank = BlockModel.fromString("{\"display\":" + held.get("display") + "}");
    for (var context : ItemDisplayContext.values()) {
      if (!context.firstPerson()
          && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
          && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) continue;
      boolean left =
          context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      PoseStack a = new PoseStack(), b = new PoseStack();
      nativeDrawn.getTransforms().getTransform(context).apply(left, a);
      blank.getTransforms().getTransform(context).apply(left, b);
      require(
          a.last().pose().equals(b.last().pose(), .000001f),
          item + " preserves native drawn/blank hand alignment in " + context);
    }
  }

  private static void paletteColors(Path assets, String item, JsonObject model) throws IOException {
    var empty = NativeEquipmentModels.image("xercapaint:item/item_palette");
    var nativeSprite = NativeEquipmentModels.image("xercapaint:item/" + item);
    Map<PaintSpot, Integer> expected = new java.util.HashMap<>();
    for (int y = 0; y < nativeSprite.getHeight(); y++)
      for (int x = 0; x < nativeSprite.getWidth(); x++) {
        int color = nativeSprite.getRGB(x, y);
        if (color != empty.getRGB(x, y))
          expected.merge(new PaintSpot(color, x, y), 1, Integer::sum);
      }
    Map<PaintSpot, Integer> actual = new java.util.HashMap<>();
    for (var face :
        VanillaEquipmentModelTest.bakeObj(model, assets, "native_cumulative_paint_state")) {
      if (face.normal().z < .99) continue;
      var image =
          javax.imageio.ImageIO.read(
              assets.resolve(face.texture().replace(":", "/textures/") + ".png").toFile());
      float u = 0, v = 0;
      for (var point : face.uv()) {
        u += point.getAsJsonArray().get(0).getAsFloat() / 4;
        v += point.getAsJsonArray().get(1).getAsFloat() / 4;
      }
      int color =
          image.getRGB(
              Math.min(image.getWidth() - 1, (int) (u / 16 * image.getWidth())),
              Math.min(image.getHeight() - 1, (int) (v / 16 * image.getHeight())));
      Vector3f center = new Vector3f();
      for (var vertex : face.vertices()) center.add(vertex);
      center.mul(4);
      int imageX = Math.round(center.x - .5f), imageY = Math.round(14.5f - center.y);
      require(
          Math.abs(center.x - imageX - .5f) < .00001f
              && Math.abs(center.y + imageY - 14.5f) < .00001f,
          item + " keeps each native paint dab at its source pixel center");
      actual.merge(new PaintSpot(color, imageX, imageY), 1, Integer::sum);
    }
    require(
        actual.equals(expected),
        item
            + " actual baked paint colors/positions match its exact native sprite, including the provider's15-to16 replacement");
  }

  private record PaintSpot(int color, int imageX, int imageY) {}

  @SuppressWarnings("deprecation")
  private static JsonArray thirdPersonAttachments(JsonObject source)
      throws ReflectiveOperationException {
    JsonArray attachments = new JsonArray();
    BlockModel model = BlockModel.fromString("{\"display\":" + source.get("display") + "}");
    for (boolean left : new boolean[] {false, true}) {
      var root =
          LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64)
              .bakeRoot();
      PlayerModel<LivingEntity> playerModel = new PlayerModel<>(root, false);
      playerModel.leftArmPose = left ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
      playerModel.rightArmPose = left ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
      var poseArm =
          HumanoidModel.class.getDeclaredMethod(
              left ? "poseLeftArm" : "poseRightArm", LivingEntity.class);
      poseArm.setAccessible(true);
      poseArm.invoke(playerModel, new Object[] {null});
      AnimationUtils.bobArms(playerModel.rightArm, playerModel.leftArm, 0);
      var arm = root.getChild(left ? "left_arm" : "right_arm");
      PoseStack rendererRoot = new PoseStack();
      rendererRoot.scale(-1, -1, 1);
      rendererRoot.scale(.9375f, .9375f, .9375f);
      rendererRoot.translate(0, -1.501, 0);
      PoseStack skin = new PoseStack();
      skin.mulPose(rendererRoot.last().pose());
      arm.translateAndRotate(skin);
      PoseStack held = new PoseStack();
      PoseStack layerPose = new PoseStack();
      layerPose.mulPose(rendererRoot.last().pose());
      ItemDisplayContext context =
          left
              ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
              : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      ItemInHandRenderer recorder =
          new ItemInHandRenderer(null, null, null) {
            @Override
            public void renderItem(
                LivingEntity entity,
                ItemStack stack,
                ItemDisplayContext displayContext,
                boolean leftHand,
                PoseStack pose,
                MultiBufferSource buffers,
                int light) {
              model.getTransforms().getTransform(displayContext).apply(leftHand, pose);
              held.setIdentity();
              held.mulPose(pose.last().pose());
            }
          };
      RenderLayerParent<LivingEntity, PlayerModel<LivingEntity>> parent =
          new RenderLayerParent<>() {
            @Override
            public PlayerModel<LivingEntity> getModel() {
              return playerModel;
            }

            @Override
            public ResourceLocation getTextureLocation(LivingEntity entity) {
              return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
            }
          };
      var layer = new ItemInHandLayer<>(parent, recorder);
      var renderArm =
          ItemInHandLayer.class.getDeclaredMethod(
              "renderArmWithItem",
              LivingEntity.class,
              ItemStack.class,
              ItemDisplayContext.class,
              HumanoidArm.class,
              PoseStack.class,
              MultiBufferSource.class,
              int.class);
      renderArm.setAccessible(true);
      renderArm.invoke(
          layer,
          null,
          new ItemStack(Items.PAPER),
          context,
          left ? HumanoidArm.LEFT : HumanoidArm.RIGHT,
          layerPose,
          null,
          15728880);
      JsonObject attachment = new JsonObject();
      attachment.addProperty("context", context.name());
      attachment.addProperty(
          "consumer",
          "native ItemInHandLayer invocation; native ITEM arm pose and idle bob; PlayerRenderer .9375 root");
      attachment.add("item", matrix(held.last().pose()));
      attachment.add("skin", matrix(skin.last().pose()));
      JsonArray cubes = new JsonArray();
      arm.visit(
          new PoseStack(),
          (pose, path, index, cube) -> {
            JsonObject bounds = new JsonObject();
            bounds.add("from", vector(cube.minX, cube.minY, cube.minZ));
            bounds.add("to", vector(cube.maxX, cube.maxY, cube.maxZ));
            cubes.add(bounds);
          });
      attachment.add("nativeArmCubes", cubes);
      JsonArray body = new JsonArray();
      for (String part : List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg"))
        root.getChild(part)
            .visit(
                rendererRoot,
                (pose, path, index, cube) -> {
                  JsonObject bounds = new JsonObject();
                  bounds.addProperty("part", part);
                  bounds.add("from", vector(cube.minX, cube.minY, cube.minZ));
                  bounds.add("to", vector(cube.maxX, cube.maxY, cube.maxZ));
                  bounds.add("transform", matrix(pose.pose()));
                  body.add(bounds);
                });
      attachment.add("nativeBodyCubes", body);
      attachments.add(attachment);
    }
    return attachments;
  }

  private static void paletteScale(
      List<VanillaEquipmentModelTest.Face> faces, JsonArray firstPerson, JsonArray thirdPerson) {
    float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
    for (var face : faces)
      for (var vertex : face.vertices()) {
        minX = Math.min(minX, vertex.x);
        maxX = Math.max(maxX, vertex.x);
      }
    for (JsonArray attachments : List.of(firstPerson, thirdPerson))
      for (var element : attachments) {
        var rows = element.getAsJsonObject().getAsJsonArray("item");
        var transform = new org.joml.Matrix4f();
        for (int row = 0; row < 4; row++)
          for (int column = 0; column < 4; column++)
            transform.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
        var low = transform.transformPosition(new Vector3f(minX - .5f, 0, 0));
        var high = transform.transformPosition(new Vector3f(maxX - .5f, 0, 0));
        require(
            high.distance(low) >= .35f && high.distance(low) <= .46f,
            "Artist palette spans 35–46 cm through each native hand consumer");
      }
  }

  private static JsonArray matrix(org.joml.Matrix4f matrix) {
    JsonArray rows = new JsonArray();
    for (int row = 0; row < 4; row++) {
      JsonArray values = new JsonArray();
      for (int column = 0; column < 4; column++) values.add(matrix.get(column, row));
      rows.add(values);
    }
    return rows;
  }

  private static JsonObject verifyRoot(Path assets, String item) throws IOException {
    JsonObject wrapper = read(assets, "xercapaint/models/item/" + item + ".json");
    JsonObject nativeModel = HeldItemModels.nativeModel("xercapaint:" + item);
    var overrides = nativeModel.remove("overrides");
    require(nativeModel.equals(wrapper.get("base")), item + " preserves native GUI/ground/fixed");
    require(
        java.util.Objects.equals(overrides, wrapper.get("overrides")),
        item + " preserves exact native predicates/aliases");
    for (String context :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand"))
      require(
          wrapper
              .getAsJsonObject("perspectives")
              .getAsJsonObject(context)
              .get("parent")
              .getAsString()
              .equals("xercapaint:item/equipment/held/" + item),
          item + " routes each hand state");
    return read(assets, "xercapaint/models/item/equipment/held/" + item + ".json");
  }

  @SuppressWarnings("deprecation")
  private static void dispatch(JsonObject source) {
    BlockModel transforms = BlockModel.fromString("{\"display\":" + source.get("display") + "}");
    try (var sprite =
        new VanillaEquipmentModelTest.Sprite(
            ResourceLocation.parse("xercapaint:item/item_canvas"))) {
      BakedModel nativeRenderer =
          new BuiltInModel(transforms.getTransforms(), ItemOverrides.EMPTY, sprite, false);
      BakedModel nativeIcon =
          new net.minecraft.client.resources.model.SimpleBakedModel.Builder(
                  transforms, ItemOverrides.EMPTY, false)
              .particle(sprite)
              .build();
      var hands = ImmutableMap.<ItemDisplayContext, BakedModel>builder();
      for (var context : ItemDisplayContext.values())
        if (context.firstPerson()
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
          hands.put(context, nativeRenderer);
      BakedModel wrapper =
          new SeparateTransformsModel.Baked(
              false, false, false, sprite, ItemOverrides.EMPTY, nativeIcon, hands.build());
      for (var context : ItemDisplayContext.values()) {
        boolean hand =
            context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        BakedModel selected =
            ClientHooks.handleCameraTransforms(
                new PoseStack(),
                wrapper,
                context,
                context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        require(
            selected.isCustomRenderer() == hand,
            "Actual NeoForge camera selection dispatches native canvas renderer only in hands");
      }
    }
  }

  private static void nativePainted(
      int width, int height, boolean glass, boolean sides, Capture capture)
      throws ReflectiveOperationException {
    Class<?> outerType = Class.forName("xerca.xercapaint.client.RenderEntityCanvas");
    Object outer = allocate(outerType);
    set(outerType, outer, "whiteLocation", ResourceLocation.parse("inspection:textures/white.png"));
    Class<?> type = Class.forName("xerca.xercapaint.client.RenderEntityCanvas$Instance");
    Object instance = allocate(type);
    set(type, instance, "this$0", outer);
    set(type, instance, "width", width);
    set(type, instance, "height", height);
    set(type, instance, "location", ResourceLocation.parse("inspection:textures/canvas.png"));
    set(type, instance, "sidesActive", sides);
    int[] pixels = new int[2 * (width + height)];
    for (int i = 0; i < pixels.length; i++)
      pixels[i] = 0xFF000000 | ((i * 17 & 255) << 16) | ((i * 31 & 255) << 8) | (i * 47 & 255);
    set(type, instance, "sidePixels", pixels);
    var method =
        type.getDeclaredMethod(
            "render",
            Class.forName("xerca.xercapaint.entity.EntityCanvas"),
            float.class,
            float.class,
            PoseStack.class,
            MultiBufferSource.class,
            net.minecraft.core.Direction.class,
            int.class,
            boolean.class,
            int.class);
    method.invoke(
        instance,
        null,
        0f,
        0f,
        new PoseStack(),
        capture,
        net.minecraft.core.Direction.UP,
        15728880,
        glass,
        0xFFFFFFFF);
  }

  private static Object allocate(Class<?> type) throws ReflectiveOperationException {
    // Execute native CPU vertex emission without constructing client texture/GPU services.
    Class<?> factoryType = Class.forName("sun.reflect.ReflectionFactory");
    Object factory = factoryType.getMethod("getReflectionFactory").invoke(null);
    var constructor =
        (java.lang.reflect.Constructor<?>)
            factoryType
                .getMethod(
                    "newConstructorForSerialization",
                    Class.class,
                    java.lang.reflect.Constructor.class)
                .invoke(factory, type, Object.class.getDeclaredConstructor());
    return constructor.newInstance();
  }

  private static void set(Class<?> type, Object object, String name, Object value)
      throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }

  private static final class Vertex {
    final Vector3f position;
    final String texture;
    float u, v;
    int color = 0xFFFFFFFF;

    Vertex(float x, float y, float z, String texture) {
      position = new Vector3f(x, y, z);
      this.texture = texture;
    }
  }

  private static final class Capture implements MultiBufferSource {
    final List<Vertex> vertices = new ArrayList<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
      String texture = null;
      for (String name :
          List.of(
              "minecraft:textures/block/birch_planks.png",
              "minecraft:textures/block/glass.png",
              "xercapaint:textures/block/empty.png",
              "inspection:textures/canvas.png",
              "inspection:textures/white.png")) {
        ResourceLocation location = ResourceLocation.parse(name);
        if (type == RenderType.entitySolid(location)
            || type == RenderType.entityCutout(location)
            || type == RenderType.entityCutoutNoCull(location)
            || type == RenderType.entityTranslucent(location)) texture = name;
      }
      if (texture == null) throw new AssertionError("Unexpected native canvas material: " + type);
      String selected = texture;
      return new VertexConsumer() {
        private Vertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
          current = new Vertex(x, y, z, selected);
          vertices.add(current);
          return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
          current.color = a << 24 | r << 16 | g << 8 | b;
          return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
          current.u = u;
          current.v = v;
          return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
          return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
          return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
          return this;
        }
      };
    }

    JsonObject preview(JsonObject model) {
      JsonObject preview = new JsonObject();
      preview.add("textures", new JsonObject());
      preview.add("display", model.get("display").deepCopy());
      JsonArray quads = new JsonArray();
      require(vertices.size() % 4 == 0, "Native renderer completes every quad");
      for (int index = 0; index < vertices.size(); index += 4) {
        JsonObject quad = new JsonObject();
        Vertex first = vertices.get(index);
        String texture = first.texture.replace(":textures/", ":").replace(".png", "");
        if (texture.equals("inspection:white"))
          texture = "inspection:pixel_" + Integer.toHexString(first.color);
        quad.addProperty("texture", texture);
        JsonArray points = new JsonArray(), uv = new JsonArray();
        for (int point = 0; point < 4; point++) {
          Vertex v = vertices.get(index + point);
          points.add(vector(v.position.x * 16, v.position.y * 16, v.position.z * 16));
          uv.add(vector(v.u * 16, v.v * 16));
        }
        quad.add("vertices", points);
        quad.add("uv", uv);
        Vector3f normal =
            new Vector3f(vertices.get(index + 1).position)
                .sub(first.position)
                .cross(new Vector3f(vertices.get(index + 2).position).sub(first.position))
                .normalize();
        require(
            Float.isFinite(normal.x + normal.y + normal.z),
            "Native canvas surface has positive area");
        quad.add("normal", vector(normal.x, normal.y, normal.z));
        quads.add(quad);
      }
      preview.add("quads", quads);
      return preview;
    }
  }

  private static JsonArray vector(float... values) {
    JsonArray array = new JsonArray();
    for (float value : values) array.add(value);
    return array;
  }

  private static JsonObject read(Path assets, String path) throws IOException {
    return JsonParser.parseString(Files.readString(assets.resolve(path))).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
