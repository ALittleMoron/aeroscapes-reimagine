package dev.aeroscapes.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Shared CPU composition: installed block pixels retain their native sixteen-texel world scale. */
public final class WhaleTextureComposer {
  public static final int SIZE = 1024;

  private WhaleTextureComposer() {}

  public record Pixels(int width, int height, int[] argb) {
    public Pixels {
      if (width <= 0
          || height < width
          || height % width != 0
          || argb.length != Math.multiplyExact(width, height))
        throw new IllegalArgumentException("Invalid square-frame texture pixels");
    }

    public int get(int x, int y) {
      return argb[y * width + x];
    }
  }

  public static Set<String> requiredTextureIds(JsonObject layout) {
    Set<String> result = new HashSet<>();
    for (String role : Set.of("skin", "coat", "edge", "growth"))
      textures(layout.getAsJsonObject(role), result);
    for (var slot : layout.getAsJsonArray("slots")) textures(slot.getAsJsonObject(), result);
    return Set.copyOf(result);
  }

  private static void textures(JsonObject surface, Set<String> result) {
    for (String face : Set.of("side", "top", "bottom")) result.add(surface.get(face).getAsString());
  }

  public static Pixels compose(
      String variety, JsonObject geometry, JsonObject layout, Map<String, Pixels> blocks) {
    if (layout.get("schema").getAsInt() != 2)
      throw new IllegalArgumentException("Unsupported whale material layout schema");
    if (!layout.get("variety").getAsString().equals(variety))
      throw new IllegalArgumentException("Whale material layout does not match its variety");
    for (String id : requiredTextureIds(layout))
      if (!blocks.containsKey(id))
        throw new IllegalArgumentException("Missing installed block texture " + id);
    int[] output = new int[SIZE * SIZE];
    Map<String, JsonObject> surfaces = new HashMap<>();
    for (var value : layout.getAsJsonArray("decorations")) {
      JsonObject decoration = value.getAsJsonObject();
      String key = decoration.get("bone").getAsString() + ":" + decoration.get("cube").getAsInt();
      if (surfaces.put(key, decoration) != null)
        throw new IllegalArgumentException("Duplicate whale cube material " + key);
      material(layout, decoration);
    }
    Map<String, JsonObject> cubesByName = new HashMap<>();
    for (var value :
        geometry
            .getAsJsonArray("minecraft:geometry")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("bones")) {
      JsonObject bone = value.getAsJsonObject();
      String name = bone.get("name").getAsString();
      if (!bone.has("cubes")) continue;
      JsonArray cubes = bone.getAsJsonArray("cubes");
      for (int index = 0; index < cubes.size(); index++) {
        JsonObject cube = cubes.get(index).getAsJsonObject();
        String key = name + ":" + index;
        cubesByName.put(key, cube);
        JsonObject surface = surfaces.get(key);
        if (surface == null) throw new IllegalArgumentException("Unassigned whale cube " + key);
        for (String face : Set.of("north", "south", "east", "west", "up", "down")) {
          if (cube.get("uv").isJsonObject() && !cube.getAsJsonObject("uv").has(face)) continue;
          int[] uv = face(cube, face);
          JsonObject reference =
              surface.has("faces") && surface.getAsJsonObject("faces").has(face)
                  ? surface.getAsJsonObject("faces").getAsJsonObject(face)
                  : surface;
          JsonObject selected = material(layout, reference);
          String direction = face.equals("up") ? "top" : face.equals("down") ? "bottom" : "side";
          Pixels source = blocks.get(selected.get(direction).getAsString());
          boolean sprite =
              surface.has("sampling") && surface.get("sampling").getAsString().equals("sprite");
          for (int row = 0; row < Math.abs(uv[3]); row++) {
            for (int column = 0; column < uv[2]; column++) {
              int x = uv[0] + column;
              int y = uv[3] < 0 ? uv[1] - 1 - row : uv[1] + row;
              if (x < 0 || y < 0 || x >= SIZE || y >= SIZE)
                throw new IllegalArgumentException("Whale face outside atlas " + key + ":" + face);
              if (sprite) {
                output[y * SIZE + x] =
                    source.get(
                        column * source.width() / uv[2], row * source.width() / Math.abs(uv[3]));
              } else {
                int[] coordinates = coordinates(cube, face, column, row);
                output[y * SIZE + x] = sample(source, coordinates[0], coordinates[1]);
              }
            }
          }
        }
      }
    }
    eyes(output, layout, cubesByName);
    return new Pixels(SIZE, SIZE, output);
  }

  private static JsonObject material(JsonObject layout, JsonObject reference) {
    if (reference.has("role") == reference.has("slot"))
      throw new IllegalArgumentException("Whale material needs exactly one role or slot");
    if (reference.has("role")) {
      String role = reference.get("role").getAsString();
      if (!Set.of("skin", "coat", "edge", "growth").contains(role))
        throw new IllegalArgumentException("Unknown whale material role " + role);
      return layout.getAsJsonObject(role);
    }
    return layout.getAsJsonArray("slots").get(reference.get("slot").getAsInt()).getAsJsonObject();
  }

