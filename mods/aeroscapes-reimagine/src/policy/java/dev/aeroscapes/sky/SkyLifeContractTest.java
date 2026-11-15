package dev.aeroscapes.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.sky.client.WhaleBirds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * Checks the sky-liveliness contracts without Minecraft: unchanged average whale chance, stable
 * bird share, a bounded whale-fall layout with a modest whale-bone yield, and journal data that
 * covers every whale form and excludes previews.
 */
public final class SkyLifeContractTest {
  private SkyLifeContractTest() {}

  public static void main(String[] arguments) throws IOException {
    Path root = Path.of(arguments[0]);
    whaleWaters();
    regionalChance();
    birds();
    whaleFall();
    journal(root);
    geyser(root);
    auroraOwnership(root);
    System.out.println("Sky life contracts passed");
  }

  private static void whaleWaters() {
    require(
        Math.abs(
                WhaleWaters.HOTSPOT_SHARE * WhaleWaters.HOTSPOT_FACTOR
                    + (1.0 - WhaleWaters.HOTSPOT_SHARE) * WhaleWaters.ORDINARY_FACTOR
                    - 1.0)
            < 1.0e-12,
        "Whale waters keep the configured mean chance");
    for (long seed : new long[] {0L, 1L, -8315743817914385108L, 42L}) {
      int hot = 0;
      int cells = 0;
      double sum = 0.0;
      for (int cellX = -60; cellX < 60; cellX++) {
        for (int cellZ = -60; cellZ < 60; cellZ++) {
          double x = cellX * (double) WhaleWaters.CELL_BLOCKS + 17.0;
          double z = cellZ * (double) WhaleWaters.CELL_BLOCKS + 911.0;
          if (WhaleWaters.hotspot(seed, x, z)) hot++;
          sum += WhaleWaters.factor(seed, x, z);
          cells++;
          require(
              WhaleWaters.hotspot(seed, x, z)
                  == WhaleWaters.hotspot(seed, x + WhaleWaters.CELL_BLOCKS - 20.0, z - 900.0),
              "One cell has one character");
        }
      }
      double share = hot / (double) cells;
      require(Math.abs(share - WhaleWaters.HOTSPOT_SHARE) < 0.02, "Hotspot share " + share);
      require(Math.abs(sum / cells - 1.0) < 0.03, "Regional mean factor " + sum / cells);
    }
  }

  private static void regionalChance() {
    for (double mean = 0.05; mean < 1.0; mean += 0.05) {
      double average =
          WhaleWaters.HOTSPOT_SHARE * WhaleWaters.chance(mean, true)
              + (1.0 - WhaleWaters.HOTSPOT_SHARE) * WhaleWaters.chance(mean, false);
      require(Math.abs(average - mean) < 1.0e-9, "Regional chance keeps mean " + mean);
      require(
          WhaleWaters.chance(mean, true) <= 1.0 && WhaleWaters.chance(mean, false) >= 0.0,
          "Regional chances stay probabilities");
    }
  }

  private static void birds() {
    var random = RandomSource.create(9);
    int accompanied = 0;
    int samples = 20_000;
    for (int index = 0; index < samples; index++) {
      int count = WhaleBirds.birds(random.nextLong(), random.nextLong());
      require(
          count == 0 || count >= WhaleBirds.MINIMUM_BIRDS && count <= WhaleBirds.MAXIMUM_BIRDS,
          "Bird count stays bounded");
      if (count > 0) accompanied++;
    }
    double share = accompanied / (double) samples;
    require(
        Math.abs(share - WhaleBirds.ACCOMPANIED_PERCENT / 100.0) < 0.02,
        "Accompanied whale share " + share);
  }

