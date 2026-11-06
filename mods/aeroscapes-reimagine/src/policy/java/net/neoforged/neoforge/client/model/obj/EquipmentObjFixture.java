package net.neoforged.neoforge.client.model.obj;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Replaces only ObjLoader's client-singleton resource I/O; all parsing/baking stays native. */
public final class EquipmentObjFixture {
  private EquipmentObjFixture() {}

  public static ObjModel parse(
      String source, InputStream materialInput, ObjModel.ModelSettings settings)
      throws IOException {
    if (settings.mtlOverride() != null
        || !source.startsWith("mtllib equipment.mtl\n")
        || source.indexOf("mtllib", "mtllib equipment.mtl\n".length()) >= 0)
      throw new IOException("Expected the single generated equipment material library");
    ObjMaterialLibrary library;
    try (ObjTokenizer tokenizer = new ObjTokenizer(materialInput)) {
      library = new ObjMaterialLibrary(tokenizer);
    }
    var previous = new HashMap<>(ObjMaterialLibrary.EMPTY.materials);
    try {
      ObjMaterialLibrary.EMPTY.materials.clear();
      ObjMaterialLibrary.EMPTY.materials.putAll(library.materials);
      try (ObjTokenizer tokenizer =
          new ObjTokenizer(
              new ByteArrayInputStream(
                  source
                      .substring("mtllib equipment.mtl\n".length())
                      .getBytes(StandardCharsets.UTF_8)))) {
        return ObjModel.parse(tokenizer, settings);
      }
    } finally {
      ObjMaterialLibrary.EMPTY.materials.clear();
      ObjMaterialLibrary.EMPTY.materials.putAll(previous);
    }
  }
}
