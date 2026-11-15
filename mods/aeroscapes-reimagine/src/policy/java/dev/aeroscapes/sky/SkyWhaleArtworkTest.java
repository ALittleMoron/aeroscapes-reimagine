package dev.aeroscapes.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Verifies explicit biome surfaces, native combat attachment points and swept geometry bounds. */
public final class SkyWhaleArtworkTest {
  private static final Set<String> ROLES = Set.of("skin", "coat", "edge", "growth");

  /**
   * Crafted or processed blocks that never form natural terrain; a whale built from them reads as
   * masonry rather than a creature of its biome. Smooth basalt forms naturally around geodes and
   * badlands strata contain only the uncoloured, white, orange, yellow, brown, red and light grey
   * terracotta.
   */
  private static final Pattern PROCESSED =
      Pattern.compile(
          "block/(smooth_(?!basalt)|polished_|cut_|chiseled_|stripped_|packed_mud|dark_prismarine"
              + "|bricks|purpur|(cyan|blue|light_blue|green|lime|black|gray|purple|magenta|pink)"
              + "_terracotta|(?!white_chalk)[a-z_]+_chalk)"
              + "|_bricks|_tiles|_wool|concrete|_planks|glazed|glass|quartz(?!_ore)"
              + "|(iron|gold|copper|diamond|emerald|lapis|redstone|netherite|coal)(?!_ore)");

  /** The hollow-log interior repeats Quark's hollow log, which its fallen logs generate. */
  private static final Set<String> NATURAL_FEATURE_TEXTURES =
      Set.of("minecraft:block/stripped_oak_log");

  private SkyWhaleArtworkTest() {}

  public static void main(String[] arguments) throws IOException {
    samplerContract();
    surfaceOverlapControls();
    require(
        PROCESSED.matcher("minecraft:block/smooth_stone").find()
            && PROCESSED.matcher("minecraft:block/bricks").find()
            && PROCESSED.matcher("minecraft:block/gray_terracotta").find()
            && PROCESSED.matcher("natures_spirit:block/gray_chalk").find()
            && PROCESSED.matcher("minecraft:block/raw_iron_block").find()
            && !PROCESSED.matcher("minecraft:block/smooth_basalt").find()
            && !PROCESSED.matcher("minecraft:block/light_gray_terracotta").find()
            && !PROCESSED.matcher("natures_spirit:block/white_chalk").find()
            && !PROCESSED.matcher("minecraft:block/deepslate_iron_ore").find()
            && !PROCESSED.matcher("minecraft:block/calcite").find(),
        "Processed-material control separates crafted from natural blocks");
    var original = SkyWhaleArtwork.nativeArt();
    String unchanged = original.geometry().toString();
    JsonObject layouts = resource("/assets/aeroscapes_reimagine/whales/material_layout.json");
    Set<String> varieties =
        java.util.Arrays.stream(WhaleVariety.values())
            .map(WhaleVariety::id)
            .collect(java.util.stream.Collectors.toSet());
    require(
        layouts.keySet().equals(varieties),
        "Every synchronized variety has one explicit material layout");
    for (String variety : SkyWhaleArtwork.VARIANTS) {
      var generated = SkyWhaleArtwork.art(original.geometry(), variety);
      JsonObject stored =
          resource("/assets/aeroscapes_reimagine/geo/whales/" + variety + ".geo.json");
      JsonObject layout = layouts.getAsJsonObject(variety);
      require(stored.equals(generated.geometry()), variety + " geometry is reproducible");
      require(
          layout.equals(generated.layout()), variety + " layout and swept bounds are reproducible");
      combatRig(model(original.geometry()), model(stored), variety);
      atlasAndMaterials(model(stored), layout, variety);
      for (String id : WhaleTextureComposer.requiredTextureIds(layout))
        require(
            NATURAL_FEATURE_TEXTURES.contains(id) || !PROCESSED.matcher(id).find(),
            variety + " uses only natural blocks, not " + id);
      sweptBounds(model(stored), layout, variety);
      exposedGeology(model(stored), variety);
      if (variety.equals("dripstone")) stalactiteSupports(model(stored), layout);
      Map<String, WhaleTextureComposer.Pixels> fixtures = new HashMap<>();
      for (String id : WhaleTextureComposer.requiredTextureIds(layout)) {
        int[] pixels = new int[256];
        java.util.Arrays.fill(pixels, 0xffa1b2c3);
        fixtures.put(id, new WhaleTextureComposer.Pixels(16, 16, pixels));
      }
      var painted = WhaleTextureComposer.compose(variety, stored, layout, fixtures);
      for (var value : layout.getAsJsonArray("eyes")) {
        JsonObject eye = value.getAsJsonObject();
        JsonObject cube =
            bones(model(stored))
                .get(eye.get("bone").getAsString())
                .getAsJsonArray("cubes")
                .get(eye.get("cube").getAsInt())
                .getAsJsonObject();
        int[] uv = WhaleTextureComposer.face(cube, eye.get("face").getAsString());
        int x = uv[0] + eye.get("u").getAsInt() + (eye.get("mirror").getAsBoolean() ? 4 : 3);
        int y = uv[1] + eye.get("v").getAsInt() + 2;
        require(
            painted.get(x, y) == 0xffe5eddf,
            variety + " authored eye follows its own head surface");
        require(
            painted.get(uv[0], uv[1]) == 0xffa1b2c3,
            variety + " eye surround retains installed block pixels");
      }
    }
    require(
        original.geometry().toString().equals(unchanged), "Pinned provider data remains unchanged");
    System.out.println(
        "Thirteen reproducible biome anatomies, complete surfaces, native combat rig, alpha sprites, eye anchors and hierarchical swept bounds passed");
  }