  private static void whaleFall() {
    for (long seed = 0; seed < 400; seed++) {
      var random = RandomSource.create(seed);
      List<SkyWhaleFallShape.Bone> bones =
          SkyWhaleFallShape.withMaterials(SkyWhaleFallShape.create(random), random);
      int intact = 0;
      int cracked = 0;
      Set<String> positions = new HashSet<>();
      for (var bone : bones) {
        require(
            Math.abs(bone.x()) <= SkyWhaleFallShape.MAXIMUM_EXTENT
                && Math.abs(bone.z()) <= SkyWhaleFallShape.MAXIMUM_EXTENT
                && bone.y() >= 0
                && bone.y() <= 5,
            "Whale fall stays inside the neighbouring chunks");
        require(positions.add(bone.x() + "," + bone.y() + "," + bone.z()), "No doubled bone");
        require(bone.grounded() == (bone.y() == 0), "Only ground-level bones follow terrain");
        switch (bone.material()) {
          case WHALE_BONE -> intact++;
          case CRACKED_WHALE_BONE -> cracked++;
          case BONE -> {}
        }
      }
      require(
          intact >= SkyWhaleFallShape.MINIMUM_INTACT_BONE
              && intact <= SkyWhaleFallShape.MAXIMUM_INTACT_BONE,
          "Intact whale bone stays modest: " + intact);
      require(
          cracked >= SkyWhaleFallShape.MINIMUM_CRACKED_BONE
              && cracked <= SkyWhaleFallShape.MAXIMUM_CRACKED_BONE,
          "Cracked whale bone stays modest: " + cracked);
      require(bones.size() >= 120 && bones.size() <= 260, "Readable skeleton: " + bones.size());
    }
    // The chunk-centred origin plus the extent never reaches beyond direct neighbours.
    for (var facing : Direction.Plane.HORIZONTAL) {
      for (int[] corner :
          new int[][] {
            {-SkyWhaleFallShape.MAXIMUM_EXTENT, -SkyWhaleFallShape.MAXIMUM_EXTENT},
            {SkyWhaleFallShape.MAXIMUM_EXTENT, SkyWhaleFallShape.MAXIMUM_EXTENT}
          }) {
        for (int jitter :
            new int[] {-SkyWhaleFallFeature.CENTRE_JITTER, SkyWhaleFallFeature.CENTRE_JITTER}) {
          var position =
              SkyWhaleFallShape.rotate(8 + jitter, 8 + jitter, corner[0], corner[1], facing);
          require(
              position.getX() >= -16
                  && position.getX() < 32
                  && position.getZ() >= -16
                  && position.getZ() < 32,
              "Whale fall writes stay in the decoration region");
        }
      }
    }
  }

  private static void journal(Path root) throws IOException {
    Path directory =
        root.resolve(
            "mods/aeroscapes-reimagine/src/main/resources/data/aeroscapes_reimagine/advancement/sky_journal");
    JsonObject all = read(directory.resolve("all_whales.json"));
    Set<String> forms = all.getAsJsonObject("criteria").keySet();
    Set<String> expected = new HashSet<>();
    for (var variety : WhaleVariety.values()) expected.add(variety.id());
    require(forms.equals(expected), "The challenge covers every whale form");
    for (var variety : WhaleVariety.values()) {
      JsonObject advancement = read(directory.resolve("whale_" + variety.id() + ".json"));
      JsonArray conditions =
          advancement
              .getAsJsonObject("criteria")
              .getAsJsonObject("whale")
              .getAsJsonObject("conditions")
              .getAsJsonArray("entities_in_frame")
              .get(0)
              .getAsJsonArray();
      String text = conditions.toString();
      require(
          text.contains("AeroscapesWhaleVariety:\\\"" + variety.id() + "\\\""),
          "Journal entry reads the saved form: " + variety.id());
      require(text.contains(WhalePreviewCommands.PREVIEW_TAG), "Previews are excluded");
    }
    Path languages =
        root.resolve(
            "mods/aeroscapes-reimagine/src/main/resources/assets/aeroscapes_reimagine/lang");
    JsonObject english = read(languages.resolve("en_us.json"));
    JsonObject russian = read(languages.resolve("ru_ru.json"));
    try (var files = Files.list(directory)) {
      for (Path file : files.toList()) {
        String name = file.getFileName().toString().replace(".json", "");
        for (String part : List.of("title", "description")) {
          String key = "advancements.aeroscapes_reimagine.sky_journal." + name + "." + part;
          require(english.has(key) && russian.has(key), "Journal text in both languages: " + key);
        }
      }
    }
  }

  private static void geyser(Path root) throws IOException {
    JsonObject source =
        read(
            root.resolve(
                "mods/aeroscapes-reimagine/src/main/resources/data/aeroscapes_reimagine/paraglider/wind_sources/geyser.json"));
    require(
        source
            .getAsJsonObject("conditions")
            .getAsJsonObject("properties")
            .get("active")
            .getAsString()
            .equals("true"),
        "Geyser lift only while the geyser is active");
    require(source.get("height").getAsInt() < 10, "Geyser lift is shorter than a campfire's");
  }

  /**
   * Every shipped shader pack is classified: one with its own aurora hides the aurora mod, any
   * other keeps it. A new pack needs a deliberate decision here.
   */
  private static void auroraOwnership(Path root) throws IOException {
    var withOwnAurora = Set.of("ComplementaryReimagined_r5.9.3.zip");
    var withoutAurora = Set.of("MakeUp-UltraFast-9.6.zip");
    try (var files = Files.list(root.resolve("pack/shaderpacks"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".pw.toml")).toList()) {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        var match = java.util.regex.Pattern.compile("filename = \"([^\"]+)\"").matcher(text);
        require(match.find(), "Shader pin names its file: " + file);
        String name = match.group(1);
        require(
            withOwnAurora.contains(name) || withoutAurora.contains(name),
            "Classify the aurora of shader pack " + name);
        require(
            dev.aeroscapes.atmosphere.AuroraOwnership.drawsOwnAurora(name)
                == withOwnAurora.contains(name),
            "Aurora ownership for " + name);
      }
    }
    require(!dev.aeroscapes.atmosphere.AuroraOwnership.drawsOwnAurora(null), "No pack, no aurora");
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
