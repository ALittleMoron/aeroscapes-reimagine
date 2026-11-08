package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.construction.ConstructionKeys;
import dev.aeroscapes.construction.ConstructionMessages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/** Actual native preview parsing and hand geometry, without starting a Minecraft client. */
public final class ConstructionEquipmentModelTest {
  private static final List<String> ITEMS =
      List.of("hammer", "chisel", "saw", "wrench", "paint_brush", "glove", "measure_tape", "bag");

  private ConstructionEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve(
                "pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets/littletiles/models/item");
    JsonObject previews = new JsonObject();
    for (String item : ITEMS) {
      JsonObject root = read(assets.resolve(item + ".json"));
      JsonObject nativeModel = HeldItemModels.nativeModel("littletiles:" + item);
      boolean layered = ConstructionIconModels.ITEMS.contains(item);
      JsonObject routing = layered ? root.getAsJsonArray("layers").get(0).getAsJsonObject() : root;
      JsonObject base;
      if (layered) {
        base = root.deepCopy();
        JsonObject sprite = routing.getAsJsonObject("base").deepCopy();
        base.getAsJsonArray("layers").set(0, sprite);
      } else base = root.getAsJsonObject("base").deepCopy();
      require(
          base.equals(nativeModel), "Native non-hand presentation and preview retained: " + item);
      require(
          routing.getAsJsonObject("perspectives").size() == 4, "Only four hand contexts: " + item);
      for (String context :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand"))
        require(
            routing.getAsJsonObject("perspectives").has(context),
            "Both cameras and hands: " + item);
      JsonObject held = read(assets.resolve("equipment/held/" + item + ".json"));
      var faces =
          held.has("loader")
              ? VanillaEquipmentModelTest.bakeObj(held, assets.getParent().getParent().getParent())
              : ConstructionIconRenderingTest.heldFaces(
                  assets.getParent().getParent().getParent(), item);
      require(faces.size() >= 12, "Useful authored volume bakes into actual faces: " + item);
      VanillaEquipmentModelTest.noOverlaps("littletiles:" + item, faces);
      if (ConstructionEquipmentModels.ITEMS.contains(item))
        AquaticToolModelTest.opaque(
            assets.getParent().getParent().getParent(), "littletiles:" + item, faces);
      previews.add("littletiles:" + item, VanillaEquipmentModelTest.preview(held, faces));
    }
    brushMaterials(assets);
    wrenchMetal(assets);
    tapeBlade(assets);
    ConstructionIconRenderingTest.verify(assets.getParent().getParent().getParent());
    commandMessages();
    keyDefaults();
    ConstructionMixinContract.verify();
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Construction equipment: eight native roots and held meshes; GUI preview dispatch and paint tint passed");
  }

  private static void brushMaterials(Path models) throws IOException {
    Path assets = models.getParent().getParent().getParent();
    JsonObject brush = read(models.resolve("equipment/held/paint_brush.json"));
    var faces = ConstructionIconRenderingTest.heldFaces(assets, "paint_brush");
    JsonObject bundle = part(brush, "bristles");
    var bristles = faces.stream().filter(face -> inPart(face, bundle)).toList();
    require(!bristles.isEmpty(), "The actual native bake retains the complete bristle bundle");
    require(
        bristles.stream()
            .allMatch(
                face ->
                    face.tint() == 1
                        && face.texture().equals("littletiles:item/paint_brush_overlay")),
        "Every bristle surface uses the native selected-color overlay, not ferrule material");
    double[] lengths = new double[3];
    for (int axis = 0; axis < 3; axis++)
      lengths[axis] =
          bundle.getAsJsonArray("to").get(axis).getAsDouble()
              - bundle.getAsJsonArray("from").get(axis).getAsDouble();
    double expectedArea =
        2 * (lengths[0] * lengths[1] + lengths[0] * lengths[2] + lengths[1] * lengths[2]);
    double tintedArea =
        bristles.stream().mapToDouble(ConstructionEquipmentModelTest::area).sum() * 256;
    require(
        Math.abs(tintedArea - expectedArea) < .0001,
        "Selected paint covers the whole authored bristle surface, not only a tip strip");
    material(
        bristles,
        (int color) -> {
          int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
          return red >= 220
              && green >= 220
              && blue >= 220
              && Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 8;
        },
        "Native pale bristle material stays opaque and separate from the darker ferrule");
    var ferrule = faces.stream().filter(face -> inPart(face, part(brush, "ferrule"))).toList();
    require(
        !ferrule.isEmpty()
            && ferrule.stream()
                .allMatch(
                    face ->
                        face.tint() < 0 && face.texture().equals("littletiles:item/paint_brush")),
        "The ferrule retains its untinted native metal material");
  }

  private static void wrenchMetal(Path models) throws IOException {
    Path assets = models.getParent().getParent().getParent();
    JsonObject wrench = read(models.resolve("equipment/held/wrench.json"));
    var metal =
        ConstructionIconRenderingTest.heldFaces(assets, "wrench").stream()
            .filter(
                face ->
                    List.of("neck", "head_base", "left_jaw", "right_jaw").stream()
                        .anyMatch(name -> inPart(face, part(wrench, name))))
            .toList();
    require(
        !metal.isEmpty() && metal.stream().allMatch(face -> face.tint() < 0),
        "The actual neck/jaws retain untinted metal surfaces");
    material(
        metal,
        (int color) -> {
          int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
          return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 3;
        },
        "Working metal samples neutral native fields instead of the blue handle joint");
  }