  private static void stalactiteSupports(JsonObject model, JsonObject layout) {
    Map<String, JsonObject> bones = bones(model);
    int attached = 0;
    for (var value : layout.getAsJsonArray("decorations")) {
      JsonObject root = value.getAsJsonObject();
      if (!root.has("attachment")) continue;
      JsonArray cubes = bones.get(root.get("bone").getAsString()).getAsJsonArray("cubes");
      Box anchor = box(cubes.get(root.get("cube").getAsInt()).getAsJsonObject());
      boolean supported = false;
      for (var candidateValue : layout.getAsJsonArray("decorations")) {
        JsonObject candidate = candidateValue.getAsJsonObject();
        if (!candidate.get("bone").equals(root.get("bone")) || !candidate.has("role")) continue;
        Box host = box(cubes.get(candidate.get("cube").getAsInt()).getAsJsonObject());
        boolean footprint =
            Math.min(anchor.high()[0], host.high()[0]) > Math.max(anchor.low()[0], host.low()[0])
                && Math.min(anchor.high()[2], host.high()[2])
                    > Math.max(anchor.low()[2], host.low()[2]);
        supported |=
            footprint
                && (Math.min(anchor.high()[1], host.high()[1])
                        > Math.max(anchor.low()[1], host.low()[1])
                    || anchor.high()[1] == host.low()[1]);
      }
      require(supported, "Every stalactite joins anatomy on the same animated bone: " + root);
      attached++;
    }
    require(attached > 0, "Dripstone formations have checked physical attachments");
  }

  private static void exposedGeology(JsonObject model, String variety) {
    var resting = new ArrayList<Box>();
    for (var bone : bones(model).values())
      for (var value : bone.getAsJsonArray("cubes")) {
        JsonObject cube = value.getAsJsonObject();
        if (!cube.has("rotation")) resting.add(box(cube));
      }
    require(
        !exposedOverlap(resting),
        variety + " has no coincident exposed faces between bones at rest");
    for (var entry : bones(model).entrySet()) {
      String name = entry.getKey();
      var boxes = new ArrayList<Box>();
      for (var value : entry.getValue().getAsJsonArray("cubes")) {
        JsonObject cube = value.getAsJsonObject();
        if (!cube.has("rotation")) boxes.add(box(cube));
      }
      require(
          !exposedOverlap(boxes), variety + " has no coincident exposed geology faces: " + name);
    }
  }

  private static void surfaceOverlapControls() {
    Box first = new Box(new double[] {0, 0, 0}, new double[] {4, 4, 4});
    require(exposedOverlap(List.of(first, first)), "Duplicate exposed faces reproduce z-fighting");
    require(
        !exposedOverlap(List.of(first, new Box(new double[] {4, 0, 0}, new double[] {8, 4, 4}))),
        "Opposite-facing touching seams are valid");
    require(
        !exposedOverlap(
            List.of(first, first, new Box(new double[] {-1, -1, -1}, new double[] {5, 5, 5}))),
        "Fully buried faces are not exposed overlaps");
  }