  public static int sample(Pixels source, int horizontal, int vertical) {
    int x = Math.floorMod(horizontal, 16) * source.width() / 16;
    int y = Math.floorMod(vertical, 16) * source.width() / 16;
    return source.get(x, y);
  }

  public static int[] face(JsonObject cube, String face) {
    // Pinned GeckoLib reflects model X and traverses the native DOWN box face with negative V.
    JsonArray size = cube.getAsJsonArray("size");
    int width = size.get(0).getAsInt();
    int height = size.get(1).getAsInt();
    int depth = size.get(2).getAsInt();
    if (cube.get("uv").isJsonObject()) {
      JsonObject mapping = cube.getAsJsonObject("uv").getAsJsonObject(face);
      JsonArray uv = mapping.getAsJsonArray("uv");
      JsonArray extent = mapping.getAsJsonArray("uv_size");
      return new int[] {
        uv.get(0).getAsInt(),
        uv.get(1).getAsInt(),
        extent.get(0).getAsInt(),
        extent.get(1).getAsInt()
      };
    }
    JsonArray uv = cube.getAsJsonArray("uv");
    int u = uv.get(0).getAsInt();
    int v = uv.get(1).getAsInt();
    return switch (face) {
      case "north" -> new int[] {u + depth, v + depth, width, height};
      case "south" -> new int[] {u + 2 * depth + width, v + depth, width, height};
      case "west" -> new int[] {u + depth + width, v + depth, depth, height};
      case "east" -> new int[] {u, v + depth, depth, height};
      case "up" -> new int[] {u + depth, v, width, depth};
      case "down" -> new int[] {u + depth + width, v + depth, width, -depth};
      default -> throw new IllegalArgumentException("Unknown cube face " + face);
    };
  }

  public static int[] coordinates(JsonObject cube, String face, int column, int row) {
    JsonArray origin = cube.getAsJsonArray("origin");
    JsonArray size = cube.getAsJsonArray("size");
    int x = origin.get(0).getAsInt();
    int y = origin.get(1).getAsInt();
    int z = origin.get(2).getAsInt();
    int width = size.get(0).getAsInt();
    int height = size.get(1).getAsInt();
    int depth = size.get(2).getAsInt();
    int vertical = -(y + height - row);
    return switch (face) {
      case "north" -> new int[] {x + column, vertical};
      case "south" -> new int[] {x + width - 1 - column, vertical};
      case "east" -> new int[] {z + depth - 1 - column, vertical};
      case "west" -> new int[] {z + column, vertical};
      case "up" -> new int[] {x + column, z + depth - 1 - row};
      case "down" -> new int[] {x + column, z + row};
      default -> throw new IllegalArgumentException("Unknown cube face " + face);
    };
  }

  private static void eyes(int[] pixels, JsonObject layout, Map<String, JsonObject> cubes) {
    String[] shape = {"..rrrr..", ".riippr.", "riicpprr", "riippprr", ".rrrrrr.", "..rrrr.."};
    for (var value : layout.getAsJsonArray("eyes")) {
      JsonObject eye = value.getAsJsonObject();
      String key = eye.get("bone").getAsString() + ":" + eye.get("cube").getAsInt();
      JsonObject cube = cubes.get(key);
      if (cube == null) throw new IllegalArgumentException("Missing whale eye cube " + key);
      int[] uv = face(cube, eye.get("face").getAsString());
      int horizontal = eye.get("u").getAsInt();
      int vertical = eye.get("v").getAsInt();
      if (horizontal < 0
          || vertical < 0
          || horizontal + 8 > uv[2]
          || vertical + 6 > Math.abs(uv[3]))
        throw new IllegalArgumentException("Whale eye outside its authored face " + key);
      boolean mirror = eye.has("mirror") && eye.get("mirror").getAsBoolean();
      for (int row = 0; row < shape.length; row++) {
        for (int column = 0; column < shape[row].length(); column++) {
          char point = shape[row].charAt(column);
          if (point == '.') continue;
          int color =
              switch (point) {
                case 'r' -> 0xff313a36;
                case 'i' -> 0xff779f88;
                case 'p' -> 0xff141c1b;
                case 'c' -> 0xffe5eddf;
                default -> throw new IllegalArgumentException("Invalid authored eye stencil");
              };
          int x = uv[0] + horizontal + (mirror ? 7 - column : column);
          int y = uv[3] < 0 ? uv[1] - 1 - vertical - row : uv[1] + vertical + row;
          pixels[y * SIZE + x] = color;
        }
      }
    }
  }
}
