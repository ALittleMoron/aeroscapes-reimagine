package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;

/** Separates the two sides of Refined Tools 3.0's steel inlay on both hand render paths. */
final class FlintAndSteelModelRepair {
  private static final String PUNCHY_HASH =
      "9221c9c09eda9bbc0a6c1839c2c2f4a23a7c752a2e3bbc8d96775f4a39bfcf9a";
  private static final String MODEL_HASH =
      "69e3bb393cdd47f292f0a8b46345b583a4d8da8720ea23ba4b20cf284fc8052e";
  private static final ResourceLocation PUNCHY_MODEL =
      ResourceLocation.withDefaultNamespace(
          "punchy/model_parts_items/geo/held_flint_steel.geo.json");
  private static final ResourceLocation HAND_MODEL =
      ResourceLocation.withDefaultNamespace("models/item/held_flint_steel.json");

  private FlintAndSteelModelRepair() {}

  static IoSupplier<InputStream> adapt(ResourceLocation location, IoSupplier<InputStream> source) {
    if (source == null) return null;
    boolean punchy = PUNCHY_MODEL.equals(location);
    if (!punchy && !HAND_MODEL.equals(location)) return source;
    return () -> repair(source, punchy);
  }

  private static InputStream repair(IoSupplier<InputStream> source, boolean punchy)
      throws IOException {
    byte[] bytes;
    try (var stream = source.get()) {
      bytes = stream.readAllBytes();
    }
    if (!(punchy ? PUNCHY_HASH : MODEL_HASH).equals(hash(bytes))) {
      return new ByteArrayInputStream(bytes);
    }
    var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    // Recess the three inward faces by 0.03 model units. The outward faces set the silhouette.
    if (punchy) {
      var inner =
          root.getAsJsonArray("minecraft:geometry")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("bones")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("cubes")
              .get(8)
              .getAsJsonObject();
      var origin = inner.getAsJsonArray("origin");
      origin.set(0, new JsonPrimitive(1.145));
      origin.set(2, new JsonPrimitive(-0.53));
      var size = inner.getAsJsonArray("size");
      size.set(0, new JsonPrimitive(-1.04));
      size.set(2, new JsonPrimitive(-1.87));
    } else {
      var inner = root.getAsJsonArray("elements").get(8).getAsJsonObject();
      var from = inner.getAsJsonArray("from");
      from.set(0, new JsonPrimitive(5.896));
      from.set(2, new JsonPrimitive(11.472));
      inner.getAsJsonArray("to").set(0, new JsonPrimitive(4.854));
    }
    return new ByteArrayInputStream(root.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Java runtime has no SHA-256", exception);
    }
  }
}