  private static boolean exposedOverlap(List<Box> boxes) {
    for (int first = 0; first < boxes.size(); first++)
      for (int second = first + 1; second < boxes.size(); second++)
        for (int axis = 0; axis < 3; axis++)
          for (int upper = 0; upper < 2; upper++) {
            Box one = boxes.get(first), two = boxes.get(second);
            double plane = (upper == 1 ? one.high() : one.low())[axis];
            if (plane != (upper == 1 ? two.high() : two.low())[axis]) continue;
            int horizontal = (axis + 1) % 3, vertical = (axis + 2) % 3;
            double[] columns = faceCuts(boxes, one, two, horizontal);
            double[] rows = faceCuts(boxes, one, two, vertical);
            for (int column = 1; column < columns.length; column++)
              for (int row = 1; row < rows.length; row++) {
                double[] point = new double[3];
                point[axis] = plane + (upper == 1 ? 0.00001 : -0.00001);
                point[horizontal] = (columns[column - 1] + columns[column]) / 2;
                point[vertical] = (rows[row - 1] + rows[row]) / 2;
                boolean covered = false;
                for (Box box : boxes)
                  covered |=
                      point[0] > box.low()[0]
                          && point[0] < box.high()[0]
                          && point[1] > box.low()[1]
                          && point[1] < box.high()[1]
                          && point[2] > box.low()[2]
                          && point[2] < box.high()[2];
                if (!covered) return true;
              }
          }
    return false;
  }

  private static double[] faceCuts(List<Box> boxes, Box one, Box two, int axis) {
    double low = Math.max(one.low()[axis], two.low()[axis]);
    double high = Math.min(one.high()[axis], two.high()[axis]);
    if (low >= high) return new double[0];
    var cuts = new java.util.TreeSet<Double>();
    cuts.add(low);
    cuts.add(high);
    for (Box box : boxes) {
      if (box.low()[axis] > low && box.low()[axis] < high) cuts.add(box.low()[axis]);
      if (box.high()[axis] > low && box.high()[axis] < high) cuts.add(box.high()[axis]);
    }
    return cuts.stream().mapToDouble(Double::doubleValue).toArray();
  }

  private static Box box(JsonObject cube) {
    double[] low = vector(cube.getAsJsonArray("origin"));
    double[] size = vector(cube.getAsJsonArray("size"));
    return new Box(low, new double[] {low[0] + size[0], low[1] + size[1], low[2] + size[2]});
  }

  private record Box(double[] low, double[] high) {}

  private static void combatRig(JsonObject nativeModel, JsonObject model, String variety) {
    Map<String, JsonObject> generated = bones(model);
    for (var value : nativeModel.getAsJsonArray("bones")) {
      JsonObject source = value.getAsJsonObject();
      String name = source.get("name").getAsString();
      if (name.equals("bone")) continue;
      require(generated.containsKey(name), variety + " preserves native combat bone " + name);
      for (String property : Set.of("parent", "pivot"))
        require(
            java.util.Objects.equals(source.get(property), generated.get(name).get(property)),
            variety + " preserves native combat " + name + ":" + property);
    }
    for (String name :
        Set.of(
            "flipper_tip_left",
            "flipper_tip_right",
            "flora_left",
            "flora_right",
            "eyelid_left",
            "eyelid_right"))
      require(generated.containsKey(name), variety + " supplies owned motion bone " + name);
    JsonObject description = model.getAsJsonObject("description");
    require(
        description.get("texture_width").getAsInt() == 1024
            && description.get("texture_height").getAsInt() == 1024,
        variety + " uses the owned atlas dimensions");
  }

