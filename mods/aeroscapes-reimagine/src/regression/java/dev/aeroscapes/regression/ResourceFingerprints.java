package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Content-free comparisons shared by old/new artifact and runtime repair fixtures. */
final class ResourceFingerprints {
  private ResourceFingerprints() {}

  static String json(JsonElement value) {
    return sha256(canonicalJson(value).toString().getBytes(StandardCharsets.UTF_8));
  }

  static String nbt(Tag data) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var output = new DataOutputStream(bytes)) {
      writeCanonicalNbt(data, output);
    }
    return sha256(bytes.toByteArray());
  }

  static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JVM has no SHA-256", exception);
    }
  }

  private static JsonElement canonicalJson(JsonElement value) {
    if (value.isJsonObject()) {
      JsonObject result = new JsonObject();
      for (String key : new TreeSet<>(value.getAsJsonObject().keySet()))
        result.add(key, canonicalJson(value.getAsJsonObject().get(key)));
      return result;
    }
    if (value.isJsonArray()) {
      JsonArray result = new JsonArray();
      for (JsonElement child : value.getAsJsonArray()) result.add(canonicalJson(child));
      return result;
    }
    return value;
  }

  /** Include every tag type and list order; compound key order is not typed NBT content. */
  private static void writeCanonicalNbt(Tag data, DataOutputStream output) throws IOException {
    output.writeByte(data.getId());
    if (data instanceof CompoundTag compound) {
      output.writeInt(compound.size());
      for (String key : new TreeSet<>(compound.getAllKeys())) {
        output.writeUTF(key);
        writeCanonicalNbt(Objects.requireNonNull(compound.get(key)), output);
      }
    } else if (data instanceof ListTag list) {
      output.writeByte(list.getElementType());
      output.writeInt(list.size());
      for (Tag entry : list) writeCanonicalNbt(entry, output);
    } else {
      data.write(output);
    }
  }
}
