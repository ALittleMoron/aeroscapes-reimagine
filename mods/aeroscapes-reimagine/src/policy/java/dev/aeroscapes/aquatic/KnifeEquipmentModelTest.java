package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exercises the exported knife meshes through the native OBJ parser and baker. */
public final class KnifeEquipmentModelTest {
  private KnifeEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) throw new IllegalArgumentException("Expected repository");
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    for (String tier : KnifeEquipmentModels.TIERS) {
      String id = "farmersdelight:" + tier + "_knife";
      var model =
          JsonParser.parseString(
                  Files.readString(
                      assets.resolve(
                          "farmersdelight/models/item/equipment/held/" + tier + "_knife.json")))
              .getAsJsonObject();
      var faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      require(!faces.isEmpty(), "The exported knife bakes visible faces: " + id);
      VanillaEquipmentModelTest.noOverlaps(id, faces);
      handContact(model, assets);
      var nativeSprite = NativeEquipmentModels.image("farmersdelight:item/" + tier + "_knife");
      var palette = new HashSet<Integer>();
      for (int y = 0; y < nativeSprite.getHeight(); y++)
        for (int x = 0; x < nativeSprite.getWidth(); x++) palette.add(nativeSprite.getRGB(x, y));
      var material =
          ImageIO.read(
              assets
                  .resolve("aeroscapes_reimagine/textures/item/knives/" + tier + ".png")
                  .toFile());
      if (material == null) throw new IOException("Unreadable knife material: " + id);
      for (int y = 0; y < material.getHeight(); y++)
        for (int x = 0; x < material.getWidth(); x++) {
          int pixel = material.getRGB(x, y);
          require((pixel >>> 24) == 255, "Opaque knife surfaces: " + id);
          require(palette.contains(pixel), "Each material retains the native palette: " + id);
        }
      for (var face : faces) {
        require(
            face.texture().equals("aeroscapes_reimagine:item/knives/" + tier),
            "The actual baker resolves the intended tier's material: " + id);
        require(
            Float.isFinite(face.normal().x + face.normal().y + face.normal().z),
            "Baked faces have positive area: " + id);
        for (var coordinate : face.uv())
          for (var value : coordinate.getAsJsonArray())
            require(
                value.getAsDouble() >= 0 && value.getAsDouble() <= 16,
                "Baked UVs stay in the material sprite: " + id);
      }
    }
    System.out.println(
        "Five knives: native OBJ bake, winding, UVs, opaque native palettes and coplanar surfaces passed");
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }

  @SuppressWarnings("deprecation") // Exercise the pinned native ItemTransform consumer.
  private static void handContact(JsonObject model, Path assets) throws IOException {
    var handDisplay = new JsonObject();
    handDisplay.add("display", model.get("display"));
    var transforms = BlockModel.fromString(handDisplay.toString()).getTransforms();
    var blade = VanillaEquipmentModelTest.bakeObj(model, assets, "blade");
    var grip = VanillaEquipmentModelTest.bakeObj(model, assets, "grip");
    float heelY = Float.POSITIVE_INFINITY, neckY = Float.NEGATIVE_INFINITY;
    for (var face : grip)
      for (var vertex : face.vertices()) {
        heelY = Math.min(heelY, vertex.y * 16);
        neckY = Math.max(neckY, vertex.y * 16);
      }
    for (var context :
        List.of(
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      boolean left = context.name().endsWith("LEFT_HAND");
      var pose = new PoseStack();
      pose.mulPose(Axis.XP.rotationDegrees(-90));
      pose.mulPose(Axis.YP.rotationDegrees(180));
      pose.translate((left ? -1 : 1) / 16f, .125f, -.625f);
      transforms.getTransform(context).apply(left, pose);
      var matrix = new Matrix4f(pose.last().pose()).translate(-.5f, -.5f, -.5f);
      var heel = matrix.transformPosition(new Vector3f(8, heelY, 8).div(16)).mul(16);
      var neck = matrix.transformPosition(new Vector3f(8, neckY, 8).div(16)).mul(16);
      float engagement = Math.min(heel.z, 2) - Math.max(neck.z, -2);
      require(
          engagement >= 3.2f,
          "At least four fifths of the fist holds the continuous grip: " + context);
      for (float depth : new float[] {-1.8f, 1}) {
        var contact = new Vector3f(neck).lerp(heel, (depth - neck.z) / (heel.z - neck.z));
        require(
            Math.abs(contact.x - (left ? 1 : -1)) < 1.5f && contact.y > 6 && contact.y < 10,
            "The engaged shaft passes through the palm volume: " + context);
      }
      for (var face : grip)
        for (var vertex : face.vertices()) {
          if (Math.abs(vertex.y * 16 - neckY) > .00001f) continue;
          var point = matrix.transformPosition(new Vector3f(vertex)).mul(16);
          require(
              point.z <= -2.8f,
              "A visible length of wood separates the whole collar from the fist: "
                  + context
                  + "; clearance="
                  + (-2 - point.z));
        }
      for (var face : blade)
        for (var vertex : face.vertices()) {
          var point = matrix.transformPosition(new Vector3f(vertex)).mul(16);
          require(point.z <= -2.8f, "The entire working blade clears the native fist: " + context);
        }
    }
  }
}
