package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.construction.ConstructionKeys;
import dev.aeroscapes.construction.ConstructionMessages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.LoadingModList;
import team.creative.creativecore.client.CreativeCoreClient;
import team.creative.creativecore.client.render.model.layer.CreativeModelLayerLoader;
import team.creative.creativecore.client.render.model.preview.CreativeUnbakedPreviewItemLayer;

/** Actual native preview parsing and hand geometry, without starting a Minecraft client. */
public final class ConstructionEquipmentModelTest {
  private static final List<String> ITEMS =
      List.of("hammer", "chisel", "saw", "wrench", "paint_brush", "glove", "measure_tape", "bag");

  private ConstructionEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) throw new IllegalArgumentException("Expected repository root");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve(
                "pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets/littletiles/models/item");
    for (String item : ITEMS) {
      JsonObject root = read(assets.resolve(item + ".json"));
      require(
          root.getAsJsonObject("base").equals(HeldItemModels.nativeModel("littletiles:" + item)),
          "Native non-hand model and all layers retained: " + item);
      require(root.getAsJsonObject("perspectives").size() == 4, "Only four hand contexts: " + item);
      for (String context :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand"))
        require(
            root.getAsJsonObject("perspectives").has(context), "Both cameras and hands: " + item);
      JsonObject held = read(assets.resolve("equipment/held/" + item + ".json"));
      var faces = VanillaEquipmentModelTest.bakeCuboids(held);
      require(faces.size() >= 12, "Useful authored volume bakes into actual faces: " + item);
    }
    JsonObject brush = read(assets.resolve("equipment/held/paint_brush.json"));
    require(
        VanillaEquipmentModelTest.bakeCuboids(brush).stream().anyMatch(face -> face.tint() == 1),
        "Native selected-paint tint reaches held brush faces");
    previews(assets);
    commandMessages();
    keyDefaults();
    ConstructionMixinContract.verify();
    System.out.println(
        "Construction equipment: eight native roots and held meshes; GUI preview dispatch and paint tint passed");
  }

  private static void keyDefaults() {
    require(ConstructionKeys.defaultKey("key.little.mark", 77) == 59, "Mark uses semicolon");
    require(ConstructionKeys.defaultKey("key.little.mirror", 71) == 39, "Mirror uses apostrophe");
    require(
        ConstructionKeys.defaultKey("key.little.config.item", 67) == 92,
        "Configure uses backslash");
    require(
        ConstructionKeys.defaultKey("key.little.config_secondary.item", 67) == 92,
        "Secondary configuration retains the same physical key with native Shift");
    require(
        ConstructionKeys.defaultKey("key.little.building_mode", 66) == 61, "Building uses equals");
    require(ConstructionKeys.defaultKey("key.little.up", 265) == 265, "Arrow keys remain native");
    require(ConstructionKeys.defaultKey("key.little.undo", 90) == 90, "Undo remains native");
    require(ConstructionKeys.defaultKey("key.little.redo", 89) == 89, "Redo remains native");
  }

  private static void commandMessages() {
    var scanned =
        ConstructionMessages.literal("Scanned 2 levels, 3 chunks, 4 blocks, 5 tiles").getContents();
    require(
        scanned instanceof TranslatableContents, "Server feedback remains a localized component");
    var contents = (TranslatableContents) scanned;
    require(
        contents.getKey().equals("aeroscapes.construction.command.scan")
            && java.util.Arrays.equals(contents.getArgs(), new Object[] {"2", "3", "4", "5"}),
        "Native command counters reach the receiving player's translation independently");
    require(
        ConstructionMessages.literal("Owner's own building name")
            .getString()
            .equals("Owner's own building name"),
        "Unrecognized source text remains literal");
  }

  private static void previews(Path assets) throws IOException {
    CreativeModelLayerLoader.REGISTRY.register(
        "creativecore:preview", CreativeUnbakedPreviewItemLayer.class);
    for (String item : List.of("chisel", "glove")) {
      AtomicInteger calls = new AtomicInteger();
      CreativeCoreClient.PREVIEW_MODEL_TYPES.register(
          ResourceLocation.fromNamespaceAndPath("littletiles", item),
          stack -> {
            calls.incrementAndGet();
            return stack;
          });
      JsonObject base = read(assets.resolve(item + ".json")).getAsJsonObject("base");
      var parsed = new CreativeModelLayerLoader().read(base, null);
      require(parsed.layers().size() == 2, "Native sprite and dynamic preview parse: " + item);
      require(
          parsed.layers().get(1) instanceof CreativeUnbakedPreviewItemLayer,
          "Native preview loader retained: " + item);
      var preview = parsed.layers().get(1).bake(null, null, null);
      for (var context : ItemDisplayContext.values()) {
        if (context == ItemDisplayContext.GUI) continue;
        preview.render(null, ItemStack.EMPTY, context, false, null, null, 0, 0);
      }
      require(calls.get() == 0, "Native preview does not render outside GUI: " + item);
      preview.render(null, ItemStack.EMPTY, ItemDisplayContext.GUI, false, null, null, 0, 0);
      require(calls.get() == 1, "GUI still resolves the dynamic native preview: " + item);
    }
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
