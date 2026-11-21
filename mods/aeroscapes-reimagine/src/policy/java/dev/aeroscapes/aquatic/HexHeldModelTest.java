package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;

/**
 * Exercises the native bakers for all physical state leaves, including partially filled vessels.
 */
public final class HexHeldModelTest {
  private HexHeldModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    Path items = assets.resolve("hexcasting/models/item");
    Path held = items.resolve("equipment/held");
    var reference =
        handPoses(read(assets.resolve("chipped/models/item/equipment/held/alchemy_book.json")));
    var bookStates = new HashSet<String>(List.of("patchouli_book", "lore_fragment"));
    for (var override :
        HeldItemModels.nativeModel("hexcasting:spellbook").getAsJsonArray("overrides"))
      bookStates.add(
          override
              .getAsJsonObject()
              .get("model")
              .getAsString()
              .substring("hexcasting:item/".length()));
    int verified = 0;
    var pigmentMeshes = new HashSet<String>();
    var previewTextures = new PreviewTextures(assets, 0);
    try (var paths = Files.walk(held)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        String state =
            held.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
        state = state.substring(0, state.length() - ".json".length());
        JsonObject original = HeldItemModels.nativeModel("hexcasting:" + state);
        JsonObject wrapper = read(items.resolve(state + ".json"));
        var views = wrapper.getAsJsonObject("perspectives");
        require(Objects.equals(original.remove("overrides"), wrapper.get("overrides")), state);
        require(original.equals(wrapper.get("base")), state);
        require(views.keySet().equals(java.util.Set.copyOf(IronHeldMesh.HANDS)), state);
        for (String hand : IronHeldMesh.HANDS) {
          require(
              views
                  .getAsJsonObject(hand)
                  .get("parent")
                  .getAsString()
                  .equals("hexcasting:item/equipment/held/" + state),
              state);
        }
        var physical = read(path);
        if (bookStates.remove(state)) {
          var actual = handPoses(physical);
          for (var expected : reference.entrySet())
            require(
                expected.getValue().equals(actual.get(expected.getKey()), .000001f),
                state + " " + expected.getKey());
        }
        List<VanillaEquipmentModelTest.Face> faces =
            physical.has("children")
                ? VanillaEquipmentModelTest.bakeComposite(physical, assets)
                : VanillaEquipmentModelTest.bakeObj(physical, assets);
        require(
            !faces.isEmpty()
                && faces.stream().noneMatch(face -> face.texture().equals("minecraft:missingno")),
            state);
        if (state.startsWith("staff/")) staffGrip(state, physical, faces);
        VanillaEquipmentModelTest.noOverlaps(state, faces);
        if (pocketItem(state)) verifyPocketItem(state, physical, original, faces, previewTextures);
        if (state.equals("jeweler_hammer")) hammerGrip(physical, faces);
        if (state.startsWith("phial_"))
          require(faces.stream().anyMatch(face -> face.alpha() > 0 && face.alpha() < 255), state);
        if (original.getAsJsonObject("textures").has("layer1")
            && (state.startsWith("focus")
                || state.startsWith("spellbook")
                || state.startsWith("thought_knot")))
          require(faces.stream().anyMatch(face -> face.tint() == 1), state);
        if (state.equals("thought_knot_written")) writtenKnot(faces);
        if (state.contains("colorizer")) {
          var animated = original.getAsJsonObject("textures").get("layer0").getAsString();
          require(faces.stream().allMatch(face -> face.texture().equals(animated)), state);
          if (pigmentMeshes.add(physical.get("model").getAsString())) pigmentFrame(faces);
        }
        verified++;
      }
    }
    require(bookStates.isEmpty(), "missing held books " + bookStates);
    System.out.println(
        "Hex Casting: " + verified + " native state leaves baked. Book hand poses match Chipped.");
  }

  private static void pigmentFrame(List<VanillaEquipmentModelTest.Face> faces) {
    var visible = new Area();
    for (var face : faces) {
      if (face.normal().z <= 0) continue;
      var polygon = new Path2D.Float();
      for (int i = 0; i < face.uv().size(); i++) {
        var uv = face.uv().get(i).getAsJsonArray();
        float u = uv.get(0).getAsFloat(), v = uv.get(1).getAsFloat();
        if (i == 0) polygon.moveTo(u, v);
        else polygon.lineTo(u, v);
      }
      polygon.closePath();
      visible.add(new Area(polygon));
    }
    for (int v = 0; v < 16; v++)
      for (int u = 0; u < 16; u++)
        require(
            visible.contains(u + .5, v + .5), "Pigment front omits native texel " + u + "," + v);
  }

  private static void writtenKnot(List<VanillaEquipmentModelTest.Face> faces) {
    var string =
        faces.stream()
            .filter(face -> face.tint() < 0)
            .flatMap(face -> face.vertices().stream())
            .toList();
    var inset =
        faces.stream()
            .filter(face -> face.tint() == 1)
            .flatMap(face -> face.vertices().stream())
            .toList();
    require(
        !string.isEmpty() && !inset.isEmpty(),
        "Written knot needs an untinted string and a tint-1 inset");
    for (int axis : List.of(0, 1, 2)) {
      var cord = string.stream().mapToDouble(point -> point.get(axis)).summaryStatistics();
      var stone = inset.stream().mapToDouble(point -> point.get(axis)).summaryStatistics();
      require(
          cord.getMin() < stone.getMax() && stone.getMin() < cord.getMax(),
          "Written knot inset detached from string");
    }
  }

  private static boolean pocketItem(String state) {
    return List.of("focus", "cypher", "trinket", "artifact", "phial")
        .contains(state.split("_", 2)[0]);
  }

  private static void verifyPocketItem(
      String state,
      JsonObject model,
      JsonObject original,
      List<VanillaEquipmentModelTest.Face> faces,
      PreviewTextures previewTextures)
      throws IOException, ReflectiveOperationException {
    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hexcasting", state);
    var frames = HeldViewport.attachments(model, id, ToolKind.INGREDIENT, true, false);
    if (state.endsWith("_filled")) {
      String overlay = original.getAsJsonObject("textures").get("layer1").getAsString();
      var sprite = NativeEquipmentModels.image(overlay);
      var sparkle = faces.stream().filter(f -> f.texture().equals(overlay)).toList();
      require(!sparkle.isEmpty(), state + " renders native glint");
      verifyGlintVisibility(state, model, faces, overlay, frames, previewTextures);
      for (var face : sparkle) {
        int visible = 0, sampled = 0;
        for (int y = 0; y < sprite.getHeight(); y++)
          for (int x = 0; x < sprite.getWidth(); x++) {
            if ((sprite.getRGB(x, y) >>> 24) == 0) continue;
            visible++;
            double u = (x + .5) * 16 / sprite.getWidth(), v = (y + .5) * 16 / sprite.getHeight();
            if (insideUv(face.uv(), 0, 1, 2, u, v) || insideUv(face.uv(), 0, 2, 3, u, v)) sampled++;
          }
        require(
            visible > 0 && sampled == visible, state + " samples all visible native glint pixels");
      }
    }
    var grips = new JsonArray();
    for (var frame : frames) if (frame.getAsJsonObject().has("skin")) grips.add(frame);
    for (var frame : PaintingEquipmentModelTest.thirdPersonAttachments(model)) grips.add(frame);
    var layers = original.getAsJsonObject("textures");
    var overlay = layers.has("layer1") ? layers.get("layer1").getAsString() : "";
    var materials = model.getAsJsonObject("textures");
    String cord = materials.has("cord") ? materials.get("cord").getAsString() : "";
    for (var frame : grips) {
      var f = frame.getAsJsonObject();
      var toPalm =
          matrix(f.getAsJsonArray("skin"))
              .invert()
              .mul(matrix(f.getAsJsonArray("item")))
              .translate(-.5f, -.5f, -.5f);
      var arm = f.getAsJsonArray("nativeArmCubes").get(0).getAsJsonObject();
      var from = arm.getAsJsonArray("from");
      var to = arm.getAsJsonArray("to");
      float fingers = to.get(1).getAsFloat(),
          wrist = fingers - (to.get(2).getAsFloat() - from.get(2).getAsFloat());
      boolean contact = false;
      for (var face : faces) {
        if (face.texture().equals(overlay) || face.texture().equals(cord)) continue;
        for (var vertex : face.vertices()) {
          var point = toPalm.transformPosition(new Vector3f(vertex)).mul(16);
          if (point.x > from.get(0).getAsFloat()
              && point.x < to.get(0).getAsFloat()
              && point.y > wrist
              && point.y < fingers
              && point.z > from.get(2).getAsFloat()
              && point.z < to.get(2).getAsFloat()) {
            contact = true;
            break;
          }
        }
        if (contact) break;
      }
      require(contact, state + " physical body contacts the fingers in " + f.get("context"));
    }
  }

  private static void verifyGlintVisibility(
      String state,
      JsonObject model,
      List<VanillaEquipmentModelTest.Face> faces,
      String overlay,
      JsonArray frames,
      PreviewTextures textures)
      throws IOException {
    int tint = state.startsWith("focus") ? 0x84ffcb : 0xffffff;
    var full = ModelPreview.surfaces(model, faces, textures, tint);
    var bare =
        ModelPreview.surfaces(
            model,
            faces.stream().filter(face -> !face.texture().equals(overlay)).toList(),
            textures,
            tint);
    var camera = new ModelPreview.Camera(400, 250, 70);
    for (var frame : frames) {
      var pose = frame.getAsJsonObject();
      var lit = ModelPreview.firstPersonFrame(full, pose, camera);
      var plain = ModelPreview.firstPersonFrame(bare, pose, camera);
      int visible = 0;
      for (int y = 0; y < camera.height(); y++) {
        for (int x = 0; x < camera.width(); x++) {
          int a = lit.getRGB(x, y), b = plain.getRGB(x, y);
          if (Math.abs((a >> 16 & 255) - (b >> 16 & 255)) > 10
              || Math.abs((a >> 8 & 255) - (b >> 8 & 255)) > 10
              || Math.abs((a & 255) - (b & 255)) > 10) visible++;
        }
      }
      require(
          visible >= 8,
          state
              + " visible glint pixels in "
              + pose.get("source")
              + " "
              + pose.get("context")
              + ": "
              + visible);
    }
  }

  private static boolean insideUv(JsonArray uv, int a, int b, int c, double u, double v) {
    int[] corners = {a, b, c};
    boolean pos = false, neg = false;
    for (int i = 0; i < 3; i++) {
      var p = uv.get(corners[i]).getAsJsonArray();
      var q = uv.get(corners[(i + 1) % 3]).getAsJsonArray();
      double dx = q.get(0).getAsDouble() - p.get(0).getAsDouble(),
          dy = q.get(1).getAsDouble() - p.get(1).getAsDouble();
      double cross = dx * (v - p.get(1).getAsDouble()) - dy * (u - p.get(0).getAsDouble());
      pos |= cross > .00001;
      neg |= cross < -.00001;
    }
    return !(pos && neg);
  }

  private static void hammerGrip(JsonObject model, List<VanillaEquipmentModelTest.Face> faces)
      throws IOException, ReflectiveOperationException {
    var wood = model.getAsJsonObject("textures").get("wood").getAsString();
    var lower = new Vector3f(Float.POSITIVE_INFINITY);
    var upper = new Vector3f(Float.NEGATIVE_INFINITY);
    for (var face : faces)
      if (face.texture().equals(wood))
        for (var vertex : face.vertices()) {
          lower.min(vertex);
          upper.max(vertex);
        }
    var center = new Vector3f(lower).lerp(upper, .5f).sub(.5f, .5f, .5f);
    for (var poses :
        List.of(
            ConstructionPlacementTest.attachments(model, "jeweler_hammer", ToolKind.INGREDIENT, 0),
            PaintingEquipmentModelTest.thirdPersonAttachments(model))) {
      for (var pose : poses) {
        var hand = pose.getAsJsonObject();
        var relative =
            matrix(hand.getAsJsonArray("skin")).invert().mul(matrix(hand.getAsJsonArray("item")));
        var working = relative.transformDirection(new Vector3f(-1, 0, 0));
        require(
            working.y > Math.max(Math.abs(working.z), Math.abs(working.x)),
            "hammer strike beyond fingers");
        var butt =
            relative.transformPosition(new Vector3f(center.x, lower.y - .5f, center.z)).mul(16);
        var eye =
            relative.transformPosition(new Vector3f(center.x, upper.y - .5f, center.z)).mul(16);
        var arm = hand.getAsJsonArray("nativeArmCubes").get(0).getAsJsonObject();
        var from = arm.getAsJsonArray("from");
        var to = arm.getAsJsonArray("to");
        float fingers = to.get(1).getAsFloat();
        float wrist = fingers - (to.get(2).getAsFloat() - from.get(2).getAsFloat());
        for (var side : List.of(from, to)) {
          float along = (side.get(2).getAsFloat() - butt.z) / (eye.z - butt.z);
          var crossing = new Vector3f(butt).lerp(eye, along);
          require(
              along > 0
                  && along < 1
                  && crossing.x > from.get(0).getAsFloat()
                  && crossing.x < to.get(0).getAsFloat()
                  && crossing.y > wrist
                  && crossing.y < fingers,
              "hammer shaft crosses both sides of the palm");
        }
      }
    }
    handPoses(model)
        .forEach(
            (context, pose) -> {
              if (context.firstPerson()) {
                var strikingFace = pose.transformDirection(new Vector3f(-1, 0, 0));
                require(
                    -strikingFace.z > Math.max(Math.abs(strikingFace.x), Math.abs(strikingFace.y)),
                    "hammer face points away from the vanilla camera");
              }
            });
  }

  private static Matrix4f matrix(JsonArray rows) {
    var result = new Matrix4f();
    for (int row = 0; row < 4; row++)
      for (int column = 0; column < 4; column++)
        result.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
    return result.determineProperties();
  }

  @SuppressWarnings("deprecation") // Apply the pinned ItemRenderer's native display transform.
  private static Map<ItemDisplayContext, Matrix4f> handPoses(JsonObject source) {
    var model = BlockModel.fromString("{\"display\":" + source.get("display") + "}");
    var result = new EnumMap<ItemDisplayContext, Matrix4f>(ItemDisplayContext.class);
    for (var context :
        List.of(
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      var held = new PoseStack();
      boolean left =
          context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      model.getTransforms().getTransform(context).apply(left, held);
      result.put(context, held.last().pose());
    }
    return result;
  }

  private static void staffGrip(
      String state, JsonObject model, List<VanillaEquipmentModelTest.Face> faces)
      throws IOException, ReflectiveOperationException {
    var extent =
        faces.stream()
            .flatMap(face -> face.vertices().stream())
            .mapToDouble(vertex -> vertex.y)
            .summaryStatistics();
    for (var hand : handPoses(model).entrySet()) {
      // ItemRenderer centres the baked mesh at (.5, .5, .5) before applying its hand transform.
      double gripY = new Matrix4f(hand.getValue()).invert().m31() + .5;
      double fraction = (gripY - extent.getMin()) / (extent.getMax() - extent.getMin());
      require(fraction > 1.0 / 3 && fraction < 2.0 / 3, state + " middle grip in " + hand.getKey());
    }
    var item =
        ResourceLocation.fromNamespaceAndPath(
            "hexcasting", state.startsWith("staff/quenched_") ? "staff/quenched" : state);
    for (var attachment : HeldViewport.attachments(model, item, ToolKind.INGREDIENT, true, false)) {
      var hand = attachment.getAsJsonObject();
      var pose = HeldViewport.fromRows(hand.getAsJsonArray("item"));
      // Include every point above the grip winding, including wood around the crystal.
      double crownBase = 12.0 / 16;
      for (var face : faces)
        for (var vertex : face.vertices())
          if (vertex.y >= crownBase) {
            var point = pose.transformPosition(new Vector3f(vertex).sub(.5f, .5f, .5f));
            var xy = screen(point);
            require(
                point.z < -.05 && xy.x > .06 && xy.x < .94 && xy.y > .06 && xy.y < .94,
                state
                    + " crown clipped in "
                    + hand.get("source")
                    + "/"
                    + hand.get("context")
                    + ": "
                    + xy);
          }
    }
    if (state.equals("staff/oak")) previousOakViewport(model, item);
  }

  private static Vector2f screen(Vector3f camera) {
    float halfHeight = -camera.z * (float) Math.tan(Math.toRadians(35));
    float halfWidth = halfHeight * 16 / 10;
    return new Vector2f(.5f + camera.x / (2 * halfWidth), .5f - camera.y / (2 * halfHeight));
  }

  private static void previousOakViewport(JsonObject model, ResourceLocation item)
      throws IOException, ReflectiveOperationException {
    // Reproduce Punchy's original third-person fallback. In the 2880x1800 profile screenshot
    // the bound shaft passed through (.79,.88), then crossed the right edge around (.99,.72).
    var previous =
        HeldViewport.attachments(model, item, ToolKind.INGREDIENT, false, false)
            .get(0)
            .getAsJsonObject();
    var pose = HeldViewport.fromRows(previous.getAsJsonArray("item"));
    var grip = screen(pose.transformPosition(new Vector3f(0, .25f / 16, 0)));
    var upperShaft = screen(pose.transformPosition(new Vector3f(.02f / 16, 8.85f / 16, 0)));
    var crystalTip = screen(pose.transformPosition(new Vector3f(0, 16.4f / 16, 0)));
    require(
        grip.distance(.79f, .88f) < .04
            && upperShaft.distance(.99f, .72f) < .08
            && crystalTip.x > 1.05f,
        "Recorded right-hand oak screenshot must reproduce a diagonal shaft and offscreen tip");
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static void require(boolean condition, String state) {
    if (!condition)
      throw new AssertionError("Hex held model failed native state or bake checks: " + state);
  }
}
