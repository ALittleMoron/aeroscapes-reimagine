package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Actual exported aquatic meshes and their native material samples, without a client. */
public final class AquaticToolModelTest {
  private AquaticToolModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview");
    net.neoforged.fml.loading.LoadingModList.of(
        List.of(), List.of(), List.of(), List.of(), Map.of());
    net.minecraft.SharedConstants.tryDetectVersion();
    net.minecraft.server.Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject previews = new JsonObject();
    for (String item : AquaticToolModels.ITEMS) {
      String id = "hybrid_aquatic:" + item;
      JsonObject model =
          JsonParser.parseString(
                  Files.readString(
                      assets.resolve(
                          "hybrid_aquatic/models/item/equipment/held/" + item + ".json")))
              .getAsJsonObject();
      List<Face> faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      previews.add(id, VanillaEquipmentModelTest.preview(model, faces));
      if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
      require(!faces.isEmpty(), "Exported tool bakes: " + id);
      for (Face face : faces)
        for (var vertex : face.vertices())
          require(vertex.isFinite(), "Finite baked geometry: " + id);
      VanillaEquipmentModelTest.noOverlaps(id, faces);
      opaque(assets, id, faces);
      shaftTexels(assets, id, faces);
      if (item.startsWith("coral_")) coralMountContact(assets, id, faces);
      grip(model, new org.joml.Vector3f(8, 5, 8));
    }
    JsonObject net = HeldItemModels.nativeModel("hapi:creature_net").deepCopy();
    JsonObject adapter =
        JsonParser.parseString(
                Files.readString(
                    assets.resolve("hapi/models/item/equipment/creature_net_held.json")))
            .getAsJsonObject();
    net.add("display", adapter.get("display"));
    grip(net, new org.joml.Vector3f(8, 8.5f, 11));
    var netFaces = VanillaEquipmentModelTest.bakeCuboids(net);
    require(!netFaces.isEmpty(), "The inherited native net has visible geometry");
    previews.add("hapi:creature_net", VanillaEquipmentModelTest.preview(net, netFaces));
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Aquatic equipment: nine exported meshes, native opaque UV samples and provider net loaded");
  }

  private static void coralMountContact(Path assets, String id, List<Face> faces)
      throws IOException {
    BufferedImage nativeCoral = NativeEquipmentModels.image("hybrid_aquatic:item/coral_blade");
    Set<Integer> goldPalette =
        Set.of(
            nativeCoral.getRGB(9, 8), nativeCoral.getRGB(6, 7),
            nativeCoral.getRGB(7, 7), nativeCoral.getRGB(8, 7));
    List<Face> socket = new ArrayList<>(), head = new ArrayList<>();
    Map<String, BufferedImage> images = new HashMap<>();
    float lowX = Float.POSITIVE_INFINITY, highX = Float.NEGATIVE_INFINITY;
    float lowZ = Float.POSITIVE_INFINITY, highZ = Float.NEGATIVE_INFINITY;
    for (Face face : faces) {
      if (face.texture().endsWith("_shaft")) continue;
      BufferedImage image = images.get(face.texture());
      if (image == null) {
        image =
            javax.imageio.ImageIO.read(
                assets.resolve(face.texture().replace(":", "/textures/") + ".png").toFile());
        if (image == null) throw new IOException("Unreadable coral material: " + face.texture());
        images.put(face.texture(), image);
      }
      double u = 0, v = 0;
      for (var coordinate : face.uv()) {
        u += coordinate.getAsJsonArray().get(0).getAsDouble() / 4;
        v += coordinate.getAsJsonArray().get(1).getAsDouble() / 4;
      }
      int x = Math.clamp((int) (u * image.getWidth() / 16), 0, image.getWidth() - 1);
      int y = Math.clamp((int) (v * image.getHeight() / 16), 0, image.getHeight() - 1);
      if (!goldPalette.contains(image.getRGB(x, y))) {
        head.add(face);
        continue;
      }
      socket.add(face);
      for (var point : face.vertices()) {
        lowX = Math.min(lowX, point.x);
        highX = Math.max(highX, point.x);
        lowZ = Math.min(lowZ, point.z);
        highZ = Math.max(highZ, point.z);
      }
    }
    require(!socket.isEmpty(), "The baked tool retains its native gold mount: " + id);
    float centerX = (lowX + highX) / 2, centerZ = (lowZ + highZ) / 2;
    double[] mount = verticalRange(socket, centerX, centerZ);
    double[] coral = verticalRange(head, centerX, centerZ);
    require(
        Double.isFinite(mount[0]) && Double.isFinite(coral[0]),
        "The baked head and mount both cross the mount center: " + id);
    // Compare real surfaces at the mount, where the distant downward tip cannot hide a gap.
    require(
        coral[0] <= mount[1] + .00001 && coral[1] >= mount[0] - .00001,
        "The baked head meets its mount at the socket center: "
            + id
            + "; gap in model units: "
            + Math.max(coral[0] - mount[1], mount[0] - coral[1]) * 16);
  }

  private static double[] verticalRange(List<Face> faces, float x, float z) {
    double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
    for (Face face : faces)
      for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}}) {
        var a = face.vertices().get(triangle[0]);
        var b = face.vertices().get(triangle[1]);
        var c = face.vertices().get(triangle[2]);
        double denominator = (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z);
        if (Math.abs(denominator) < .000000000001) continue;
        double first = ((b.z - c.z) * (x - c.x) + (c.x - b.x) * (z - c.z)) / denominator;
        double second = ((c.z - a.z) * (x - c.x) + (a.x - c.x) * (z - c.z)) / denominator;
        double third = 1 - first - second;
        if (first < -.000001 || second < -.000001 || third < -.000001) continue;
        double y = first * a.y + second * b.y + third * c.y;
        low = Math.min(low, y);
        high = Math.max(high, y);
      }
    return new double[] {low, high};
  }

  private static void shaftTexels(Path assets, String id, List<Face> faces) throws IOException {
    for (Face face : faces) {
      if (!face.texture().endsWith("_shaft")) continue;
      var image =
          javax.imageio.ImageIO.read(
              assets.resolve(face.texture().replace(":", "/textures/") + ".png").toFile());
      if (image == null) throw new IOException("Unreadable shaft field: " + face.texture());
      for (int edge = 0; edge < 2; edge++) {
        var a = face.uv().get(edge).getAsJsonArray();
        var b = face.uv().get(edge + 1).getAsJsonArray();
        double u = (a.get(0).getAsDouble() - b.get(0).getAsDouble()) * image.getWidth() / 16;
        double v = (a.get(1).getAsDouble() - b.get(1).getAsDouble()) * image.getHeight() / 16;
        double lengthInModelUnits =
            face.vertices().get(edge).distance(face.vertices().get(edge + 1)) * 16;
        double density = Math.hypot(u, v) / lengthInModelUnits;
        require(
            density > .9 && density < 1.1,
            "Shaft texels match the pinned Refined square-shaft scale on both axes: " + id);
      }
    }
  }

  @SuppressWarnings("deprecation") // Exact pinned ItemTransform application in both hands/cameras.
  private static void grip(JsonObject source, org.joml.Vector3f grip) {
    JsonObject display = new JsonObject();
    display.add("display", source.get("display"));
    var model = net.minecraft.client.renderer.block.model.BlockModel.fromString(display.toString());
    for (var context :
        List.of(
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      var pose = new com.mojang.blaze3d.vertex.PoseStack();
      model.getTransforms().getTransform(context).apply(context.name().endsWith("LEFT_HAND"), pose);
      var actual =
          pose.last().pose().transformPosition(new org.joml.Vector3f(grip).sub(8, 8, 8).div(16));
      require(
          actual.distance(new org.joml.Vector3f(0, -2, 2).div(16)) < .00001,
          "Native mirrored transform keeps the grip at the palm contact: " + context);
    }
  }

  static void opaque(Path assets, String id, List<Face> faces) throws IOException {
    for (Face face : faces) {
      Path local = assets.resolve(face.texture().replace(":", "/textures/") + ".png");
      var image =
          Files.isRegularFile(local)
              ? javax.imageio.ImageIO.read(local.toFile())
              : NativeEquipmentModels.image(face.texture());
      if (image == null) throw new IOException("Unreadable physical material: " + face.texture());
      for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}})
        for (int a = 0; a <= 12; a++)
          for (int b = 0; b <= 12 - a; b++) {
            double u = 0, v = 0;
            double[] weights = {a / 12.0, b / 12.0, 1 - (a + b) / 12.0};
            for (int point = 0; point < 3; point++) {
              var uv = face.uv().get(triangle[point]).getAsJsonArray();
              u += uv.get(0).getAsDouble() * weights[point];
              v += uv.get(1).getAsDouble() * weights[point];
            }
            require(u >= 0 && u <= 16 && v >= 0 && v <= 16, "UV inside native sprite: " + id);
            int x = Math.clamp((int) (u * image.getWidth() / 16), 0, image.getWidth() - 1);
            int y = Math.clamp((int) (v * image.getHeight() / 16), 0, image.getHeight() - 1);
            require(
                (image.getRGB(x, y) >>> 24) == 255,
                "No background holes in physical surfaces: " + id + " at " + x + "," + y);
          }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
