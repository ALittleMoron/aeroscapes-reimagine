package dev.aeroscapes.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.sky.client.WhaleBirds;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy;
import dev.aeroscapes.sky.fauna.CloudJellyContact;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
    jellyContact();
    jellySwimming();
    birds();
    whaleFall();
    journal(root);
    geyser(root);
    auroraOwnership(root);
    System.out.println("Sky life contracts passed");
  }

  private static void jellySwimming() {
    AABB probe = AABB.ofSize(Vec3.ZERO, .04, .04, .04);
    for (var form : CloudJellyAnatomy.Form.values())
      for (float diameter : new float[] {1, 4, 8, 16}) {
        double unit = diameter / 16;
        var shape = CloudJellyAnatomy.of(form);
        Vec3 crown = new Vec3(0, shape.height() * unit, 0);
        for (int heading : new int[] {-90, 90}) {
          boolean stroke = false, reopen = false, glide = false, sideways = false;
          for (int t = 0; t < 2100; t++) {
            var pose = CloudJellyMotion.sample(form, diameter, 11593, t, heading, false);
            double phase = pose.phase();
            if (phase > .06 && phase < .18) {
              require(
                  pose.thrust > 0
                      && pose.velocity(Vec3.ZERO)
                              .subtract(pose.current.scale(1 - .91 * .94))
                              .normalize()
                              .dot(pose.basis.y)
                          > .99999,
                  form + " swimming impulse points toward the crown");
              stroke = true;
            } else if (phase > .24 && phase < .6) reopen |= pose.thrust == 0;
            else if (phase > .65) glide |= pose.thrust == 0;
            if (!sideways && pose.basis.y.y < .22 && pose.thrust > 0) {
              var posed = new CloudJellyContact(pose, diameter);
              for (int i = 0; i < shape.strands().size(); i++) {
                var tip = pose.segment(i, shape.strands().get(i).segments().size() - 1).getCenter();
                Vec3 skin = crown.add(pose.basis.apply(new Vec3(tip.x, -tip.y, tip.z).scale(unit)));
                require(
                    posed.touches(skin, skin, probe, (time, root, point, victim) -> true),
                    form + " horizontal moving strand " + i);
              }
              Vec3 empty =
                  crown
                      .add(pose.basis.apply(new Vec3(1, -shape.height(), 0).scale(unit)))
                      .add(pose.basis.z.scale(diameter));
              require(
                  !posed.touches(empty, empty, probe, (time, root, point, victim) -> true),
                  form + " clearance around a sideways giant");
              sideways = true;
            }
            if (stroke && reopen && glide && sideways) break;
          }
          require(
              stroke && reopen && glide && sideways,
              form + " naturally crosses all swim stages and approaches horizontal");
        }
      }
  }

  private static void jellyContact() {
    var probe = AABB.ofSize(Vec3.ZERO, 0.05, 0.05, 0.05);
    var body = AABB.ofSize(Vec3.ZERO, 0.6, 1.8, 0.6);
    CloudJellyContact.Exposure open = (time, root, skin, victim) -> true;
    for (var form : CloudJellyAnatomy.Form.values()) {
      var shape = CloudJellyAnatomy.of(form);
      var giant = new CloudJellyContact(form, CloudJellyAnatomy.SIZE_UNITS);
      boolean clearUnderShortTip = false;
      for (var strand : shape.strands()) {
        var first = strand.segments().getFirst();
        Vec3 attachment = new Vec3(first.x(), shape.height() - first.top(), first.z());
        CloudJellyContact.Exposure thisStrand =
            (t, root, skin, victim) -> root.distanceToSqr(attachment) < 1.0e-9;
        for (var segment : strand.segments()) {
          Vec3 edge =
              new Vec3(
                  segment.x() + segment.width() * 0.49,
                  shape.height() - (segment.top() + segment.bottom()) / 2,
                  segment.z() - segment.depth() * 0.49);
          require(giant.touches(edge, edge, probe, thisStrand), form + " visible strand section");
        }
        var last = strand.segments().getLast();
        Vec3 tip = new Vec3(last.x(), shape.height() - last.bottom() + 0.02, last.z());
        require(giant.touches(tip, tip, body, thisStrand), form + " tip can sting");
        Vec3 below = tip.add(0, -1.5, 0);
        require(!giant.touches(below, below, body, thisStrand), form + " tip ends at its skin");
        if (below.y > 0 && !giant.touches(below, below, body, open)) clearUnderShortTip = true;
      }
      require(clearUnderShortTip, form + " room beneath a short strand inside the broad box");

      for (var shell : shape.bell()) {
        Vec3 rim =
            new Vec3(
                (shell.minX() + shell.maxX()) / 2,
                shape.height() - (shell.top() + shell.bottom()) / 2,
                (shell.minZ() + shell.maxZ()) / 2);
        require(giant.touches(rim, rim, probe, open), form + " tangible bell section");
        require(
            !giant.touches(rim, rim, body, (t, root, skin, victim) -> false),
            form + " covered bell");
      }
      Vec3 corner = new Vec3(7.7, shape.height() - 0.5, 7.7);
      require(!giant.touches(corner, corner, body, open), form + " open upper bell corner");

      var small = new CloudJellyContact(form, 1);
      var crown = shape.bell().getFirst();
      Vec3 core =
          new Vec3(
                  (crown.minX() + crown.maxX()) / 2,
                  shape.height() - (crown.top() + crown.bottom()) / 2,
                  (crown.minZ() + crown.maxZ()) / 2)
              .scale(1.0 / CloudJellyAnatomy.SIZE_UNITS);
      Vec3 from = core.add(-12, 0, 0);
      Vec3 to = core.add(12, 0, 0);
      require(
          small.touches(from, to, body, (t, root, skin, victim) -> Math.abs(t - 0.5) < 0.001),
          form + " fast contact opens in the middle of a covered crossing");
      require(
          !small.touches(core.add(-4, 0, .9), core.add(.9, 0, 4), body, open),
          form + " diagonal flyby misses despite overlapping broad boxes");
    }
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
