package dev.aeroscapes.aquatic;

import static dev.aeroscapes.atmosphere.EquipmentArtwork.PROFILE_GRID;
import static dev.aeroscapes.atmosphere.EquipmentArtwork.PROFILE_MAX;
import static dev.aeroscapes.atmosphere.EquipmentArtwork.PROFILE_MIN;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/** Physical components and painted material faces, separate from the native inventory artwork. */
final class EquipmentSculpture {
  private static final List<String> MATERIALS =
      dev.aeroscapes.atmosphere.EquipmentArtwork.MATERIALS;
  private final String id;
  private final EquipmentStyle profile;
  private final JsonObject nativeDisplay;
  private final JsonObject model = new JsonObject();
  private final JsonArray parts = new JsonArray();
  private int serial;

  static boolean readable(EquipmentStyle style) {
    return switch (style) {
      case DIAL,
          DEPTH_METER,
          MONSTROMETER,
          WATCH,
          ENDER_EYE,
          CLIMATE_GAUGE,
          MULTIMETER,
          TERMINAL,
          FISH_FINDER,
          PROJECTOR,
          BOOK,
          CLIPBOARD,
          SCHEMATIC,
          TABLET,
          SPECTRE_FLARE ->
          true;
      default -> false;
    };
  }

  private EquipmentSculpture(String id, EquipmentStyle profile, JsonObject original) {
    this.id = id;
    this.profile = profile;
    nativeDisplay = original.getAsJsonObject("display");
    model.addProperty("parent", "minecraft:block/block");
    model.addProperty("ambientocclusion", false);
    var textures = original.getAsJsonObject("textures").deepCopy();
    textures.addProperty("particle", "#layer0");
    textures.addProperty("surface", id.replace(":", ":item/equipment/painted/"));
    model.add("textures", textures);
    model.add("elements", parts);
  }

  static JsonObject model(String id, EquipmentStyle profile, JsonObject original)
      throws IOException {
    EquipmentSculpture shape = new EquipmentSculpture(id, profile, original);
    shape.build();
    shape.model.add("display", shape.display());
    return shape.model;
  }

  static JsonObject transforms(String id, EquipmentStyle profile, JsonObject original) {
    return new EquipmentSculpture(id, profile, original).display();
  }

  static byte[] texture(String id, EquipmentStyle profile) throws IOException {
    return EquipmentModels.png(
        dev.aeroscapes.atmosphere.EquipmentArtwork.paint(
            NativeEquipmentModels.image(sourceTexture(id)), profile));
  }

  static JsonObject spriteSource(String id, EquipmentStyle profile) throws IOException {
    JsonObject source = new JsonObject();
    source.addProperty("type", "aeroscapes_reimagine:equipment_paint");
    source.addProperty("sprite", id.replace(":", ":item/equipment/painted/"));
    source.addProperty("resource", sourceTexture(id));
    source.addProperty("profile", profile.getSerializedName());
    return source;
  }

  private static String sourceTexture(String id) throws IOException {
    var textures = NativeEquipmentModels.inherited(id).getAsJsonObject("textures");
    String reference = textures.get("layer0").getAsString();
    while (reference.startsWith("#"))
      reference = textures.get(reference.substring(1)).getAsString();
    return reference.contains(":") ? reference : "minecraft:" + reference;
  }

  private void build() throws IOException {
    switch (profile) {
      case BELL -> bell();
      case WATERING_CAN -> wateringCan();
      case CAPTURE_NET -> net();
      case COIL -> coil();
      case KNIFE -> knife();
      case DIAL, DEPTH_METER, MONSTROMETER, WATCH, ENDER_EYE -> dial();
      case CLIMATE_GAUGE -> thermometer();
      case MULTIMETER, TERMINAL, FISH_FINDER, PROJECTOR -> instrument();
      case BOOK, CLIPBOARD, SCHEMATIC, TABLET, ALTIMETER -> document();
      case NEEDLES -> needles();
      case GLUE -> glue();
      case HORN -> horn();
      case WHISTLE -> whistle();
      case LEAD, CASTING_NET -> rope();
      case BUBBLE, KEY -> smallLoop();
      case SPYGLASS -> spyglass();
      case GLOW_FLARE, SPECTRE_FLARE -> flare();
      case PAINT_KIT -> paintKit();
      case CRYSTAL -> crystal();
      case LUNCHBOX -> lunchbox();
      case CORAL, SHELL -> aquaticTool();
      case FISH -> nativeCutout(0, 0);
      case BRUSH -> brush();
      case HAMMER, SPADE, SAW, CHISEL, WRENCH -> workshop();
      case BOOMERANG -> boomerang();
      case DART -> dart();
      case BOMB -> bomb();
      case SLUDGE -> sludge();
      case BOW, SLINGSHOT -> ranged();
      case GRAPPLE -> grapple();
      case CLAW -> claw();
      case PAPER -> paper();
      case ABACUS -> abacus();
      case QUILL -> nativeCutout(0, 0);
    }
  }

  private void grip(double top) {
    cylinder("grip", 8, 8, 1.05, 1, top);
  }

  private void knife() throws IOException {
    grip(6.7);
    boolean dagger = id.startsWith("wetland_whimsy:");
    nativeCutout(10, 0);
    if (dagger) {
      hoop("body", 8, 1.1, 8, .8, .2, .5);
      box("grip", 7, 6, 7.3, 9, 6.7, 8.7);
    }
  }

  private void aquaticTool() throws IOException {
    grip(9.2);
    int head = parts.size();
    nativeCutout(12, 0);
    if (id.endsWith("_hoe")) {
      double offset = profile == EquipmentStyle.CORAL ? Math.sqrt(.5) : Math.sqrt(2);
      for (int index = head; index < parts.size(); index++)
        for (String bound : List.of("from", "to")) {
          JsonArray point = parts.get(index).getAsJsonObject().getAsJsonArray(bound);
          point.set(0, new com.google.gson.JsonPrimitive(point.get(0).getAsDouble() - offset));
        }
    }
    if (profile == EquipmentStyle.CORAL) box("gold", 7.1, 8.5, 7.1, 8.9, 9.5, 8.9);
  }

  private void brush() throws IOException {
    grip(6.5);
    box("band", 6.9, 6, 7, 9.1, 7.4, 9);
    nativeCutout(10.5, 0);
  }