  private static void tapeBlade(Path models) throws IOException {
    Path assets = models.getParent().getParent().getParent();
    JsonObject tape = read(models.resolve("equipment/held/measure_tape.json"));
    List<VanillaEquipmentModelTest.Face> yellow = new java.util.ArrayList<>();
    for (var face : VanillaEquipmentModelTest.bakeObj(tape, assets)) {
      var image = NativeEquipmentModels.image(face.texture());
      double u =
          face.uv().asList().stream()
              .mapToDouble(uv -> uv.getAsJsonArray().get(0).getAsDouble())
              .average()
              .orElseThrow();
      double v =
          face.uv().asList().stream()
              .mapToDouble(uv -> uv.getAsJsonArray().get(1).getAsDouble())
              .average()
              .orElseThrow();
      if ((image.getRGB((int) (u * image.getWidth() / 16), (int) (v * image.getHeight() / 16))
              & 0xFFFFFF)
          == 0xFFFF00) yellow.add(face);
    }
    require(!yellow.isEmpty(), "The actual yellow measuring strip is present");
    material(
        yellow,
        (int color) -> (color & 0xFFFFFF) == 0xFFFF00,
        "The strip retains its opaque native yellow material");
    var vertical =
        yellow.stream()
            .flatMap(face -> face.vertices().stream())
            .mapToDouble(point -> point.y)
            .summaryStatistics();
    var width =
        yellow.stream()
            .flatMap(face -> face.vertices().stream())
            .mapToDouble(point -> point.z)
            .summaryStatistics();
    require(
        (vertical.getMax() - vertical.getMin()) * 4 < width.getMax() - width.getMin(),
        "The extended tape is a flat strip with a clear width, not a square rod");
  }

  private static JsonObject part(JsonObject model, String name) {
    return model.getAsJsonArray("elements").asList().stream()
        .map(com.google.gson.JsonElement::getAsJsonObject)
        .filter(element -> element.get("name").getAsString().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static boolean inPart(VanillaEquipmentModelTest.Face face, JsonObject part) {
    for (var vertex : face.vertices())
      for (int axis = 0; axis < 3; axis++)
        if (vertex.get(axis) * 16 < part.getAsJsonArray("from").get(axis).getAsDouble() - .0001
            || vertex.get(axis) * 16 > part.getAsJsonArray("to").get(axis).getAsDouble() + .0001)
          return false;
    return true;
  }

  private static double area(VanillaEquipmentModelTest.Face face) {
    double area = 0;
    for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}})
      area +=
          new org.joml.Vector3f(face.vertices().get(triangle[1]))
                  .sub(face.vertices().get(triangle[0]))
                  .cross(
                      new org.joml.Vector3f(face.vertices().get(triangle[2]))
                          .sub(face.vertices().get(triangle[0])))
                  .length()
              / 2;
    return area;
  }

  private static void material(
      List<VanillaEquipmentModelTest.Face> faces, IntPredicate accepted, String message)
      throws IOException {
    for (var face : faces) {
      var image = NativeEquipmentModels.image(face.texture());
      var horizontal =
          face.uv().asList().stream()
              .mapToDouble(uv -> uv.getAsJsonArray().get(0).getAsDouble() * image.getWidth() / 16)
              .summaryStatistics();
      var vertical =
          face.uv().asList().stream()
              .mapToDouble(uv -> uv.getAsJsonArray().get(1).getAsDouble() * image.getHeight() / 16)
              .summaryStatistics();
      for (int y = (int) vertical.getMin(); y <= (int) vertical.getMax(); y++)
        for (int x = (int) horizontal.getMin(); x <= (int) horizontal.getMax(); x++) {
          int color = image.getRGB(x, y);
          require(color >>> 24 == 255 && accepted.test(color), message);
        }
    }
  }

  private static void keyDefaults() {
    require(ConstructionKeys.defaultKey("key.little.mark", 77) == 59, "Mark uses semicolon");
    require(ConstructionKeys.defaultKey("key.little.mirror", 71) == 39, "Mirror uses apostrophe");
    require(
        ConstructionKeys.defaultKey("key.little.config.item", 67) == 92,
        "Configure uses backslash");
    require(
        ConstructionKeys.defaultKey("key.little.config_secondary.item", 67) == 92,
        "Secondary configuration retains the same physical key with native Shift");
    require(
        ConstructionKeys.defaultKey("key.little.building_mode", 66) == 61, "Building uses equals");
    require(ConstructionKeys.defaultKey("key.little.up", 265) == 265, "Arrow keys remain native");
    require(ConstructionKeys.defaultKey("key.little.undo", 90) == 90, "Undo remains native");
    require(ConstructionKeys.defaultKey("key.little.redo", 89) == 89, "Redo remains native");
  }

  private static void commandMessages() {
    var scanned =
        ConstructionMessages.literal("Scanned 2 levels, 3 chunks, 4 blocks, 5 tiles").getContents();
    require(
        scanned instanceof TranslatableContents, "Server feedback remains a localized component");
    var contents = (TranslatableContents) scanned;
    require(
        contents.getKey().equals("aeroscapes.construction.command.scan")
            && java.util.Arrays.equals(contents.getArgs(), new Object[] {"2", "3", "4", "5"}),
        "Native command counters reach the receiving player's translation independently");
    require(
        ConstructionMessages.literal("Owner's own building name")
            .getString()
            .equals("Owner's own building name"),
        "Unrecognized source text remains literal");
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
