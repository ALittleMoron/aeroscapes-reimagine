package dev.aeroscapes.aquatic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.ExtraFaceData;

/** Exercises parsed provider geometry without starting a Minecraft client. */
public final class RefinedRodCastCleanupTest {
  private RefinedRodCastCleanupTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1)
      throw new IllegalArgumentException("Expected the pinned Refined Tools 3.0 archive");
    try (ZipFile archive = new ZipFile(Path.of(arguments[0]).toFile())) {
      for (String suffix : List.of("", "_left", "_fp", "_fp_left")) {
        String path = "item/rods/held_fishing_cast" + suffix;
        ResourceLocation location = ResourceLocation.withDefaultNamespace(path);
        List<BlockElement> elements = read(archive, path);
        List<BlockElement> retained = List.copyOf(elements.subList(5, elements.size()));
        String retainedShape = RefinedRodCastCleanup.fingerprint(retained);
        require(
            RefinedRodCastCleanup.removeDecoration(location, elements),
            "Pinned cast geometry must remove its stowed tackle: " + suffix);
        require(elements.size() == 13, "Only five decorative elements may be removed");
        for (int index = 0; index < retained.size(); index++)
          require(
              elements.get(index) == retained.get(index), "Retain the original native elements");
        require(
            retainedShape.equals(RefinedRodCastCleanup.fingerprint(elements)),
            "All retained geometry fields must remain identical");
        require(
            !RefinedRodCastCleanup.removeDecoration(location, elements),
            "Repeated loading must not remove more geometry");
        unchanged(ResourceLocation.fromNamespaceAndPath("another_pack", path), read(archive, path));
        unchanged(
            ResourceLocation.withDefaultNamespace("item/rods/held_fishing_rod" + suffix),
            read(archive, "item/rods/held_fishing_rod" + suffix));

        List<BlockElement> changed = read(archive, path);
        changed.getFirst().from.x += 0.0001F;
        unchanged(location, changed);
        changed = read(archive, path);
        changed.getLast().faces.get(Direction.NORTH).uv().uvs[0] += 0.125F;
        unchanged(location, changed);
        changed = read(archive, path);
        changed.getLast().setFaceData(new ExtraFaceData(0, 0, 0, true));
        unchanged(location, changed);
      }
    }
    System.out.println(
        "Refined cast cleanup: four parsed native models, idle/override guards and idempotence passed");
  }

  private static void unchanged(ResourceLocation location, List<BlockElement> elements) {
    List<BlockElement> before = List.copyOf(elements);
    String fingerprint = RefinedRodCastCleanup.fingerprint(elements);
    require(
        !RefinedRodCastCleanup.removeDecoration(location, elements), "Leave other models native");
    require(before.equals(elements), "Unknown geometry must retain all original elements");
    require(
        fingerprint.equals(RefinedRodCastCleanup.fingerprint(elements)),
        "Unknown geometry fields must remain unchanged");
  }

  private static List<BlockElement> read(ZipFile archive, String path) throws IOException {
    var entry = archive.getEntry("assets/minecraft/models/" + path + ".json");
    if (entry == null) throw new IOException("Missing provider model " + path);
    JsonObject model;
    try (var input = archive.getInputStream(entry);
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      model = JsonParser.parseReader(reader).getAsJsonObject();
    }
    Gson parser =
        new GsonBuilder()
            .registerTypeAdapter(BlockElement.class, new BlockElement.Deserializer())
            .registerTypeAdapter(BlockElementFace.class, new BlockElementFace.Deserializer())
            .registerTypeAdapter(BlockFaceUV.class, new BlockFaceUV.Deserializer())
            .create();
    List<BlockElement> elements = new ArrayList<>();
    for (var value : model.getAsJsonArray("elements")) {
      JsonObject source = value.getAsJsonObject();
      JsonObject normalized = source.deepCopy();
      // Puzzle permits the provider's non-vanilla angles at runtime; restore those after native
      // parsing.
      if (normalized.has("rotation"))
        normalized.getAsJsonObject("rotation").addProperty("angle", 0);
      BlockElement parsed = parser.fromJson(normalized, BlockElement.class);
      if (parsed.rotation != null) {
        var rotation = parsed.rotation;
        parsed =
            new BlockElement(
                parsed.from,
                parsed.to,
                parsed.faces,
                new BlockElementRotation(
                    rotation.origin(),
                    rotation.axis(),
                    source.getAsJsonObject("rotation").get("angle").getAsFloat(),
                    rotation.rescale()),
                parsed.shade,
                parsed.getFaceData());
      }
      elements.add(parsed);
    }
    return elements;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