  private static void atlasAndMaterials(JsonObject model, JsonObject layout, String variety) {
    require(layout.get("schema").getAsInt() == 2, variety + " uses explicit surface schema");
    require(
        layout.getAsJsonArray("slots").size() >= 6,
        variety + " retains the original material slots");
    Map<String, JsonObject> surfaces = new HashMap<>();
    for (var value : layout.getAsJsonArray("decorations")) {
      JsonObject surface = value.getAsJsonObject();
      String key = surface.get("bone").getAsString() + ":" + surface.get("cube").getAsInt();
      require(surfaces.put(key, surface) == null, variety + " has no duplicate cube material");
      material(surface, layout, variety);
    }
    boolean[] occupied = new boolean[1024 * 1024];
    int cubeCount = 0;
    for (JsonObject bone : bones(model).values()) {
      JsonArray cubes = bone.getAsJsonArray("cubes");
      for (int index = 0; index < cubes.size(); index++) {
        cubeCount++;
        JsonObject cube = cubes.get(index).getAsJsonObject();
        String key = bone.get("name").getAsString() + ":" + index;
        require(surfaces.containsKey(key), variety + " assigns every full-body cube a material");
        JsonObject surface = surfaces.get(key);
        double[] size = vector(cube.getAsJsonArray("size"));
        boolean sprite =
            surface.has("sampling") && surface.get("sampling").getAsString().equals("sprite");
        require(
            size[1] > 0 && size[0] >= 0 && size[2] >= 0, variety + " has valid cube dimensions");
        require(
            sprite ? (size[0] == 0) != (size[2] == 0) : size[0] > 0 && size[2] > 0,
            variety + " only alpha sprites have zero-depth geometry");
        require(cube.get("uv").isJsonObject(), variety + " owns every cube face UV");
        JsonObject faces = cube.getAsJsonObject("uv");
        require(
            faces.size() == (sprite ? 2 : 6), variety + " maps every visible solid or sprite face");
        if (surface.has("faces"))
          for (var override : surface.getAsJsonObject("faces").entrySet()) {
            require(faces.has(override.getKey()), variety + " overrides an existing face");
            material(override.getValue().getAsJsonObject(), layout, variety);
          }
        for (var entry : faces.entrySet()) {
          int[] uv = WhaleTextureComposer.face(cube, entry.getKey());
          require(
              uv[0] >= 0
                  && uv[1] >= 0
                  && uv[2] > 0
                  && uv[3] > 0
                  && uv[0] + uv[2] <= 1024
                  && uv[1] + uv[3] <= 1024,
              variety + " packs faces inside the full owned atlas");
          for (int row = uv[1]; row < uv[1] + uv[3]; row++)
            for (int column = uv[0]; column < uv[0] + uv[2]; column++) {
              int pixel = row * 1024 + column;
              if (occupied[pixel]) throw new AssertionError(variety + " face UVs overlap");
              occupied[pixel] = true;
            }
        }
      }
    }
    require(cubeCount == surfaces.size(), variety + " has no orphan material entries");
  }

  private static void material(JsonObject reference, JsonObject layout, String variety) {
    require(
        reference.has("role") != reference.has("slot"), variety + " uses exactly one role or slot");
    if (reference.has("role"))
      require(
          ROLES.contains(reference.get("role").getAsString()),
          variety + " references a known role");
    else
      require(
          reference.get("slot").getAsInt() >= 0
              && reference.get("slot").getAsInt() < layout.getAsJsonArray("slots").size(),
          variety + " references an existing material slot");
  }

  private static void sweptBounds(JsonObject model, JsonObject layout, String variety) {
    Map<String, JsonObject> bones = bones(model);
    JsonArray regions = layout.getAsJsonArray("spawn_clearance");
    JsonObject whole = layout.getAsJsonObject("model_bounds");
    int region = 0;
    double[] unionMin = {
      Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
    };
    double[] unionMax = {
      Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
    };
    for (JsonObject bone : bones.values()) {
      if (bone.getAsJsonArray("cubes").isEmpty()) continue;
      require(region < regions.size(), variety + " bounds every nonempty bone");
      JsonObject bounds = regions.get(region++).getAsJsonObject();
      double[] minimum = vector(bounds.getAsJsonArray("min")),
          maximum = vector(bounds.getAsJsonArray("max"));
      for (int axis = 0; axis < 3; axis++) {
        require(minimum[axis] <= maximum[axis], variety + " has ordered swept bounds");
        unionMin[axis] = Math.min(unionMin[axis], minimum[axis]);
        unionMax[axis] = Math.max(unionMax[axis], maximum[axis]);
      }
    }
    require(region == regions.size(), variety + " has no unrelated clearance regions");
    require(
        java.util.Arrays.equals(unionMin, vector(whole.getAsJsonArray("min")))
            && java.util.Arrays.equals(unionMax, vector(whole.getAsJsonArray("max"))),
        variety + " model bounds are the complete per-bone union");
  }

