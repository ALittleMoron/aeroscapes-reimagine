package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.obj.EquipmentObjFixture;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import org.joml.Vector3f;

/** Native parsing/baking of the distributed models, without starting a Minecraft client. */
public final class VanillaEquipmentModelTest {
  private VanillaEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview output");
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject previews = new JsonObject();
    resizeAnchors();
    brushGrip(assets, arguments.length == 2 ? previews : null);
    int tested = 0;
    for (var entry : NativeEquipmentModels.models().entrySet()) {
      String id = entry.getKey();
      if (!id.startsWith("minecraft:")) continue;
      JsonObject model =
          JsonParser.parseString(
                  Files.readString(
                      assets.resolve(
                          "minecraft/models/item/equipment/held/" + id.substring(10) + ".json")))
              .getAsJsonObject();
      for (String hand :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand"))
        require(
            model.getAsJsonObject("display").has(hand),
            "Both hands retain explicit transforms: " + id);
      List<Face> faces = model.has("loader") ? bakeObj(model, assets) : bakeCuboids(model);
      require(!faces.isEmpty(), "The native baker emits visible geometry: " + id);
      noOverlaps(id, faces);
      for (Face face : faces) {
        require(
            Float.isFinite(face.normal.x + face.normal.y + face.normal.z),
            "Baked faces have positive area: " + id);
        for (var coordinate : face.uv)
          for (var value : coordinate.getAsJsonArray())
            require(
                value.getAsDouble() >= -.00001 && value.getAsDouble() <= 16.00001,
                "Atlas coordinates remain inside the material sprite: " + id);
      }
      if (id.contains("compass")) compass(id, model, faces);
      if (id.contains("clock")) clock(id, model, faces);
      if (id.equals("minecraft:lead") || id.endsWith("goat_horn")) closedSurface(id, faces);
      if (List.of(
              "minecraft:clock",
              "minecraft:compass",
              "minecraft:recovery_compass",
              "minecraft:goat_horn",
              "minecraft:lead")
          .contains(id)) {
        previews.add(id, preview(model, faces));
      }
      tested++;
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Vanilla equipment: "
            + tested
            + " native-parsed/baked states, atlas UVs, physical pointers and day/night phase passed");
  }

  static JsonObject preview(JsonObject model, List<Face> faces) {
    JsonObject preview = new JsonObject();
    preview.add("textures", model.get("textures").deepCopy());
    preview.add("display", model.get("display").deepCopy());
    JsonArray quads = new JsonArray();
    for (Face face : faces) {
      JsonObject quad = new JsonObject();
      String texture = face.texture;
      while (texture.startsWith("#"))
        texture = model.getAsJsonObject("textures").get(texture.substring(1)).getAsString();
      quad.addProperty("texture", texture);
      JsonArray vertices = new JsonArray();
      for (Vector3f vertex : face.vertices)
        vertices.add(vector(vertex.x * 16, vertex.y * 16, vertex.z * 16));
      quad.add("vertices", vertices);
      quad.add("uv", face.uv);
      quad.add("normal", vector(face.normal.x, face.normal.y, face.normal.z));
      quad.addProperty("blockLight", net.minecraft.client.renderer.LightTexture.block(face.light));
      quads.add(quad);
    }
    preview.add("quads", quads);
    return preview;
  }

