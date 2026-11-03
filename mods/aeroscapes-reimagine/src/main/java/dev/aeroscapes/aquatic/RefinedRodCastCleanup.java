package dev.aeroscapes.aquatic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;

/** Removes stowed tackle only from the exact loaded Refined Tools 3.0 cast geometry. */
public final class RefinedRodCastCleanup {
  private static final Map<String, String> SHAPES =
      Map.of(
          "item/rods/held_fishing_cast",
          "f0c9427d0da5279adbb9ee58bc7887528158b66a07017b196030e0cf6fc993d0",
          "item/rods/held_fishing_cast_left",
          "c5d9b393f5a5dd112a7a0df444cd13ce898036bdef373bab0afbb8d18ca05efc",
          "item/rods/held_fishing_cast_fp",
          "51d71d1d72a68c94724a0d2000a68ebd251e974141ae8bcc36f2c39201019c43",
          "item/rods/held_fishing_cast_fp_left",
          "fb9e51ec339348ab599fe2d12952585899148e4f96ea373f50c4acd64b0dbc0f");

  private RefinedRodCastCleanup() {}

  public static boolean removeDecoration(ResourceLocation location, List<BlockElement> elements) {
    if (!location.getNamespace().equals("minecraft") || elements.size() != 18) return false;
    String expected = SHAPES.get(location.getPath());
    if (expected == null || !expected.equals(fingerprint(elements))) return false;
    // The provider's first four elements are stowed tackle; the fifth is its hanging line.
    elements.subList(0, 5).clear();
    return true;
  }

  static String fingerprint(List<BlockElement> elements) {
    StringBuilder shape = new StringBuilder().append(elements.size()).append(';');
    for (BlockElement element : elements) {
      if (!ExtraFaceData.DEFAULT.equals(element.getFaceData())) return "";
      vector(shape, element.from);
      vector(shape, element.to);
      shape.append(element.shade).append(';');
      if (element.rotation == null) {
        shape.append("none;");
      } else {
        vector(shape, element.rotation.origin());
        shape
            .append(element.rotation.axis().getName())
            .append(';')
            .append(Float.floatToIntBits(element.rotation.angle()))
            .append(';')
            .append(element.rotation.rescale())
            .append(';');
      }
      for (Direction direction : Direction.values()) {
        shape.append(direction.getName()).append(';');
        var face = element.faces.get(direction);
        if (face == null) {
          shape.append("none;");
          continue;
        }
        if (!ExtraFaceData.DEFAULT.equals(face.faceData()) || face.uv().uvs == null) return "";
        shape
            .append(face.cullForDirection() == null ? "none" : face.cullForDirection().getName())
            .append(';')
            .append(face.tintIndex())
            .append(';')
            .append(face.texture().length())
            .append(':')
            .append(face.texture())
            .append(';')
            .append(face.uv().rotation)
            .append(';');
        for (float coordinate : face.uv().uvs)
          shape.append(Float.floatToIntBits(coordinate)).append(',');
        shape.append(';');
      }
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(shape.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }

  private static void vector(StringBuilder shape, Vector3f vector) {
    shape
        .append(Float.floatToIntBits(vector.x))
        .append(',')
        .append(Float.floatToIntBits(vector.y))
        .append(',')
        .append(Float.floatToIntBits(vector.z))
        .append(',')
        .append(';');
  }
}