  private static void samplerContract() {
    JsonObject geometry =
        JsonParser.parseString(
                """
      {"minecraft:geometry":[{"bones":[{"name":"body","cubes":[{"origin":[3,5,7],"size":[20,20,20],"uv":[32,400]}]}]}]}
      """)
            .getAsJsonObject();
    JsonObject layout =
        JsonParser.parseString(
                """
      {"schema":2,"variety":"fixture","skin":{"side":"fixture:side","top":"fixture:top","bottom":"fixture:bottom"},"slots":[],"eyes":[],"decorations":[{"bone":"body","cube":0,"role":"skin"}]}
      """)
            .getAsJsonObject();
    for (String role : Set.of("coat", "edge", "growth"))
      layout.add(role, layout.get("skin").deepCopy());
    var sources =
        Map.of("fixture:side", ramp(0x55), "fixture:top", ramp(0x99), "fixture:bottom", ramp(0xaa));
    var image = WhaleTextureComposer.compose("fixture", geometry, layout, sources);
    require(image.get(52, 420) == 0xff030755, "North uses world cube-origin phase");
    require(image.get(32, 420) == 0xff0a0755, "East reverses provider depth");
    require(image.get(72, 420) == 0xff070755, "West uses forward provider depth");
    require(image.get(92, 420) == 0xff060755, "South reverses provider X");
    require(image.get(52, 400) == 0xff030a99, "Top uses X/Z and top material");
    require(image.get(72, 400) == 0xff030aaa, "Down reverses native V");
    require(
        image.get(68, 420) == image.get(52, 420),
        "Block pixels repeat every sixteen geometry units");
    require(
        WhaleTextureComposer.sample(ramp(0x55), 0, 32) == 0xff000055,
        "Animated textures use their first square frame");
    JsonObject surface = layout.getAsJsonArray("decorations").get(0).getAsJsonObject();
    JsonObject overrides = new JsonObject();
    JsonObject ore = new JsonObject();
    ore.addProperty("role", "coat");
    overrides.add("north", ore);
    surface.add("faces", overrides);
    layout.getAsJsonObject("coat").addProperty("side", "fixture:bottom");
    image = WhaleTextureComposer.compose("fixture", geometry, layout, sources);
    require(image.get(52, 420) == 0xff0307aa, "A flush ore inlay overrides only its selected face");
    require(image.get(32, 420) == 0xff0a0755, "Other faces keep their original material");
    surface.remove("faces");
    JsonObject sprite =
        JsonParser.parseString(
                """
      {"minecraft:geometry":[{"bones":[{"name":"body","cubes":[{"origin":[0,0,0],"size":[32,32,0],"uv":{"north":{"uv":[64,64],"uv_size":[32,32]},"south":{"uv":[100,64],"uv_size":[32,32]}}}]}]}]}
      """)
            .getAsJsonObject();
    layout.getAsJsonArray("decorations").get(0).getAsJsonObject().addProperty("sampling", "sprite");
    int[] alpha = ramp(0x55).argb().clone();
    alpha[0] = 0;
    image =
        WhaleTextureComposer.compose(
            "fixture",
            sprite,
            layout,
            Map.of(
                "fixture:side",
                new WhaleTextureComposer.Pixels(16, 32, alpha),
                "fixture:top",
                ramp(0x99),
                "fixture:bottom",
                ramp(0xaa)));
    require(image.get(64, 64) == 0, "Cross sprites retain native transparent cutouts");
    require(
        image.get(95, 95) == 0xff0f0f55, "Cross sprites stretch one complete native first frame");
  }

  private static WhaleTextureComposer.Pixels ramp(int blue) {
    int[] values = new int[16 * 32];
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 16; x++)
        values[y * 16 + x] = y < 16 ? 0xff000000 | (x << 16) | (y << 8) | blue : 0xffff00ff;
    return new WhaleTextureComposer.Pixels(16, 32, values);
  }

  private static Map<String, JsonObject> bones(JsonObject model) {
    Map<String, JsonObject> result = new LinkedHashMap<>();
    for (var value : model.getAsJsonArray("bones")) {
      JsonObject bone = value.getAsJsonObject();
      result.put(bone.get("name").getAsString(), bone);
    }
    return result;
  }

  private static JsonObject model(JsonObject root) {
    return root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
  }

  private static double[] vector(JsonArray values) {
    return new double[] {
      values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()
    };
  }

  private static JsonObject resource(String path) throws IOException {
    try (var input = SkyWhaleArtworkTest.class.getResourceAsStream(path)) {
      if (input == null) throw new IOException("Missing owned whale resource " + path);
      return JsonParser.parseString(
              new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
