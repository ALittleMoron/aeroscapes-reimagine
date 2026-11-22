package dev.aeroscapes.sky.fauna;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Pickable sections of the animated disc. Flight and entity collisions still use the body. */
final class SkyRayWingPart extends AerialHitPart {
  // Model pixels relative to each hinge: inner edge, outer edge, front, back, min Y, max Y.
  private static final int[][] SECTIONS = {
    {-1, 10, -21, 24, -2, 4},
    {10, 25, -16, 21, -2, 4},
    {-1, 15, -7, 21, -1, 3},
    {0, 8, 4, 23, 0, 3},
    {8, 13, 13, 25, 1, 3}
  };
  private final Vector3f minimum = new Vector3f();
  private final Vector3f maximum = new Vector3f();

  private SkyRayWingPart(SkyRay ray) {
    super(ray);
  }

  static SkyRayWingPart[] create(SkyRay ray) {
    var parts = new SkyRayWingPart[2 * SECTIONS.length];
    for (int i = 0; i < parts.length; i++) parts[i] = new SkyRayWingPart(ray);
    return parts;
  }

  static void position(SkyRay ray, SkyRayWingPart[] parts) {
    var pose = ray.flightPose(1);
    var body =
        new Matrix4f()
            .rotateY(-ray.yBodyRot * Mth.DEG_TO_RAD)
            .scale(ray.getScale() / 16, -ray.getScale() / 16, -ray.getScale() / 16)
            .translate(0, -17, 0)
            .rotateZ(pose.bank())
            .rotateX(pose.pitch());
    int index = 0;
    for (int sign = -1; sign <= 1; sign += 2) {
      var wing = new Matrix4f(body).translate(sign * 11, 0, 0).rotateZ(sign * pose.shoulder());
      if (ray.isCalf()) wing.scale(SkyRayWings.CALF_WING_SPAN, 1, 1);
      var tip = new Matrix4f(wing).translate(sign * 25, 0, 0).rotateZ(sign * pose.middle());
      var edge = new Matrix4f(tip).translate(sign * 15, 0, 0).rotateZ(sign * pose.tip());
      for (int i = 0; i < SECTIONS.length; i++) {
        var part = parts[index++];
        var section = SECTIONS[i];
        int minX = sign > 0 ? section[0] : -section[1];
        int maxX = sign > 0 ? section[1] : -section[0];
        (i < 2 ? wing : i == 2 ? tip : edge)
            .transformAab(
                minX,
                section[4],
                section[2],
                maxX,
                section[5],
                section[3],
                part.minimum,
                part.maximum);
        var box =
            new AABB(
                ray.getX() + part.minimum.x,
                ray.getY() + part.minimum.y,
                ray.getZ() + part.minimum.z,
                ray.getX() + part.maximum.x,
                ray.getY() + part.maximum.y,
                ray.getZ() + part.maximum.z);
        part.place(box);
      }
    }
  }
}