  @SuppressWarnings("deprecation") // Verify the actual native hand transform application.
  private static void brushGrip(Path assets, JsonObject previews) throws IOException {
    for (String state : List.of("", "_0", "_1", "_2")) {
      var model =
          BlockModel.fromString(
              Files.readString(
                  assets.resolve("minecraft/models/item/equipment/held_brush" + state + ".json")));
      if (previews != null
          && VanillaEquipmentModelTest.class.getResource(
                  "/assets/minecraft/models/item/held_brush" + state + ".json")
              != null) {
        JsonObject provider = HeldItemModels.nativeModel("minecraft:held_brush" + state);
        provider.add(
            "display",
            JsonParser.parseString(
                    Files.readString(
                        assets.resolve(
                            "minecraft/models/item/equipment/held_brush" + state + ".json")))
                .getAsJsonObject()
                .get("display"));
        previews.add("minecraft:brush" + state, preview(provider, bakeCuboids(provider)));
      }
      palmGrip(
          JsonParser.parseString(
                  Files.readString(
                      assets.resolve(
                          "minecraft/models/item/equipment/held_brush" + state + ".json")))
              .getAsJsonObject(),
          new Vector3f(8, 1, 15.5f));
      for (var context :
          List.of(
              net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
              net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
              net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
              net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
        boolean left = context.name().endsWith("LEFT_HAND");
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        model.getTransforms().getTransform(context).apply(left, pose);
        Vector3f handle =
            pose.last()
                .pose()
                .transformPosition(new Vector3f(0, -7, context.firstPerson() ? 7.5f : 5).div(16));
        require(
            handle.length() < .000001,
            "The brush handle must meet the attachment origin in "
                + context
                + state
                + ": "
                + handle);
      }
    }
  }

  @SuppressWarnings("deprecation") // Compare with the native ModelPart and ItemTransform consumers.
  private static void palmGrip(JsonObject source, Vector3f grip) throws IOException {
    JsonArray frames;
    try (var stream =
        VanillaEquipmentModelTest.class.getResourceAsStream("/held-arm-frames.json")) {
      if (stream == null) throw new IOException("Missing recorded arm frames");
      frames =
          JsonParser.parseReader(
                  new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonArray();
    }
    var handDisplay = new JsonObject();
    handDisplay.add("display", source.get("display"));
    var model = BlockModel.fromString(handDisplay.toString());
    for (var frameData : frames) {
      var frame = new org.joml.Matrix4f();
      var rows = frameData.getAsJsonArray();
      for (int row = 0; row < 4; row++)
        for (int column = 0; column < 4; column++)
          frame.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
      var nativeArm = new net.minecraft.client.model.geom.ModelPart(List.of(), Map.of());
      nativeArm.setPos(frame.m30(), frame.m31(), frame.m32());
      nativeArm.xRot = (float) Math.atan2(frame.m12(), frame.m22());
      nativeArm.yRot = (float) Math.asin(-frame.m02());
      nativeArm.zRot = (float) Math.atan2(frame.m01(), frame.m00());
      var root = new org.joml.Matrix4f().translate(.2f, -.3f, .1f).rotateXYZ(.2f, -.4f, .1f);
      for (boolean slim : new boolean[] {false, true})
        for (var context :
            List.of(
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
          boolean left = context.name().endsWith("LEFT_HAND");
          float side = left ? 1 : -1;
          var skin = new com.mojang.blaze3d.vertex.PoseStack();
          skin.mulPose(root);
          if (slim) skin.translate(side * .5f / 16, 0, 0);
          nativeArm.translateAndRotate(skin);
          Vector3f palm =
              skin.last()
                  .pose()
                  .transformPosition(new Vector3f(side * (slim ? .5f : 1f) / 16, .5f, 0));
          var held = dev.aeroscapes.atmosphere.HeldEquipmentGrip.palm(root, frame, left, slim);
          model.getTransforms().getTransform(context).apply(left, held);
          Vector3f handle =
              held.last().pose().transformPosition(new Vector3f(grip).sub(8, 8, 8).div(16));
          require(
              handle.distance(palm) < .00001,
              "The rigid tool grip follows the native palm through recorded arm poses, including slim arms: "
                  + handle
                  + " vs "
                  + palm);
          var inHand = new org.joml.Matrix4f(skin.last().pose()).invert().mul(held.last().pose());
          for (float x : new float[] {6, 10})
            for (float y : new float[] {-1, 3})
              for (float z : new float[] {8, 10}) {
                Vector3f ferrule =
                    inHand.transformPosition(new Vector3f(x, y, z).sub(8, 8, 8).div(16));
                require(
                    ferrule.z * 16 < -2.05,
                    "The entire brush ferrule stays beyond the fist, leaving the wooden handle visible: "
                        + ferrule);
              }
        }
    }
  }

  @SuppressWarnings("deprecation") // Exercises the exact 1.21.1 ItemTransform application.
  private static void resizeAnchors() throws IOException {
    JsonObject original = NativeEquipmentModels.inherited("minecraft:compass");
    JsonObject display = original.getAsJsonObject("display");
    for (String hand : List.of("firstperson", "thirdperson"))
      if (!display.has(hand + "_lefthand"))
        display.add(hand + "_lefthand", display.get(hand + "_righthand").deepCopy());
    JsonObject resized = original.deepCopy();
    Vector3f grip = new Vector3f(9, 3.17f, 7);
    HeldItemModels.resizeHeld(resized.getAsJsonObject("display"), .42f, .5f, grip);
    var before = BlockModel.fromString(original.toString());
    var after = BlockModel.fromString(resized.toString());
    for (var context :
        List.of(
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      boolean left =
          context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      var nativePose = new com.mojang.blaze3d.vertex.PoseStack();
      var resizedPose = new com.mojang.blaze3d.vertex.PoseStack();
      before.getTransforms().getTransform(context).apply(left, nativePose);
      after.getTransforms().getTransform(context).apply(left, resizedPose);
      Vector3f point = new Vector3f(grip).sub(8, 8, 8).div(16);
      Vector3f anchor = nativePose.last().pose().transformPosition(new Vector3f(point));
      Vector3f moved = resizedPose.last().pose().transformPosition(new Vector3f(point));
      require(
          anchor.distance(moved) < .000001, "Resizing preserves an off-centre grip in " + context);
      Vector3f edge =
          resizedPose.last().pose().transformPosition(new Vector3f(point).add(1f / 16, 0, 0));
      float expected = context.firstPerson() ? .42f : .5f;
      require(
          Math.abs(edge.distance(moved) - expected / 16) < .000001,
          "The native transform applies the requested physical scale in " + context);
    }
  }

  private static void compass(String id, JsonObject model, List<Face> faces) {
    boolean recovery = id.contains("recovery");
    String material = "aeroscapes_reimagine:item/vanilla_equipment/" + (recovery ? "cyan" : "red");
    String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
    int frame = Integer.parseInt(texture.substring(texture.lastIndexOf('_') + 1));
    double[][] cardinal = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
    double[] anchor = cardinal[frame / 8];
    double turn = (frame % 8) * Math.PI / 16;
    double expectedX = anchor[0] * Math.cos(turn) + anchor[1] * Math.sin(turn);
    double expectedY = anchor[1] * Math.cos(turn) - anchor[0] * Math.sin(turn);
    for (int side : new int[] {-1, 1}) {
      Vector3f tip = null;
      float distance = 0;
      for (Face face : faces)
        if (face.texture.equals(material) && face.normal.z * side > .5)
          for (Vector3f vertex : face.vertices) {
            float length =
                (vertex.x - .5f) * (vertex.x - .5f) + (vertex.y - .5f) * (vertex.y - .5f);
            if (length > distance && length < 9f / 256) {
              distance = length;
              tip = vertex;
            }
          }
      require(tip != null, "A solid colored pointer is visible on both sides: " + id);
      double dot = ((tip.x - .5) * expectedX + (tip.y - .5) * expectedY) / Math.sqrt(distance);
      require(dot > .999, "The baked pointer follows the native frame without mirroring: " + id);
      require(
          Math.abs(tip.z - .5) * 16 > .32,
          "The pointer is physically above the recessed dial: " + id);
    }
  }

  private static void clock(String id, JsonObject model, List<Face> faces) {
    String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
    int frame = Integer.parseInt(texture.substring(texture.lastIndexOf('_') + 1));
    for (int side : new int[] {-1, 1})
      for (String body : List.of("sun", "moon")) {
        List<Vector3f> points = new ArrayList<>();
        for (Face face : faces)
          if (face.texture.endsWith("/" + body) && face.normal.z * side > .9)
            points.addAll(face.vertices);
        require(!points.isEmpty(), "Both celestial bodies are retained behind the horizon: " + id);
        float lowX = Float.MAX_VALUE,
            highX = -Float.MAX_VALUE,
            lowY = Float.MAX_VALUE,
            highY = -Float.MAX_VALUE;
        for (Vector3f point : points) {
          lowX = Math.min(lowX, point.x);
          highX = Math.max(highX, point.x);
          lowY = Math.min(lowY, point.y);
          highY = Math.max(highY, point.y);
        }
        double angle = Math.PI / 2 - frame * Math.PI / 32 + (body.equals("moon") ? Math.PI : 0);
        require(
            Math.abs((lowX + highX) / 2 - (8 + Math.cos(angle) * 2.55) / 16) < .0001
                && Math.abs((lowY + highY) / 2 - (8 + Math.sin(angle) * 2.55) / 16) < .0001,
            "The native clock frame retains its day/night phase: " + id);
      }
  }

  static List<Face> bakeObj(JsonObject source, Path assets) throws IOException {
    return bakeObj(source, assets, null);
  }

  static List<Face> bakeObj(JsonObject source, Path assets, String component) throws IOException {
    return bakeObj(source, assets, component, Function.identity());
  }

  static List<Face> bakeObjWithAtlas(
      JsonObject source, Path assets, java.util.Set<ResourceLocation> registered)
      throws IOException {
    return bakeObj(
        source,
        assets,
        null,
        texture ->
            texture.getPath().startsWith("item/") || registered.contains(texture)
                ? texture
                : net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
  }

  private static List<Face> bakeObj(
      JsonObject source,
      Path assets,
      String component,
      Function<ResourceLocation, ResourceLocation> resolveSprite)
      throws IOException {
    ResourceLocation location = ResourceLocation.parse(source.get("model").getAsString());
    ObjModel parsed = objGeometry(source, assets);
    var definition = source.deepCopy();
    definition.remove("loader");
    var contextBuilder =
        StandaloneGeometryBakingContext.builder(
                BlockModel.fromString(definition.toString()).customData)
            .withUseAmbientOcclusion(false)
            .withRenderTypeHint(null);
    if (component != null) {
      var visible = new it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap<String>();
      for (String name : parsed.getConfigurableComponentNames())
        visible.put(name, name.equals(component));
      contextBuilder.withVisibleComponents(visible);
    }
    var context = contextBuilder.build(location);
    Map<ResourceLocation, Sprite> sprites = new HashMap<>();
    try {
      BakedModel baked =
          parsed.bake(
              context,
              new NoChildModels(),
              material ->
                  sprites.computeIfAbsent(
                      material.texture(), name -> new Sprite(resolveSprite.apply(name))),
              BlockModelRotation.X0_Y0,
              ItemOverrides.EMPTY);
      List<Face> result = new ArrayList<>();
      for (BakedQuad quad :
          baked.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null)) {
        Face face = face(quad, quad.getSprite().contents().name().toString());
        int[] vertices = quad.getVertices();
        for (int[] triangle : List.of(new int[] {0, 1, 2}, new int[] {0, 2, 3})) {
          Vector3f a = face.vertices.get(triangle[0]),
              b = face.vertices.get(triangle[1]),
              c = face.vertices.get(triangle[2]);
          Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
          if (normal.lengthSquared() == 0) continue;
          require(
              normal.normalize().dot(face.normal) > .98,
              "Both rendered triangles follow the authored normal: " + location);
          double[][] coordinates = new double[3][2];
          for (int point = 0; point < 3; point++)
            for (int axis = 0; axis < 2; axis++)
              coordinates[point][axis] =
                  face.uv.get(triangle[point]).getAsJsonArray().get(axis).getAsDouble();
          require(
              Math.abs(cross(coordinates[0], coordinates[1], coordinates[2])) > .0000000001,
              "Each rendered triangle has a nondegenerate texture mapping: " + location);
        }
        for (int index = 0; index < 4; index++) {
          int packed = vertices[index * 8 + 7];
          Vector3f normal =
              new Vector3f(
                  (byte) packed / 127f, (byte) (packed >> 8) / 127f, (byte) (packed >> 16) / 127f);
          require(
              normal.dot(face.normal) > .98, "Authored OBJ normals follow the real baked winding");
        }
        result.add(face);
      }
      return result;
    } finally {
      sprites.values().forEach(Sprite::close);
    }
  }

  private static ObjModel objGeometry(JsonObject source, Path assets) throws IOException {
    ResourceLocation location = ResourceLocation.parse(source.get("model").getAsString());
    Path obj = assets.resolve(location.getNamespace()).resolve(location.getPath());
    var settings =
        new ObjModel.ModelSettings(
            location,
            false,
            true,
            false,
            source.has("emissive_ambient") && source.get("emissive_ambient").getAsBoolean(),
            null);
    try (var material = Files.newInputStream(obj.resolveSibling("equipment.mtl"))) {
      return EquipmentObjFixture.parse(Files.readString(obj), material, settings);
    }
  }

  @SuppressWarnings(
      "deprecation") // Exercise the pinned BlockModel/ElementsModel native cuboid bake.
  static List<Face> bakeComposite(JsonObject source, Path assets) throws IOException {
    List<BlockModel> nativeChildren = new ArrayList<>();
    var gson =
        new com.google.gson.GsonBuilder()
            .registerTypeAdapter(
                BlockModel.class,
                (com.google.gson.JsonDeserializer<BlockModel>)
                    (json, type, context) -> {
                      JsonObject definition = json.getAsJsonObject().deepCopy();
                      var loader = definition.remove("loader");
                      BlockModel model = BlockModel.fromString(definition.toString());
                      if (loader != null) {
                        require(
                            loader.getAsString().equals("neoforge:obj"),
                            "Composite child uses the supported OBJ loader");
                        try {
                          model.customData.setCustomGeometry(objGeometry(definition, assets));
                        } catch (IOException failure) {
                          throw new com.google.gson.JsonParseException(
                              "Cannot load composite OBJ child", failure);
                        }
                      } else nativeChildren.add(model);
                      return model;
                    })
            .registerTypeAdapter(
                net.neoforged.neoforge.client.model.CompositeModel.class,
                (com.google.gson.JsonDeserializer<
                        net.neoforged.neoforge.client.model.CompositeModel>)
                    (json, type, context) ->
                        net.neoforged.neoforge.client.model.CompositeModel.Loader.INSTANCE.read(
                            json.getAsJsonObject(), context))
            .create();
    var geometry = gson.fromJson(source, net.neoforged.neoforge.client.model.CompositeModel.class);
    var context =
        StandaloneGeometryBakingContext.builder()
            .build(
                ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "composite_fixture"));
    geometry.resolveParents(
        location -> {
          try (var input =
              VanillaEquipmentModelTest.class.getResourceAsStream(
                  "/assets/"
                      + location.getNamespace()
                      + "/models/"
                      + location.getPath()
                      + ".json")) {
            if (input == null)
              throw new IOException("Missing native composite parent: " + location);
            return BlockModel.fromString(
                new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
          } catch (IOException failure) {
            throw new com.google.gson.JsonParseException(
                "Cannot resolve native composite parent", failure);
          }
        },
        context);
    // Use the same native cuboid baker without ModelBakery's live-client marker initialization.
    for (BlockModel child : nativeChildren)
      child.customData.setCustomGeometry(
          new net.neoforged.neoforge.client.model.ElementsModel(child.getElements()));
    Map<ResourceLocation, Sprite> sprites = new HashMap<>();
    java.lang.reflect.Field renderTypes;
    Object previousRenderTypes;
    try {
      Class<?> manager = net.neoforged.neoforge.client.NamedRenderTypeManager.class;
      renderTypes = manager.getDeclaredField("RENDER_TYPES");
      renderTypes.setAccessible(true);
      previousRenderTypes = renderTypes.get(null);
      var register = manager.getDeclaredMethod("preRegisterVanillaRenderTypes", Map.class);
      register.setAccessible(true);
      Map<ResourceLocation, net.neoforged.neoforge.client.RenderTypeGroup> vanilla =
          new HashMap<>();
      register.invoke(null, vanilla);
      renderTypes.set(null, com.google.common.collect.ImmutableMap.copyOf(vanilla));
    } catch (ReflectiveOperationException failure) {
      throw new IOException(
          "Cannot initialize the pinned native render types for offline baking", failure);
    }
    try {
      BakedModel baked =
          geometry.bake(
              context,
              new NoChildModels(),
              material -> sprites.computeIfAbsent(material.texture(), Sprite::new),
              BlockModelRotation.X0_Y0,
              ItemOverrides.EMPTY);
      if (source.getAsJsonObject("children").has("glass")) {
        for (boolean fabulous : new boolean[] {false, true}) {
          var itemPasses =
              baked.getRenderPasses(net.minecraft.world.item.ItemStack.EMPTY, fabulous);
          require(itemPasses.size() == 2, "Bottle renders opaque contents before its glass shell");
          for (int index = 0; index < itemPasses.size(); index++) {
            var pass = itemPasses.get(index);
            var expected =
                index == 0
                    ? net.neoforged.neoforge.client.NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT.get()
                    : net.neoforged.neoforge.client.NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT
                        .get();
            require(
                pass.getRenderTypes(net.minecraft.world.item.ItemStack.EMPTY, fabulous)
                    .equals(List.of(expected)),
                "Native item pass selects the actual cutout/translucent renderer");
            var quads = pass.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null);
            require(!quads.isEmpty(), "Both contents and closed glass shell are visible");
            for (BakedQuad quad : quads)
              for (int vertex = 0; vertex < 4; vertex++) {
                int alpha = quad.getVertices()[vertex * 8 + 3] >>> 24;
                require(
                    index == 0 ? alpha == 255 : alpha > 0 && alpha < 255,
                    "Actual baked contents are opaque and every glass vertex has fractional alpha");
              }
          }
        }
      }
      List<Face> faces = new ArrayList<>();
      int passes = 0;
      for (BakedModel pass :
          baked.getRenderPasses(net.minecraft.world.item.ItemStack.EMPTY, false)) {
        passes++;
        for (BakedQuad quad :
            pass.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null))
          faces.add(face(quad, quad.getSprite().contents().name().toString()));
      }
      require(passes == 2, "The item renderer receives both authored composite passes");
      return faces;
    } finally {
      sprites.values().forEach(Sprite::close);
      try {
        renderTypes.set(null, previousRenderTypes);
      } catch (IllegalAccessException failure) {
        throw new IOException("Cannot restore the native render type fixture", failure);
      }
    }
  }

  @SuppressWarnings(
      "deprecation") // The unchanged horn is a native cuboid model, not a custom loader.
  static List<Face> bakeCuboids(JsonObject source) {
    BlockModel model = BlockModel.fromString(source.toString());
    List<Face> result = new ArrayList<>();
    FaceBakery baker = new FaceBakery();
    try (Sprite sprite = new Sprite(ResourceLocation.withDefaultNamespace("test"))) {
      for (var part : model.getElements())
        for (var entry : part.faces.entrySet())
          result.add(
              face(
                  baker.bakeQuad(
                      part.from,
                      part.to,
                      entry.getValue(),
                      sprite,
                      entry.getKey(),
                      BlockModelRotation.X0_Y0,
                      part.rotation,
                      part.shade),
                  entry.getValue().texture()));
    }
    return result;
  }

  static Face face(BakedQuad quad, String texture) {
    int[] data = quad.getVertices();
    require(
        data.length == 32, "The pinned baker emits four vertices at its native eight-int stride");
    List<Vector3f> vertices = new ArrayList<>();
    JsonArray uv = new JsonArray();
    for (int vertex = 0; vertex < 4; vertex++) {
      int offset = vertex * 8;
      vertices.add(
          new Vector3f(
              Float.intBitsToFloat(data[offset]),
              Float.intBitsToFloat(data[offset + 1]),
              Float.intBitsToFloat(data[offset + 2])));
      uv.add(
          vector(
              (Float.intBitsToFloat(data[offset + 4]) - quad.getSprite().getU0())
                  * 16
                  / (quad.getSprite().getU1() - quad.getSprite().getU0()),
              (Float.intBitsToFloat(data[offset + 5]) - quad.getSprite().getV0())
                  * 16
                  / (quad.getSprite().getV1() - quad.getSprite().getV0())));
    }
    Vector3f normal =
        new Vector3f(vertices.get(1))
            .sub(vertices.getFirst())
            .cross(new Vector3f(vertices.get(2)).sub(vertices.getFirst()))
            .normalize();
    return new Face(texture, vertices, normal, uv, quad.getTintIndex(), data[3] >>> 24, data[6]);
  }

  private static JsonArray vector(float... values) {
    JsonArray result = new JsonArray();
    for (float value : values) result.add(value);
    return result;
  }

  static void noOverlaps(String id, List<Face> faces) {
    for (int first = 0; first < faces.size(); first++)
      for (int second = first + 1; second < faces.size(); second++) {
        Face a = faces.get(first), b = faces.get(second);
        if (a.normal.dot(b.normal) < .99999f) continue;
        boolean coplanar = true;
        for (Vector3f point : b.vertices)
          coplanar &=
              Math.abs(new Vector3f(point).sub(a.vertices.getFirst()).dot(a.normal)) < .000001f;
        if (!coplanar) continue;
        int perpendicular = Math.abs(a.normal.x) > Math.abs(a.normal.y) ? 0 : 1;
        if (Math.abs(a.normal.z) > Math.abs(a.normal.get(perpendicular))) perpendicular = 2;
        List<double[]> subject = project(a, perpendicular), clip = project(b, perpendicular);
        double orientation = Math.signum(area(clip));
        for (int edge = 0; edge < clip.size() && !subject.isEmpty(); edge++) {
          double[] start = clip.get(edge), end = clip.get((edge + 1) % clip.size());
          if (Math.hypot(start[0] - end[0], start[1] - end[1]) < .0000001) continue;
          List<double[]> result = new ArrayList<>();
          double[] previous = subject.getLast();
          double before = cross(start, end, previous) * orientation;
          for (double[] point : subject) {
            double after = cross(start, end, point) * orientation;
            if ((before >= 0) != (after >= 0)) {
              double t = before / (before - after);
              result.add(
                  new double[] {
                    previous[0] + (point[0] - previous[0]) * t,
                    previous[1] + (point[1] - previous[1]) * t
                  });
            }
            if (after >= 0) result.add(point);
            previous = point;
            before = after;
          }
          subject = result;
        }
        require(
            Math.abs(area(subject)) < .00000001,
            "Baked surfaces cannot compete at the same depth: "
                + id
                + " faces "
                + first
                + "/"
                + second);
      }
  }

  private static List<double[]> project(Face face, int perpendicular) {
    List<double[]> points = new ArrayList<>();
    for (Vector3f point : face.vertices)
      points.add(
          new double[] {point.get((perpendicular + 1) % 3), point.get((perpendicular + 2) % 3)});
    return points;
  }

  static void closedSurface(String id, List<Face> faces) {
    Map<String, int[]> edges = new HashMap<>();
    for (Face face : faces)
      for (int[] triangle : List.of(new int[] {0, 1, 2}, new int[] {0, 2, 3})) {
        Vector3f a = face.vertices.get(triangle[0]),
            b = face.vertices.get(triangle[1]),
            c = face.vertices.get(triangle[2]);
        if (new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).lengthSquared() == 0) continue;
        for (int point = 0; point < 3; point++) {
          String from = positionKey(face.vertices.get(triangle[point])),
              to = positionKey(face.vertices.get(triangle[(point + 1) % 3]));
          int direction = from.compareTo(to) < 0 ? 1 : -1;
          String key = direction == 1 ? from + "/" + to : to + "/" + from;
          int[] value = edges.computeIfAbsent(key, unused -> new int[2]);
          value[0]++;
          value[1] += direction;
        }
      }
    for (var entry : edges.entrySet())
      require(
          entry.getValue()[0] == 2 && entry.getValue()[1] == 0,
          "A closed consistently wound surface without folded triangles: "
              + id
              + " "
              + entry.getKey());
  }

  private static String positionKey(Vector3f point) {
    return Integer.toHexString(Float.floatToIntBits(point.x))
        + ":"
        + Integer.toHexString(Float.floatToIntBits(point.y))
        + ":"
        + Integer.toHexString(Float.floatToIntBits(point.z));
  }

  private static double cross(double[] a, double[] b, double[] c) {
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  }

  private static double area(List<double[]> points) {
    double total = 0;
    for (int index = 0; index < points.size(); index++) {
      double[] a = points.get(index), b = points.get((index + 1) % points.size());
      total += a[0] * b[1] - b[0] * a[1];
    }
    return total / 2;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  record Face(
      String texture,
      List<Vector3f> vertices,
      Vector3f normal,
      JsonArray uv,
      int tint,
      int alpha,
      int light) {}

  static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
    Sprite(ResourceLocation name) {
      super(
          name,
          new SpriteContents(
              name, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY),
          512,
          512,
          32,
          48);
    }

    @Override
    public void close() {
      contents().close();
    }
  }

  static final class NoChildModels implements ModelBaker {
    @Override
    public UnbakedModel getModel(ResourceLocation location) {
      throw new AssertionError(location);
    }

    @Override
    public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
      throw new AssertionError(location);
    }

    @Override
    @SuppressWarnings(
        "deprecation") // Required legacy interface method; any call fails the fixture.
    public BakedModel bake(ResourceLocation location, ModelState state) {
      throw new AssertionError(location);
    }

    @Override
    public BakedModel bake(
        ResourceLocation location,
        ModelState state,
        Function<Material, TextureAtlasSprite> sprites) {
      throw new AssertionError(location);
    }

    @Override
    public BakedModel bakeUncached(
        UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> sprites) {
      throw new AssertionError("Unexpected child model");
    }

    @Override
    public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
      throw new AssertionError("Unexpected child textures");
    }
  }
}