  private void workshop() {
    grip(profile == EquipmentStyle.HAMMER ? 11.7 : 9);
    switch (profile) {
      case HAMMER -> {
        bevelBox("body", 3.4, 11.5, 6.2, 12.2, 15, 9.8, .65);
        bevelBox("edge", 2.4, 11.8, 6, 4, 14.7, 10, .45);
        box("grip", 7.6, 14.8, 7.6, 8.4, 15.5, 8.4);
      }
      case SPADE -> {
        box("body", 7.4, 8.8, 7.5, 8.6, 11, 8.5);
        sphere("body", 8, 13.4, 8, 2.6, 3, .55);
        box("edge", 7.7, 11, 8.5, 8.3, 15.5, 8.7);
      }
      case SAW -> {
        hoop("grip", 8, 4.5, 8, 2.3, .7, 1.6);
        box("body", 5.5, 6.5, 7.7, 10.5, 12.5, 8.3);
        box("body", 6.2, 12.5, 7.7, 9.8, 15.5, 8.3);
        for (int tooth = 0; tooth < 5; tooth++)
          rotated(
              "edge",
              5,
              7.5 + tooth * 1.35,
              7.75,
              5.9,
              8.4 + tooth * 1.35,
              8.25,
              "z",
              45,
              5.5,
              8 + tooth * 1.35,
              8);
      }
      case CHISEL -> {
        cylinder("body", 8, 8, .7, 8.5, 13.4);
        bevelBox("edge", 7, 13.3, 7.7, 9, 15.4, 8.3, .15);
      }
      case WRENCH -> {
        box("body", 7.3, 8.5, 7.3, 8.7, 12, 8.7);
        if (id.equals("toms_storage:inventory_configurator")) {
          bevelBox("body", 5.8, 11, 7.2, 7.1, 15, 8.8, .3);
          bevelBox("body", 8.9, 11, 7.2, 10.2, 15, 8.8, .3);
          box("body", 7.1, 11, 7.2, 8.9, 12, 8.8);
          box("body", 7.1, 14, 7.2, 8.9, 15, 8.8);
        } else {
          rotated("body", 5.7, 11.1, 7.2, 7, 15, 8.8, "z", 22.5, 7, 12, 8);
          rotated("body", 9, 11.1, 7.2, 10.3, 15, 8.8, "z", -22.5, 9, 12, 8);
          box("body", 6.4, 11, 7.2, 9.6, 12.3, 8.8);
        }
      }
      default -> throw new IllegalStateException(profile.name());
    }
  }

  private void boomerang() {
    rotated("body", 2, 10.5, 7.2, 10, 12.2, 8.8, "z", -22.5, 9.5, 10.5, 8);
    rotated("body", 9, 3, 7.2, 10.7, 11.8, 8.8, "z", -22.5, 9.5, 10.5, 8);
    rotated("edge", 2, 12, 7.55, 9.8, 12.6, 8.45, "z", -22.5, 9.5, 10.5, 8);
    rotated("edge", 10.4, 3, 7.55, 11, 11.5, 8.45, "z", -22.5, 9.5, 10.5, 8);
    rotated("grip", 8.4, 9.5, 7.1, 10.5, 11.6, 8.9, "z", -22.5, 9.5, 10.5, 8);
  }

  private void dart() {
    for (int coil = 0; coil < 3; coil++) hoop("rope", 8, 5.2, 6.5 + coil, 2.3, .4, .7);
    cylinder("detail", 8, 8, .45, 7.1, 10);
    sphere("body", 8, 12.1, 8, 1.8, 2.8, 1.5);
    rotated("edge", 7.45, 13.8, 7.45, 8.55, 15.6, 8.55, "z", 45, 8, 14.2, 8);
  }

  private void bomb() {
    sphere("body", 8, 7, 8, 4.7, 4.7, 4.7);
    cylinder("dark", 8, 8, 1.1, 11, 12);
    strand("rope", 8, 12, 8, 8, 14, 8, .45);
    strand("rope", 8, 14, 8, 10, 15, 8, .45);
    if (id.contains("palladium")) {
      for (int x : new int[] {5, 10})
        for (int y : new int[] {4, 9}) box("detail", x, y, 12.1, x + 1, y + 1, 12.6);
      box("detail", 5.5, 6.2, 12, 10.5, 8, 12.6);
    } else if (id.startsWith("betterarcheology:")) {
      box("grip", 5.2, 5, 11.8, 6.5, 7.5, 12.4);
      box("grip", 9.5, 8.5, 11.5, 11, 9.5, 12.2);
    } else if (id.contains("spiky")) {
      for (int axis = 0; axis < 3; axis++)
        for (int side : new int[] {-1, 1})
          for (int step = 0; step < 4; step++) {
            double[] center = {8, 7, 8};
            center[axis] += side * (4.1 + step * .55);
            double radius = .7 - step * .16;
            box(
                "body",
                center[0] - radius,
                center[1] - radius,
                center[2] - radius,
                center[0] + radius,
                center[1] + radius,
                center[2] + radius);
          }
    }
    if (id.endsWith("_projectile")) {
      rotated("red", 9.4, 14.4, 7.4, 10.6, 15.6, 8.6, "z", 45, 10, 15, 8);
    }
  }

  private void sludge() {
    sphere("body", 8, 8.5, 8, 4.5, 4.8, 3.5);
    sphere("body", 5.7, 8.5, 7, 2.7, 3, 2.5);
    for (int drip = 0; drip < 4; drip++)
      cylinder("body", 5 + drip * 1.9, 7.5, .5, 1.4 + drip % 3, 5);
    box("edge", 6, 10.5, 10.1, 7.1, 11.2, 10.5);
  }

  private void ranged() {
    int draw = id.endsWith("_2") ? 3 : id.endsWith("_1") ? 2 : id.endsWith("_0") ? 1 : 0;
    if (profile == EquipmentStyle.SLINGSHOT) {
      grip(8.5);
      for (double y : new double[] {2.4, 3.4, 4.4}) ring("paper", 8, y, 8, 1.12, .3, .45);
      rotated("body", 5.2, 7.5, 7.3, 6.8, 14, 8.7, "z", 22.5, 7, 8, 8);
      rotated("body", 9.2, 7.5, 7.3, 10.8, 14, 8.7, "z", -22.5, 9, 8, 8);
      sphere("body", 8, 8.4, 8, 2, 1.2, 1.0);
      strand("rope", 4, 12.7, 8, 8, 11, 8 + draw, .2);
      strand("rope", 12, 12.7, 8, 8, 11, 8 + draw, .2);
      box("dark", 7.3, 10.5, 7.8 + draw, 8.7, 11.5, 8.3 + draw);
    } else {
      box("body", 7.2, 6, 7.2, 8.8, 10, 8.8);
      rotated("gold", 7.4, 9, 7.65, 8.6, 12.8, 8.35, "z", 22.5, 8, 9, 8);
      rotated("gold", 6.1, 12, 7.65, 7.3, 15.4, 8.35, "z", 22.5, 6.7, 12, 8);
      rotated("gold", 7.4, 3.2, 7.65, 8.6, 7, 8.35, "z", -22.5, 8, 7, 8);
      rotated("gold", 6.1, .6, 7.65, 7.3, 4, 8.35, "z", -22.5, 6.7, 4, 8);
      box("body", 5, 7.5, 7.3, 11, 8.5, 8.7);
      box("body", 7.5, 5, 7.3, 8.5, 11, 8.7);
      for (int side : new int[] {-1, 1})
        rotated("body", 7.5, 5, 7.4, 8.5, 11, 8.6, "z", side * 45, 8, 8, 8);
      double nock = 5.2 - draw * 1.1;
      strand("paper", 5.2, 14.5, 8, nock, 8, 8, .18);
      strand("paper", 5.2, 1.5, 8, nock, 8, 8, .18);
      if (draw > 0) {
        box("grip", nock, 7.85, 7.85, 14, 8.15, 8.15);
        rotated("detail", 13, 7.4, 7.75, 14.2, 8.6, 8.25, "z", 45, 13.6, 8, 8);
        box("paper", nock + .3, 7.5, 7.9, nock + 1.3, 8.5, 8.1);
      }
    }
  }

