package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** A rigid tool's model-space grip follows the same animated frame as the visible palm. */
public final class HeldEquipmentGrip {
  private HeldEquipmentGrip() {}

  public static boolean usesPalm(ResourceLocation id) {
    if (!ItemModelResources.enabled()) return false;
    return switch (id.getNamespace()) {
      case "minecraft" -> id.getPath().equals("brush");
      case "littletiles" -> id.getPath().equals("paint_brush") || id.getPath().equals("bag");
      case "betterarcheology" ->
          switch (id.getPath()) {
            case "iron_brush", "diamond_brush", "netherite_brush" -> true;
            default -> false;
          };
      case "spawn" -> id.getPath().equals("casting_net");
      case "nutritionalbalance" -> id.getPath().equals("lunchbox");
      default -> false;
    };
  }

  public static ItemDisplayContext punchyDisplay(
      ResourceLocation item, ItemDisplayContext original, boolean left, boolean atPalm) {
    return punchyDisplay(
        item, original, left, atPalm, ItemModelResources.refinedModelsLoaded(item));
  }

  public static ItemDisplayContext punchyDisplay(
      ResourceLocation item,
      ItemDisplayContext original,
      boolean left,
      boolean atPalm,
      boolean refined) {
    // Punchy normally requests the third-person model even from the player's own camera.
    boolean separate = refined && separateHexDisplay(item);
    if (!separate && !atPalm) return original;
    return left
        ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
  }

  static boolean separateHexDisplay(ResourceLocation item) {
    return item.getNamespace().equals("hexcasting")
        && (item.getPath().startsWith("staff/") || smallHexItem(item));
  }

  public static ResourceLocation hexStackId(ResourceLocation model) {
    if (!model.getNamespace().equals("hexcasting")) return model;
    String name = model.getPath();
    if (name.startsWith("phial_")) return model.withPath("battery");
    if (name.startsWith("staff/quenched_")) return model.withPath("staff/quenched");
    for (String carrier : List.of("cypher", "trinket", "artifact", "focus"))
      if (name.startsWith(carrier + "_")) return model.withPath(carrier);
    return model;
  }

  private static boolean smallHexItem(ResourceLocation item) {
    return switch (item.getPath()) {
      case "battery", "cypher", "trinket", "artifact", "focus" ->
          item.getNamespace().equals("hexcasting");
      default -> false;
    };
  }

  /** Align the vanilla first-person model origin with Punchy's grip on small Hex items. */
  public static PoseStack smallItemGrip(ResourceLocation item, PoseStack base) {
    return smallItemGrip(item, base, ItemModelResources.refinedModelsLoaded(item));
  }

  public static PoseStack smallItemGrip(ResourceLocation item, PoseStack base, boolean separate) {
    if (!separate || !smallHexItem(item)) return base;
    var result = new PoseStack();
    result.mulPose(base.last().pose());
    result.translate(0, -2.5f / 16, 1f / 16);
    return result;
  }

  public static PoseStack palm(Matrix4fc root, Matrix4fc arm, boolean left, boolean slim) {
    Matrix4f frame = new Matrix4f(arm);
    // Punchy's cached bone frame stores translation in pixels, like ModelPartMixin.
    frame.m30(frame.m30() / 16);
    frame.m31(frame.m31() / 16);
    frame.m32(frame.m32() / 16);
    PoseStack pose = new PoseStack();
    pose.mulPose(new Matrix4f(root));
    if (slim) pose.translate((left ? 1f : -1f) * .5f / 16, 0, 0);
    pose.mulPose(frame);
    pose.translate((left ? 1f : -1f) * (slim ? .5f : 1f) / 16, .5f, 0);
    pose.mulPose(Axis.XP.rotationDegrees(-90));
    pose.mulPose(Axis.YP.rotationDegrees(180));
    return pose;
  }
}
