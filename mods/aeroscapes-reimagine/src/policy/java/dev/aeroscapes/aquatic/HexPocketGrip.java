package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.util.ArrayList;
import java.util.Comparator;
import org.joml.Vector3f;

/** Seats a solid point on each small object in the palm, independent of fill, wax and glints. */
final class HexPocketGrip {
  private HexPocketGrip() {}

  static P contact(EquipmentObjMesh mesh) {
    var vertices = new ArrayList<P>();
    double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = -x0, y1 = x1, z1 = x1;
    for (String line : mesh.obj().lines().filter(l -> l.startsWith("v ")).toList()) {
      var parts = line.split(" ");
      double x = Double.parseDouble(parts[1]) * 16,
          y = Double.parseDouble(parts[2]) * 16,
          z = Double.parseDouble(parts[3]) * 16;
      vertices.add(new P(x, y, z));
      x0 = Math.min(x0, x);
      x1 = Math.max(x1, x);
      y0 = Math.min(y0, y);
      y1 = Math.max(y1, y);
      z0 = Math.min(z0, z);
      z1 = Math.max(z1, z);
    }
    var grip = new P((x0 + x1) / 2, y0 + (y1 - y0) * .12, z0 + (z1 - z0) * .20);
    return vertices.stream()
        .min(
            Comparator.comparingDouble(
                v -> v.vector().distanceSquared(grip.vector()) + 3 * Math.pow(v.y() - grip.y(), 2)))
        .orElseThrow();
  }

  static JsonObject display(P grip, boolean glass) {
    var result = new JsonObject();
    for (String context : IronHeldMesh.HANDS) {
      boolean first = context.startsWith("first");
      var single = new JsonObject();
      var transform = new JsonObject();
      double scale = glass ? .83 : .82;
      transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
      transform.add(
          "rotation",
          IronHeldMesh.vector(first ? -10 : glass ? 72 : 55, first ? 18 : 12, glass ? -12 : -9));
      single.add(context, transform);
      // The third-person model turns a flask upright alongside the relaxed arm.
      // Punchy uses the first-person transform with its own animated wrist offset.
      var target = first ? new Vector3f(.64f, 2.87f, -.62f) : new Vector3f(1.86f, -.12f, .20f);
      HeldItemModels.anchorHeld(
          single, new Vector3f((float) grip.x(), (float) grip.y(), (float) grip.z()), target);
      result.add(context, transform);
    }
    return result;
  }
}
