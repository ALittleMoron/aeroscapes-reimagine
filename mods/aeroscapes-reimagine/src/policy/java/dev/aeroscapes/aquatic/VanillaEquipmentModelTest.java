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
      if (id.equals("minecraft:lead")) closedRope(faces);
      if (List.of(
              "minecraft:clock",
              "minecraft:compass",
              "minecraft:recovery_compass",
              "minecraft:goat_horn",
              "minecraft:lead")
          .contains(id)) {
        JsonObject preview = new JsonObject();
        preview.add("textures", model.get("textures").deepCopy());
        preview.add("display", model.get("display").deepCopy());
        JsonArray quads = new JsonArray();
        for (Face face : faces) {
          JsonObject quad = new JsonObject();
          quad.addProperty("texture", face.texture);
          JsonArray vertices = new JsonArray();
          for (Vector3f vertex : face.vertices)
            vertices.add(vector(vertex.x * 16, vertex.y * 16, vertex.z * 16));
          quad.add("vertices", vertices);
          quad.add("uv", face.uv);
          quad.add("normal", vector(face.normal.x, face.normal.y, face.normal.z));
          quads.add(quad);
        }
        preview.add("quads", quads);
        previews.add(id, preview);
      }
      tested++;
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Vanilla equipment: "
            + tested
            + " native-parsed/baked states, atlas UVs, physical pointers and day/night phase passed");
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
    ResourceLocation location = ResourceLocation.parse(source.get("model").getAsString());
    Path obj = assets.resolve(location.getNamespace()).resolve(location.getPath());
    var settings = new ObjModel.ModelSettings(location, false, true, false, false, null);
    ObjModel parsed;
    try (var material = Files.newInputStream(obj.resolveSibling("equipment.mtl"))) {
      parsed = EquipmentObjFixture.parse(Files.readString(obj), material, settings);
    }
    var contextBuilder = StandaloneGeometryBakingContext.builder().withUseAmbientOcclusion(false);
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
              material -> sprites.computeIfAbsent(material.texture(), Sprite::new),
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

  private static Face face(BakedQuad quad, String texture) {
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
              Float.intBitsToFloat(data[offset + 4]) * 512,
              Float.intBitsToFloat(data[offset + 5]) * 512));
    }
    Vector3f normal =
        new Vector3f(vertices.get(1))
            .sub(vertices.getFirst())
            .cross(new Vector3f(vertices.get(2)).sub(vertices.getFirst()))
            .normalize();
    return new Face(texture, vertices, normal, uv);
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

  private static void closedRope(List<Face> faces) {
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
          "The rope has a closed consistently wound surface without folded triangles: "
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

  record Face(String texture, List<Vector3f> vertices, Vector3f normal, JsonArray uv) {}

  private static final class Sprite extends TextureAtlasSprite implements AutoCloseable {
    Sprite(ResourceLocation name) {
      super(
          name,
          new SpriteContents(
              name, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY),
          512,
          512,
          0,
          0);
    }

    @Override
    public void close() {
      contents().close();
    }
  }

  private static final class NoChildModels implements ModelBaker {
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
