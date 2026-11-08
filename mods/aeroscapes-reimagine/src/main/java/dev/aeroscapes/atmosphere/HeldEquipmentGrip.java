package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** A rigid tool's model-space grip follows the same animated frame as the visible palm. */
public final class HeldEquipmentGrip {
  private HeldEquipmentGrip() {}

  public static boolean usesPalm(ResourceLocation id) {
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
