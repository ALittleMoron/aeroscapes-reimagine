package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.atmosphere.HeldEquipmentGrip;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;

/** Neutral classic-arm frames for ordinary baked items, in camera space before projection. */
public final class HeldViewport {
  private HeldViewport() {}

  public static JsonArray attachments(
      JsonObject physical, ResourceLocation item, ToolKind kind, boolean refined, boolean atPalm)
      throws IOException, ReflectiveOperationException {
    return attachments(physical, item, kind, refined, atPalm, false);
  }

  @SuppressWarnings("deprecation") // Use Minecraft's display parser and handed ItemTransform.
  static JsonArray attachments(
      JsonObject physical,
      ResourceLocation item,
      ToolKind kind,
      boolean refined,
      boolean atPalm,
      boolean slim)
      throws IOException, ReflectiveOperationException {
    var transforms = ConstructionPlacementTest.handModel(physical).getTransforms();
    var result = new JsonArray();
    var root = new Matrix4f().rotateZ((float) Math.PI);
    var body =
        LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
            .bakeRoot();
    JsonArray frames;
    try (var stream = HeldViewport.class.getResourceAsStream("/held-arm-frames.json")) {
      if (stream == null) throw new IOException("Missing recorded Punchy arm frames");
      frames =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonArray();
    }
    for (boolean left : new boolean[] {false, true}) {
      var first =
          left
              ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
      var third =
          left
              ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
              : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      var stack = HeldEquipmentGrip.hexStackId(item);
      var context = HeldEquipmentGrip.punchyDisplay(stack, third, left, atPalm, refined);
      var arm = fromRows(frames.get(left ? 0 : 2).getAsJsonArray());
      var skin = new Matrix4f(arm);
      skin.m30(skin.m30() / 16).m31(skin.m31() / 16).m32(skin.m32() / 16);
      var itemArm = new Matrix4f(root).mul(skin);
      var skinRoot = new Matrix4f(root);
      if (slim) skinRoot.translate((left ? 1f : -1f) * .5f / 16, 0, 0);
      skin.mulLocal(skinRoot);
      var punchy =
          atPalm
              ? HeldEquipmentGrip.palm(root, arm, left, slim)
              : ConstructionPlacementTest.neutralPunchy(itemArm, left, kind);
      punchy = HeldEquipmentGrip.smallItemGrip(stack, punchy, refined);
      transforms.getTransform(context).apply(left, punchy);
      var pose = entry("punchy", left, context, punchy);
      pose.addProperty("armWidth", slim ? "slim" : "classic");
      pose.add("skin", ConstructionPlacementTest.matrix(skin));
      var cubes = new JsonArray();
      body.getChild(left ? "left_arm" : "right_arm")
          .visit(
              new PoseStack(),
              (unused, path, index, cube) -> {
                var bounds = new JsonObject();
                bounds.add(
                    "from", ConstructionPlacementTest.vector(cube.minX, cube.minY, cube.minZ));
                bounds.add("to", ConstructionPlacementTest.vector(cube.maxX, cube.maxY, cube.maxZ));
                cubes.add(bounds);
              });
      pose.add("nativeArmCubes", cubes);
      result.add(pose);

      var vanilla = new PoseStack();
      // ItemInHandRenderer.applyItemArmTransform at zero equip and swing progress.
      vanilla.translate((left ? -1 : 1) * .56f, -.52f, -.72f);
      transforms.getTransform(first).apply(left, vanilla);
      result.add(entry("vanilla", left, first, vanilla));
    }
    return result;
  }

  private static JsonObject entry(
      String source, boolean left, ItemDisplayContext context, PoseStack item) {
    var entry = new JsonObject();
    entry.addProperty("source", source);
    entry.addProperty("left", left);
    entry.addProperty("context", context.name());
    entry.addProperty("fovY", 70);
    entry.add("item", ConstructionPlacementTest.matrix(item.last().pose()));
    return entry;
  }

  static Matrix4f fromRows(JsonArray rows) {
    var result = new Matrix4f();
    for (int row = 0; row < 4; row++)
      for (int column = 0; column < 4; column++)
        result.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
    return result.determineProperties();
  }
}