  private void grapple() {
    cylinder("body", 9.5, 8, .75, 4, 11);
    hoop("dark", 9.5, 2.8, 8, 1.3, .35, .9);
    rotated("body", 7.1, 10.5, 7.3, 9.9, 11.7, 8.7, "z", -22.5, 9.3, 11, 8);
    rotated("body", 4.8, 10.5, 7.3, 7.5, 11.7, 8.7, "z", 22.5, 7, 11, 8);
    rotated("body", 4.7, 8, 7.3, 5.9, 11.3, 8.7, "z", -22.5, 5.5, 10.5, 8);
    rotated("edge", 5.3, 7.8, 7.5, 7.7, 8.55, 8.5, "z", 22.5, 5.7, 8.2, 8);
  }

  private void claw() {
    sphere("body", 6, 7, 8, 2.7, 3.3, 2);
    sphere("red", 5.3, 6.7, 8.8, 2.4, 2.8, 1.45);
    boolean empty = id.endsWith("_empty");
    int state = id.contains("shooting2") ? 2 : id.contains("shooting1") ? 1 : 0;
    double gap = empty ? .4 : 1.4 + state * .55;
    for (int side : new int[] {-1, 1}) {
      double y = 7 + side * (gap + 1.1);
      sphere("body", 9, y, 8, 3.1, side < 0 ? 1.2 : 1.6, 1.45);
      rotated("body", 10.7, y - .45, 7.15, 13.3, y + .45, 8.85, "z", -side * 22.5, 11, y, 8);
      box("paper", 7.6, y - side * .65 - .2, 8.9, 10.8, y - side * .65 + .2, 9.15);
    }
    if (id.contains("shooting")) {
      double radius = .65 + state * .45;
      JsonObject bubble = new JsonObject();
      bubble.addProperty("name", "water_bubble");
      bubble.add("from", vector(11 - radius, 7 - radius, 8 - radius));
      bubble.add("to", vector(11 + radius, 7 + radius, 8 + radius));
      JsonObject faces = new JsonObject();
      for (String side : List.of("north", "south", "east", "west", "up", "down")) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", "spawn:entity/coastal_crab/bubble");
        face.add("uv", vector(4, 8, 8, 16));
        faces.add(side, face);
      }
      bubble.add("faces", faces);
      parts.add(bubble);
    }
  }

  private void paper() {
    box("body", 3.5, 3.5, 7.87, 12.5, 12.5, 8.13);
    rotated("body", 3.5, 11.4, 7.87, 6, 13.2, 8.13, "x", 22.5, 5, 11.5, 8);
  }

  private void abacus() {
    box("body", 2, 2, 7, 3, 14, 9);
    box("body", 13, 2, 7, 14, 14, 9);
    box("body", 2, 2, 7, 14, 3, 9);
    box("body", 2, 13, 7, 14, 14, 9);
    int count =
        id.endsWith("_unset")
            ? -1
            : id.contains("abacus_") ? Integer.parseInt(id.substring(id.lastIndexOf('_') + 1)) : 0;
    for (int row = 0; row < 2; row++) {
      box("grip", 3, 5 + row * 5, 7.8, 13, 5.3 + row * 5, 8.2);
      int moved = count < 0 ? 3 : row == 0 ? count % 7 : count / 7;
      for (int bead = 0; bead < 6; bead++) {
        double x = bead < 6 - moved ? 3.3 + bead * .85 : 12.5 - (5 - bead) * .85;
        bevelBox("paper", x, 4.2 + row * 5, 7.1, x + .65, 6.2 + row * 5, 8.9, .18);
      }
    }
  }

  private void nativeCutout(double minimumAlong, int layer) throws IOException {
    if (profile != EquipmentStyle.QUILL) {
      rectifiedSurface(minimumAlong);
      return;
    }
    var textures = model.getAsJsonObject("textures");
    String key = "layer" + layer;
    if (!textures.has(key)) return;
    String texture = textures.get(key).getAsString();
    while (texture.startsWith("#")) texture = textures.get(texture.substring(1)).getAsString();
    BufferedImage icon = NativeEquipmentModels.image(texture);
    int width = icon.getWidth(), height = Math.min(width, icon.getHeight());
    double unit = 16.0 / width;
    for (int py = 0; py < height; py++)
      for (int px = 0; px < width; px++) {
        if (icon.getRGB(px, py) >>> 24 == 0) continue;
        double x = (px + .5) * unit,
            y = (py + .5) * unit,
            along = (x + 16 - y) / Math.sqrt(2),
            across = (x + y - 16) / Math.sqrt(2);
        if (along < minimumAlong) continue;
        double thickness =
            switch (profile) {
              case FISH ->
                  along > 17 || along < 5 || across < -.8 || across > 3.1
                      ? .35
                      : .5 + 3.8 * Math.sqrt(Math.max(0, 1 - Math.pow((across - 1.2) / 2, 2)));
              case CORAL -> 1.8;
              case SHELL -> .8 + Math.max(0, 1.2 - Math.abs(across) * .15);
              case BRUSH -> 1.7;
              default -> profile == EquipmentStyle.QUILL ? .25 : .6;
            };
        JsonObject part = new JsonObject();
        part.addProperty("name", "painted_profile_" + serial++);
        part.add("from", vector(px * unit, 16 - (py + 1) * unit, 8 - thickness / 2 - layer * .015));
        part.add("to", vector((px + 1) * unit, 16 - py * unit, 8 + thickness / 2 + layer * .015));
        JsonObject rotation = new JsonObject();
        rotation.addProperty("axis", "z");
        rotation.addProperty("angle", 0);
        rotation.add("origin", vector(8, 8, 8));
        part.add("rotation", rotation);
        JsonObject faces = new JsonObject();
        for (String side : List.of("north", "south", "east", "west", "up", "down")) {
          JsonObject face = new JsonObject();
          face.addProperty("texture", "#" + key);
          face.addProperty("tintindex", layer);
          face.add(
              "uv",
              side.equals("north")
                  ? vector((px + 1) * unit, py * unit, px * unit, (py + 1) * unit)
                  : vector(px * unit, py * unit, (px + 1) * unit, (py + 1) * unit));
          faces.add(side, face);
        }
        part.add("faces", faces);
        parts.add(part);
      }
    if (profile == EquipmentStyle.QUILL && layer == 0) nativeCutout(minimumAlong, 1);
  }

  private void rectifiedSurface(double minimumAlong) throws IOException {
    var textures = model.getAsJsonObject("textures");
    String reference = textures.get("layer0").getAsString();
    while (reference.startsWith("#"))
      reference = textures.get(reference.substring(1)).getAsString();
    BufferedImage icon = NativeEquipmentModels.image(reference);
    boolean[][] mask = new boolean[PROFILE_GRID][PROFILE_GRID];
    for (int row = 0; row < PROFILE_GRID; row++)
      for (int column = 0; column < PROFILE_GRID; column++) {
        double x = PROFILE_MIN + (column + .5) / 2, y = PROFILE_MAX - (row + .5) / 2;
        if (y + (8 * Math.sqrt(2) - 8) < minimumAlong) continue;
        int coverage = 0;
        for (double dx : new double[] {-.3, 0, .3})
          for (double dy : new double[] {-.3, 0, .3}) {
            double u = 8 + (x + dx + y + dy - 16) / Math.sqrt(2),
                v = 8 + (x + dx - y - dy) / Math.sqrt(2);
            int px = (int) Math.floor(u * icon.getWidth() / 16),
                py = (int) Math.floor(v * icon.getWidth() / 16);
            if (px >= 0
                && py >= 0
                && px < icon.getWidth()
                && py < Math.min(icon.getWidth(), icon.getHeight())
                && (icon.getRGB(px, py) >>> 24) > 0) coverage++;
          }
        mask[row][column] = coverage >= 4;
      }
    for (int row = 0; row < PROFILE_GRID; row++) {
      int column = 0;
      while (column < PROFILE_GRID) {
        if (!mask[row][column]) {
          column++;
          continue;
        }
        double y = PROFILE_MAX - (row + .5) / 2;
        double depth = surfaceDepth(PROFILE_MIN + (column + .5) / 2, y, mask, row, column);
        int end = column + 1;
        while (end < PROFILE_GRID
            && mask[row][end]
            && surfaceDepth(PROFILE_MIN + (end + .5) / 2, y, mask, row, end) == depth) end++;
        int before = parts.size();
        box(
            "body",
            PROFILE_MIN + column / 2.0,
            y - .25,
            8 - depth / 2,
            PROFILE_MIN + end / 2.0,
            y + .25,
            8 + depth / 2);
        for (int partIndex = before; partIndex < parts.size(); partIndex++) {
          var part = parts.get(partIndex).getAsJsonObject();
          var faces = part.getAsJsonObject("faces");
          double u0 = 6 + part.getAsJsonArray("from").get(0).getAsDouble() / 2;
          double u1 = 6 + part.getAsJsonArray("to").get(0).getAsDouble() / 2;
          for (String side : List.of("north", "south")) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", "#surface");
            face.addProperty("tintindex", 0);
            face.add(
                "uv",
                side.equals("north")
                    ? vector(u1, row / 4.0, u0, (row + 1) / 4.0)
                    : vector(u0, row / 4.0, u1, (row + 1) / 4.0));
            faces.add(side, face);
          }
        }
        column = end;
      }
    }
  }

  private double surfaceDepth(double x, double y, boolean[][] mask, int row, int column) {
    double along = y + (8 * Math.sqrt(2) - 8), across = x - 8;
    double edge = 2;
    for (int dy = -3; dy <= 3; dy++)
      for (int dx = -3; dx <= 3; dx++) {
        int rx = column + dx, ry = row + dy;
        if (rx < 0 || ry < 0 || rx >= PROFILE_GRID || ry >= PROFILE_GRID || !mask[ry][rx])
          edge = Math.min(edge, Math.hypot(dx, dy) / 2);
      }
    double depth =
        switch (profile) {
          case FISH ->
              .35
                  + 3.6
                      * Math.sqrt(Math.max(0, 1 - Math.pow((along - 10.5) / 5.5, 2)))
                      * Math.sqrt(Math.max(0, 1 - Math.pow((across - .8) / 2.5, 2)));
          case CORAL -> Math.min(2.4, .65 + edge * .8);
          case SHELL -> Math.min(2.1, .5 + edge * .55);
          case BRUSH -> Math.min(2.5, .8 + edge * .8);
          default -> Math.min(.8, .25 + edge * .22);
        };
    return Math.max(.25, Math.rint(depth * 8) / 8);
  }

  private void bell() {
    grip(6.2);
    box("band", 6.9, 6.1, 6.9, 9.1, 7.1, 9.1);
    cylinder("body", 8, 8, 1.1, 7, 8.4);
    for (int row = 0; row < 5; row++)
      ring("body", 8, 8.1 + row * 1.05, 8, 1.2 + row * .65, .55, 1.1);
    ring("edge", 8, 13.2, 8, 4.15, .6, .7);
    cylinder("dark", 8, 8, .3, 8.3, 11.3);
    sphere("body", 8, 11.2, 8, .8, .8, .8);
  }

  private void wateringCan() {
    cylinder("body", 8, 8, 3.3, 2, 9.2);
    ring("edge", 8, 9.2, 8, 3.35, .6, .65);
    cylinder("dark", 8, 8, 2.5, 8.8, 9.05);
    hoop("body", 8, 11.3, 8, 3.2, .65, 1.1);
    hoop("body", 12.5, 6.8, 8, 2.3, .6, 1.1);
    rotated("body", 2.6, 4.5, 7.25, 4.1, 9.6, 8.75, "z", 45, 4.1, 5, 8);
    rotated("edge", 2.1, 8.8, 6.8, 4.6, 10, 9.2, "z", 45, 4.1, 5, 8);
  }

  private void net() {
    grip(7.8);
    hoop("body", 8, 11, 8, 4.1, .45, .65);
    double[] radii = {3.9, 3.6, 2.5, 1.2, .25};
    for (int row = 0; row < radii.length; row++)
      hoop("paper", 8, 11, 8.1 + row * 1.05, radii[row], .14, .18);
    for (int spoke = 0; spoke < 8; spoke++) {
      double angle = spoke * Math.PI / 4;
      for (int row = 0; row < radii.length - 1; row++)
        strand(
            "paper",
            8 + Math.cos(angle) * radii[row],
            11 + Math.sin(angle) * radii[row],
            8.1 + row * 1.05,
            8 + Math.cos(angle) * radii[row + 1],
            11 + Math.sin(angle) * radii[row + 1],
            9.15 + row * 1.05,
            .17);
    }
  }

  private void coil() {
    for (int loop = 0; loop < 4; loop++) hoop("body", 8, 8, 5.5 + loop * 1.2, 4.1, .8, 1);
    for (double z : new double[] {5.5, 9.1}) {
      hoop("body", 8, 8, z, 2.85, .85, 1);
      hoop("body", 8, 8, z, 1.6, .85, 1);
    }
    box("rope", 7.4, 3.1, 5, 8.6, 12.9, 5.8);
    box("rope", 7.4, 3.1, 9.9, 8.6, 12.9, 10.7);
    box("rope", 7.4, 3.1, 5, 8.6, 3.9, 10.7);
    box("rope", 7.4, 12.1, 5, 8.6, 12.9, 10.7);
    strand("rope", 8.1, 4.1, 5.1, 11, 1.7, 7, .28);
  }

  private void dial() {
    if (profile == EquipmentStyle.DEPTH_METER) {
      box("body", 2.5, 4, 6.8, 13.5, 7.5, 9.2);
      box("body", 3.5, 7.5, 6.8, 12.5, 9.5, 9.2);
      box("body", 5, 9.5, 6.8, 11, 10.8, 9.2);
      rotated("edge", 3.2, 7.5, 6.7, 4, 10, 9.3, "z", -45, 4, 8, 8);
      rotated("edge", 12, 7.5, 6.7, 12.8, 10, 9.3, "z", 45, 12, 8, 8);
      box("edge", 2.5, 4, 9.2, 13.5, 4.7, 9.5);
      face(4, 4.8, 9.3, 12, 8.8, 4, 6, 12, 12, 0);
      face(5.2, 8.8, 9.3, 10.8, 9.8, 5.2, 4.5, 10.8, 6, 0);
      return;
    }
    double cx = 8, cy = 8, radius = 5.2;
    if (profile == EquipmentStyle.WATCH) {
      cx = 6;
      cy = 6;
      radius = 3.6;
      hoop("rope", 9, 11.3, 8, 3, .35, .45);
    }
    if (profile == EquipmentStyle.MONSTROMETER) radius = 4.8;
    solidDisc("body", cx, cy, 8, radius, 2.2);
    hoop("edge", cx, cy, readable(profile) ? 9.275 : 9.15, radius, .55, .35);
    dialFace(cx, cy, 9.36, radius - .8);
    if (profile == EquipmentStyle.MONSTROMETER) {
      for (int x : new int[] {3, 12})
        for (int y : new int[] {3, 12}) box("dark", x - .5, y - .5, 6.8, x + .5, y + .5, 9.2);
      box("grip", 6, 1, 7, 10, 3.5, 9);
    }
    if (profile == EquipmentStyle.ENDER_EYE) hoop("body", cx, cy, 9.45, 2.2, .45, .3);
    if (id.equals("naturescompass:naturescompass")) {
      for (int side : new int[] {-1, 1}) {
        strand("green", 8 + side * 4.8, 4, 8.8, 8 + side * 4.6, 12, 9.4, .3);
        rotated(
            "green",
            8 + side * 4.8 - .45,
            10,
            9.1,
            8 + side * 4.8 + .45,
            12,
            9.6,
            "z",
            side * 22.5,
            8 + side * 4.8,
            11,
            9.3);
        rotated(
            "green",
            8 + side * 4.4 - .45,
            4.3,
            9,
            8 + side * 4.4 + .45,
            6.3,
            9.5,
            "z",
            -side * 22.5,
            8 + side * 4.4,
            5.3,
            9.3);
      }
    }
  }

  private void dialFace(double x, double y, double z, double radius) {
    double sourceX = profile == EquipmentStyle.WATCH ? 5.5 : 8,
        sourceY = profile == EquipmentStyle.WATCH ? 10 : 8,
        sourceRadius = profile == EquipmentStyle.WATCH ? 2.3 : 4;
    for (double[] band :
        new double[][] {{-1, -.7, .45}, {-.7, -.4, .7}, {-.4, .4, 1}, {.4, .7, .7}, {.7, 1, .45}}) {
      double left = x - radius * band[2],
          right = x + radius * band[2],
          bottom = y + radius * band[0],
          top = y + radius * band[1];
      face(
          left,
          bottom,
          z,
          right,
          top,
          sourceX - band[2] * sourceRadius,
          sourceY - band[1] * sourceRadius,
          sourceX + band[2] * sourceRadius,
          sourceY - band[0] * sourceRadius,
          0);
    }
  }

  private void thermometer() {
    sphere("glass", 8, 3.5, 8, 1.7, 2.1, 1.65);
    solidDisc("red", 8, 3.5, 9.5, 1.15, .55);
    cylinder("glass", 8, 8, .7, 4.5, 15);
    box("red", 7.75, 4.6, 8.72, 8.25, 9.7, 8.97);
    for (int tick = 0; tick < 5; tick++)
      box("dark", 8.35, 7 + tick * 1.3, 8.72, 8.8, 7.2 + tick * 1.3, 8.9);
  }

  private void instrument() {
    if (profile == EquipmentStyle.FISH_FINDER) solidDisc("body", 8, 8, 8, 5.5, 2.6);
    else if (id.equals("chipped:multimeter")) box("body", 3, 2.5, 6.7, 13, 13.5, 9.3);
    else bevelBox("body", 3, 2.5, 6.7, 13, 13.5, 9.3, .6);
    if (profile == EquipmentStyle.TERMINAL) {
      box("dark", 11.3, 12, 7.6, 12, 16, 8.4);
      for (int row = 0; row < 3; row++)
        for (int column = 0; column < 3; column++) {
          box(
              "dark",
              4.4 + column * 2.6,
              4 + row * 2.5,
              9.3,
              6.1 + column * 2.6,
              5.7 + row * 2.5,
              9.6);
          face(
              4.4 + column * 2.6,
              4 + row * 2.5,
              9.62,
              6.1 + column * 2.6,
              5.7 + row * 2.5,
              4 + 3 * column,
              5 + 3 * (2 - row),
              6 + 3 * column,
              7 + 3 * (2 - row),
              0);
        }
    } else if (profile == EquipmentStyle.MULTIMETER) {
      box("green", 4.5, 10, 9.3, 11.5, 12, 9.45);
      solidDisc("detail", 8, 6.5, 9.4, 2.1, .7);
      rotated("dark", 7.7, 5, 9.8, 8.3, 8, 10, "z", 22.5, 8, 6.5, 10);
    } else if (profile == EquipmentStyle.FISH_FINDER) {
      box("dark", 4, 12.5, 7.3, 4.7, 16, 8.2);
      face(4.2, 4.2, 9.4, 11.8, 11.8, 5, 7, 11, 12, 0);
    } else {
      hoop("edge", 8, 8, 9.4, 3.6, .6, .5);
      face(5.3, 5.3, 9.35, 10.7, 10.7, 5, 5, 11, 11, 0);
      for (int x : new int[] {3, 12})
        for (int y : new int[] {3, 12}) box("red", x, y, 9.2, x + 1, y + 1, 9.5);
    }
  }

  private void document() {
    if (profile == EquipmentStyle.ALTIMETER) {
      box("body", 4, 2, 6.6, 6, 14, 9.4);
      box("body", 10, 2, 6.6, 12, 14, 9.4);
      box("body", 6, 2, 6.6, 10, 6, 9.4);
      box("body", 6, 12, 6.6, 10, 14, 9.4);
    } else if (profile == EquipmentStyle.TABLET) {
      bevelBox("body", 3.5, 1.5, 7, 12.5, 14.5, 9, .7);
      face(4, 2.5, 9.05, 12, 13.5, 4, 3, 12, 13, 0);
    } else if (profile == EquipmentStyle.BOOK) {
      boolean reviewed = id.equals("chipped:alchemy_book");
      box("paper", reviewed ? 3.5 : 3, 2, reviewed ? 6.5 : 6.4, 12, 13.3, reviewed ? 9.5 : 9.6);
      box("body", reviewed ? 3.5 : 2.5, 1.5, 6, 12.5, 14, 6.5);
      box("body", reviewed ? 3.5 : 2.5, 1.5, 9.5, 12.5, 14, 10);
      box("grip", 2.5, 1.5, 6, 3.5, 14, 10);
      for (int x : new int[] {3, 11})
        for (double y : new double[] {2, 12.5}) box("gold", x, y, 10, x + 1, y + 1, 10.2);
      solidDisc("green", 7.5, 8, 10.1, 1.2, .4);
    } else {
      box(profile == EquipmentStyle.CLIPBOARD ? "grip" : "body", 3, 1, 7.6, 13, 14.5, 8.4);
      face(3.7, 2, 8.45, 12.3, 13, 4, 4, 12, 13, 0);
      if (profile == EquipmentStyle.CLIPBOARD) {
        box("detail", 6, 13, 8.3, 10, 15, 8.7);
        if (id.equals("create:clipboard_2")) {
          model.getAsJsonObject("textures").addProperty("layer0", "create:item/clipboard");
          rotated("dark", 5.8, 2.3, 8.7, 6.15, 14.2, 9.05, "z", 22.5, 6, 8, 9);
          for (int row = 0; row < 7; row++) {
            double width = 1.8 - Math.abs(row - 3) * .35;
            rotated("paper", 6 - width, 7 + row, 8.8, 6 + width, 8 + row, 9.12, "z", 22.5, 6, 8, 9);
          }
        }
      } else rotated("paper", 10.5, 5, 8.5, 11.3, 15, 9, "z", -22.5, 11, 9, 9);
    }
  }

  private void needles() {
    cylinder("body", 7.3, 7.5, .4, 1, 15);
    rotated("body", 8.5, 1, 8, 9.3, 15, 8.8, "z", -45, 8.9, 8, 8.4);
    sphere("edge", 7.3, 14.7, 7.5, .65, .65, .65);
  }

  private void glue() {
    bevelBox("body", 5.5, 2.5, 6.5, 10.5, 11, 9.5, .6);
    box("edge", 5.2, 1.5, 6.4, 10.8, 2.5, 9.6);
    cylinder("detail", 8, 8, .9, 11, 12.5);
    cylinder("detail", 8, 8, .55, 12.5, 15);
    face(6, 4, 9.55, 10, 9.5, 5, 7, 9, 12, 0);
  }

  private void horn() {
    tube("body", 10.5, 2, 8, 1, 3, 0);
    tube("body", 10, 4.2, 8, 1.2, 3.5, 22.5);
    tube("body", 8.6, 6.4, 8, 1.7, 3.8, 45);
    tube("body", 6.2, 8.6, 8, 2.3, 3.5, 22.5);
    ring("edge", 5.1, 11.3, 8, 3, .65, .8);
    ring("body", 5.1, 10.3, 8, 2.6, .65, 1.1);
  }

  private void whistle() {
    sphere("body", 8, 7.5, 8, 3.4, 2.8, 2.8);
    bevelBox("body", 2.2, 6.5, 7, 5.5, 8.4, 9, .35);
    box("dark", 2.1, 7, 7.3, 2.3, 7.7, 8.7);
    hoop("paper", 12, 5, 8, 2.5, .25, .35);
    box("paper", 10.4, 7, 7.8, 12.2, 7.5, 8.2);
  }

  private void rope() {
    if (profile == EquipmentStyle.LEAD) {
      hoop("body", 7.5, 10, 8, 3.5, .45, .6);
      strand("body", 10, 7.5, 8, 12, 4, 8.7, .6);
      strand("body", 12, 4, 8.7, 5, 1.5, 8, .6);
    } else {
      if (id.endsWith("_full")) sphere("dark", 8, 8, 8, 3.4, 3.4, 1.45);
      for (int ring = 0; ring < 3; ring++) hoop("body", 8, 8, 6.5 + ring, 4, .4, .65);
      for (int index = 0; index < 5; index++)
        strand("rope", 4 + index * 1.5, 4.5, 7, 5 + index * 1.5, 11, 9, .22);
    }
  }

  private void smallLoop() {
    box("body", 7.5, profile == EquipmentStyle.BUBBLE ? -1 : 1, 7.5, 8.5, 10, 8.5);
    if (profile == EquipmentStyle.KEY) {
      bevelBox("body", 5.5, 10, 7.5, 6.4, 14.8, 8.5, .2);
      bevelBox("body", 9.6, 10, 7.5, 10.5, 14.8, 8.5, .2);
      box("body", 6.4, 10, 7.5, 9.6, 10.9, 8.5);
      box("body", 6.4, 13.9, 7.5, 9.6, 14.8, 8.5);
      box("body", 8.4, 2, 7.5, 10.4, 3, 8.5);
      box("body", 8.4, 4, 7.5, 9.7, 5, 8.5);
      box("body", 8.4, 6, 7.5, 10.1, 7, 8.5);
    } else hoop("body", 8, 12, 8, 3.1, .45, .6);
  }

  private void spyglass() {
    cylinder("grip", 8, 8, 1.4, 1, 6);
    cylinder("body", 8, 8, 1.7, 5.5, 11);
    cylinder("edge", 8, 8, 2.1, 10.5, 14);
    ring("edge", 8, 14, 8, 2.15, .4, .55);
    cylinder("glass", 8, 8, 1.7, 14.1, 14.3);
  }

  private void flare() {
    cylinder("body", 8, 8, 1.2, 1, 8);
    if (profile == EquipmentStyle.GLOW_FLARE) {
      cylinder("glass", 8, 8, 2.2, 8, 10);
      sphere("glass", 8, 11, 8, 1.6, 2.1, 1.6);
    } else {
      bevelBox("glass", 5, 8, 5.8, 11, 14, 10.2, .5);
      face(5.8, 8.8, 10.25, 10.2, 13.2, 6, 4, 10, 8, 0);
    }
    strand("rope", 9, 2, 8, 11, 1, 8, .25);
    strand("rope", 11, 1, 8, 12, 2.5, 8, .25);
  }

  private void paintKit() {
    cylinder("detail", 6, 8, 2.8, 1.5, 8.5);
    ring("red", 6, 8.5, 8, 2.9, .45, .5);
    hoop("dark", 6, 10.5, 8, 2.9, .3, .45);
    rotated("grip", 10, 1.5, 7.4, 11.2, 12, 8.6, "z", -22.5, 10.6, 7, 8);
    rotated("paper", 9.7, 11, 7.1, 11.5, 15, 8.9, "z", -22.5, 10.6, 7, 8);
    rotated("red", 9.7, 13.5, 7.05, 11.5, 15, 8.95, "z", -22.5, 10.6, 7, 8);
  }

  private void crystal() {
    sphere("body", 8, 8, 8, 4.5, 5.5, 3.4);
    rotated("edge", 6.5, 11.6, 6.6, 9.5, 14.4, 9.4, "z", 45, 8, 12, 8);
    rotated("dark", 6.8, 1.8, 6.8, 9.2, 4.6, 9.2, "z", 45, 8, 4, 8);
  }

  private void lunchbox() {
    bevelBox("body", 3, 2, 5, 13, 9, 11, .7);
    bevelBox("body", 3, 9, 5, 13, 10.1, 11, .7);
    bevelBox("body", 3.7, 10.1, 5, 12.3, 11, 11, .7);
    bevelBox("body", 4.5, 11, 5, 11.5, 11.6, 11, .7);
    face(3.6, 2.6, 11.02, 12.4, 9.6, 3, 7, 13, 14, 0);
    for (int rib = 0; rib < 8; rib++)
      box("grip", 4.8 + rib * .9, 11.59, 5.2, 4.95 + rib * .9, 11.72, 10.8);
    hoop("grip", 8, 12.2, 8, 2.5, .5, 1.1);
    for (int x : new int[] {5, 10}) {
      box("detail", x, 7, 4.7, x + .8, 10, 5.05);
      box("detail", x, 7, 10.95, x + .8, 10, 11.3);
    }
  }

  private void tube(
      String material, double x, double y, double z, double radius, double height, double angle) {
    rotated(
        material,
        x - radius,
        y,
        z - radius,
        x + radius,
        y + height,
        z - radius + .5,
        "z",
        angle,
        x,
        y,
        z);
    rotated(
        material,
        x - radius,
        y,
        z + radius - .5,
        x + radius,
        y + height,
        z + radius,
        "z",
        angle,
        x,
        y,
        z);
    rotated(
        material,
        x - radius,
        y,
        z - radius + .5,
        x - radius + .5,
        y + height,
        z + radius - .5,
        "z",
        angle,
        x,
        y,
        z);
    rotated(
        material,
        x + radius - .5,
        y,
        z - radius + .5,
        x + radius,
        y + height,
        z + radius - .5,
        "z",
        angle,
        x,
        y,
        z);
  }

  private void strand(
      String material,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double width) {
    if (readable(profile)) {
      int steps = Math.max(1, (int) Math.ceil(Math.abs(x2 - x1) / width));
      for (int step = 0; step < steps; step++) {
        double a = step / (double) steps, b = (step + 1) / (double) steps, middle = (a + b) / 2;
        double first = x1 + (x2 - x1) * a, second = x1 + (x2 - x1) * b;
        double y = y1 + (y2 - y1) * middle, z = z1 + (z2 - z1) * middle;
        box(
            material,
            Math.min(first, second),
            y - width / 2,
            z - width / 2,
            Math.max(first, second),
            y + width / 2,
            z + width / 2);
      }
      return;
    }
    int count =
        (int)
            Math.ceil(
                Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)))
                    / (width * .85));
    for (int step = 0; step <= count; step++) {
      double t = (double) step / Math.max(1, count),
          x = x1 + (x2 - x1) * t,
          y = y1 + (y2 - y1) * t,
          z = z1 + (z2 - z1) * t;
      box(
          material,
          x - width / 2,
          y - width / 2,
          z - width / 2,
          x + width / 2,
          y + width / 2,
          z + width / 2);
    }
  }

  private void sphere(
      String material, double x, double y, double z, double rx, double ry, double rz) {
    for (int band = 0; band < 6; band++) {
      double lo = -1 + band / 3.0, hi = -1 + (band + 1) / 3.0;
      double radius = Math.sqrt(1 - Math.pow((lo + hi) / 2, 2));
      bevelBox(
          material,
          x - rx * radius,
          y + ry * lo,
          z - rz * radius,
          x + rx * radius,
          y + ry * hi,
          z + rz * radius,
          Math.min(rx, rz) * radius * .28);
    }
  }

  private void cylinder(
      String material, double x, double z, double radius, double bottom, double top) {
    bevelBox(material, x - radius, bottom, z - radius, x + radius, top, z + radius, radius * .35);
  }

  private void solidDisc(
      String material, double x, double y, double z, double radius, double depth) {
    if (readable(profile)) {
      for (double[] band :
          new double[][] {{-1, -.8, .45}, {-.8, -.5, .7}, {-.5, .5, 1}, {.5, .8, .7}, {.8, 1, .45}})
        box(
            material,
            x - radius * band[2],
            y + radius * band[0],
            z - depth / 2,
            x + radius * band[2],
            y + radius * band[1],
            z + depth / 2);
      return;
    }
    box(
        material,
        x - radius,
        y - radius * .5,
        z - depth / 2,
        x + radius,
        y + radius * .5,
        z + depth / 2);
    box(
        material,
        x - radius * .7,
        y - radius * .8,
        z - depth / 2,
        x + radius * .7,
        y + radius * .8,
        z + depth / 2);
    box(
        material,
        x - radius * .45,
        y - radius,
        z - depth / 2,
        x + radius * .45,
        y + radius,
        z + depth / 2);
  }

  private void bevelBox(
      String material,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double bevel) {
    box(material, x1 + bevel, y1, z1, x2 - bevel, y2, z2);
    box(material, x1, y1, z1 + bevel, x1 + bevel, y2, z2 - bevel);
    box(material, x2 - bevel, y1, z1 + bevel, x2, y2, z2 - bevel);
    if (readable(profile)) return;
    double half = bevel / Math.sqrt(2);
    for (double cx : new double[] {x1 + bevel, x2 - bevel})
      for (double cz : new double[] {z1 + bevel, z2 - bevel})
        rotated(material, cx - half, y1, cz - half, cx + half, y2, cz + half, "y", 45, cx, y1, cz);
  }

  private void ring(
      String material, double x, double y, double z, double radius, double wall, double height) {
    double straight = radius * .42;
    box(material, x - straight, y, z - radius, x + straight, y + height, z - radius + wall);
    box(material, x - straight, y, z + radius - wall, x + straight, y + height, z + radius);
    box(material, x - radius, y, z - straight, x - radius + wall, y + height, z + straight);
    box(material, x + radius - wall, y, z - straight, x + radius, y + height, z + straight);
    for (int sx : new int[] {-1, 1})
      for (int sz : new int[] {-1, 1}) {
        double cx = x + sx * radius * .71, cz = z + sz * radius * .71;
        rotated(
            material,
            cx - radius * .4,
            y,
            cz - wall / 2,
            cx + radius * .4,
            y + height,
            cz + wall / 2,
            "y",
            sx == sz ? -45 : 45,
            cx,
            y,
            cz);
      }
  }

  private void hoop(
      String material, double x, double y, double z, double radius, double wall, double depth) {
    if (readable(profile)) {
      double inner = radius - wall;
      var levels = new java.util.TreeSet<Double>();
      for (double factor : new double[] {-1, -.8, -.5, .5, .8, 1}) {
        levels.add(factor * radius);
        levels.add(factor * inner);
      }
      var bounds = List.copyOf(levels);
      for (int index = 1; index < bounds.size(); index++) {
        double low = bounds.get(index - 1), high = bounds.get(index), middle = (low + high) / 2;
        double outerWidth = discWidth(middle / radius) * radius;
        double innerWidth = discWidth(middle / inner) * inner;
        if (innerWidth == 0)
          box(
              material,
              x - outerWidth,
              y + low,
              z - depth / 2,
              x + outerWidth,
              y + high,
              z + depth / 2);
        else
          for (int side : new int[] {-1, 1})
            box(
                material,
                x + (side < 0 ? -outerWidth : innerWidth),
                y + low,
                z - depth / 2,
                x + (side < 0 ? -innerWidth : outerWidth),
                y + high,
                z + depth / 2);
      }
      return;
    }
    double straight = radius * .42;
    box(
        material,
        x - straight,
        y - radius,
        z - depth / 2,
        x + straight,
        y - radius + wall,
        z + depth / 2);
    box(
        material,
        x - straight,
        y + radius - wall,
        z - depth / 2,
        x + straight,
        y + radius,
        z + depth / 2);
    box(
        material,
        x - radius,
        y - straight,
        z - depth / 2,
        x - radius + wall,
        y + straight,
        z + depth / 2);
    box(
        material,
        x + radius - wall,
        y - straight,
        z - depth / 2,
        x + radius,
        y + straight,
        z + depth / 2);
    for (int sx : new int[] {-1, 1})
      for (int sy : new int[] {-1, 1}) {
        double cx = x + sx * radius * .71, cy = y + sy * radius * .71;
        rotated(
            material,
            cx - radius * .4,
            cy - wall / 2,
            z - depth / 2,
            cx + radius * .4,
            cy + wall / 2,
            z + depth / 2,
            "z",
            sx == sy ? -45 : 45,
            cx,
            cy,
            z);
      }
  }

  private static double discWidth(double height) {
    double distance = Math.abs(height);
    return distance > 1 ? 0 : distance > .8 ? .45 : distance > .5 ? .7 : 1;
  }

  private void box(
      String material, double x1, double y1, double z1, double x2, double y2, double z2) {
    for (double y = y1; y < y2 - .001; y += 7.9)
      for (double x = x1; x < x2 - .001; x += 7.9)
        for (double z = z1; z < z2 - .001; z += 7.9) {
          add(
              material,
              x,
              y,
              z,
              Math.min(x2, x + 7.9),
              Math.min(y2, y + 7.9),
              Math.min(z2, z + 7.9),
              null);
          if (id.startsWith("exposure:interplanar_projector")) {
            // UV subdivisions inside the tall housing are not physical caps.
            JsonObject faces =
                parts.get(parts.size() - 1).getAsJsonObject().getAsJsonObject("faces");
            if (x > x1) faces.remove("west");
            if (x + 7.9 < x2) faces.remove("east");
            if (y > y1) faces.remove("down");
            if (y + 7.9 < y2) faces.remove("up");
            if (z > z1) faces.remove("north");
            if (z + 7.9 < z2) faces.remove("south");
          }
        }
  }

  private void rotated(
      String material,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String axis,
      double angle,
      double ox,
      double oy,
      double oz) {
    JsonObject rotation = new JsonObject();
    rotation.addProperty("axis", axis);
    rotation.addProperty("angle", angle);
    rotation.add("origin", vector(ox, oy, oz));
    add(material, x1, y1, z1, x2, y2, z2, rotation);
  }

  private void add(
      String material,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      JsonObject rotation) {
    int tile = MATERIALS.indexOf(material);
    if (tile < 0) throw new IllegalArgumentException(material);
    JsonObject part = new JsonObject();
    part.addProperty("name", material + "_" + serial++);
    part.add("from", vector(x1, y1, z1));
    part.add("to", vector(x2, y2, z2));
    if (rotation != null) part.add("rotation", rotation);
    JsonObject faces = new JsonObject();
    double u = tile % dev.aeroscapes.atmosphere.EquipmentArtwork.MATERIAL_COLUMNS * 2,
        v = tile / dev.aeroscapes.atmosphere.EquipmentArtwork.MATERIAL_COLUMNS * 2;
    for (String side : List.of("north", "south", "east", "west", "up", "down")) {
      double width = (side.equals("east") || side.equals("west")) ? z2 - z1 : x2 - x1;
      double height = (side.equals("up") || side.equals("down")) ? z2 - z1 : y2 - y1;
      JsonObject face = new JsonObject();
      face.addProperty("texture", "#surface");
      face.add("uv", vector(u, v, u + Math.min(2, width / 4), v + Math.min(2, height / 4)));
      faces.add(side, face);
    }
    part.add("faces", faces);
    parts.add(part);
  }

  private void face(
      double x1,
      double y1,
      double z,
      double x2,
      double y2,
      double u1,
      double v1,
      double u2,
      double v2,
      int tint) {
    JsonObject part = new JsonObject();
    part.addProperty("name", "native_face_" + serial++);
    part.add("from", vector(x1, y1, z));
    part.add("to", vector(x2, y2, z + .015));
    JsonObject face = new JsonObject();
    face.addProperty("texture", "#layer" + tint);
    face.addProperty("tintindex", tint);
    face.add("uv", vector(u1, v1, u2, v2));
    JsonObject faces = new JsonObject();
    faces.add("south", face);
    part.add("faces", faces);
    parts.add(part);
  }

  private JsonObject display() {
    JsonObject display = new JsonObject();
    for (boolean left : new boolean[] {false, true}) {
      int side = left ? -1 : 1;
      String hand = left ? "lefthand" : "righthand";
      display.add(
          "thirdperson_" + hand,
          transform(0, profile == EquipmentStyle.SLINGSHOT ? 0 : side * 90, 0, 0, 2, 0, .75));
      display.add("firstperson_" + hand, transform(0, -side * 70, side * 10, 1.3, 2, 0, .65));
    }
    boolean rectified =
        switch (profile) {
          case KNIFE, CORAL, SHELL, FISH, BRUSH, BUBBLE -> true;
          default -> false;
        };
    boolean readable = readable(profile);
    if (rectified || readable || profile == EquipmentStyle.QUILL || id.contains("tooting_"))
      for (String context :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand")) {
        JsonObject source = nativeDisplay.getAsJsonObject(context);
        if (source == null)
          source = nativeDisplay.getAsJsonObject(context.replace("lefthand", "righthand"));
        JsonObject nativeTransform = source == null ? new JsonObject() : source.deepCopy();
        if (rectified) {
          JsonArray rotation =
              nativeTransform.has("rotation")
                  ? nativeTransform.getAsJsonArray("rotation").deepCopy()
                  : vector(0, 0, 0);
          rotation.set(
              2,
              new com.google.gson.JsonPrimitive(
                  rotation.get(2).getAsDouble() + (context.endsWith("lefthand") ? 45 : -45)));
          nativeTransform.add("rotation", rotation);
        }
        display.add(context, nativeTransform);
      }
    if (id.equals("chipped:saw") || id.equals("chipped:watering_can"))
      for (var context : display.entrySet()) {
        JsonArray angles = context.getValue().getAsJsonObject().getAsJsonArray("rotation");
        angles.set(1, new com.google.gson.JsonPrimitive(angles.get(1).getAsDouble() + 180));
      }
    return display;
  }

  private static JsonObject transform(
      double rx, double ry, double rz, double x, double y, double z, double scale) {
    JsonObject transform = new JsonObject();
    transform.add("rotation", vector(rx, ry, rz));
    transform.add("translation", vector(x, y, z));
    transform.add("scale", vector(scale, scale, scale));
    return transform;
  }

  private static JsonArray vector(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) array.add(value);
    return array;
  }
}
