package dev.aeroscapes.aquatic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.PreviewRaster.Quad;
import dev.aeroscapes.aquatic.PreviewRaster.Vertex;
import dev.aeroscapes.atmosphere.HeldEquipmentGrip;
import dev.aeroscapes.sky.fauna.client.FaunaPreview;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;

/** Standalone offline inspection of the exported held models and native fauna rigs. */
public final class ModelPreview {
  private ModelPreview() {}

  private record View(String label, BufferedImage image) {}

  record Camera(int width, int height, double fov) {}

  public static void main(String[] args) throws IOException, ReflectiveOperationException {
    Path assets = Path.of(args[0]);
    Path output = Path.of(args[1]);
    String[] size = args[3].split("x", -1);
    if (size.length != 2) throw new IllegalArgumentException("Expected WIDTHxHEIGHT");
    Camera camera =
        new Camera(
            Integer.parseInt(size[0]), Integer.parseInt(size[1]), Double.parseDouble(args[4]));
    if (camera.width < 160
        || camera.height < 100
        || camera.width > 3840
        || camera.height > 2160
        || !(camera.fov >= 30 && camera.fov <= 110))
      throw new IllegalArgumentException("Invalid camera dimensions or FOV");
    if (!args[6].matches("[0-9a-fA-F]{6}"))
      throw new IllegalArgumentException("Expected six RGB hex digits");
    int tick = Integer.parseInt(args[5]);
    int tint = Integer.parseInt(args[6], 16);
    ToolKind kind = ToolKind.valueOf(args[7]);
    boolean slim =
        switch (args.length > 9 ? args[9] : "classic") {
          case "classic" -> false;
          case "slim" -> true;
          default -> throw new IllegalArgumentException("Expected classic or slim arms");
        };
    boolean refined =
        switch (args[8]) {
          case "true" -> true;
          case "false" -> false;
          default ->
              throw new IllegalArgumentException("Expected true or false for refined dispatch");
        };
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Files.createDirectories(output);
    var textures = new PreviewTextures(assets, tick);
    var sheets = new ArrayList<String>();
    for (String requested : args[2].split(",")) {
      if (requested.startsWith("creature:")) {
        sheets.add(creature(requested, output, camera, tick));
        continue;
      }
      for (var id : subjects(assets, requested)) {
        sheets.add(item(id, assets, output, textures, camera, kind, refined, tint, tick, slim));
      }
    }
    gallery(output, sheets);
    System.out.println("Rendered " + sheets.size() + " model sheets in " + output);
  }

  private static String creature(String requested, Path output, Camera camera, int tick)
      throws IOException {
    String subject = requested.substring("creature:".length());
    var faces = FaunaPreview.capture(subject, tick);
    var views = new ArrayList<View>();
    String[] names = {"Front", "Side", "Back", "Oblique", "Dorsal", "Ventral"};
    float[][] angles = {{0, 0}, {90, 0}, {180, 0}, {32, 17}, {0, 90}, {0, -90}};
    for (int i = 0; i < names.length; i++)
      views.add(new View(names[i], objectView(faces, angles[i][0], angles[i][1], camera)));
    var motion =
        List.of(
            faces,
            FaunaPreview.capture(subject, tick + 10),
            FaunaPreview.capture(subject, tick + 20));
    var envelope = motion.stream().flatMap(List::stream).toList();
    var frame = studioCamera(envelope, 32, 17, camera);
    for (int i = 0; i < motion.size(); i++)
      views.add(
          new View(
              "Motion | tick " + (tick + 10 * i),
              PreviewRaster.render(
                  transform(motion.get(i), frame), camera.width, camera.height, STUDIO_FOV)));
    return save(
        output,
        requested,
        views,
        camera,
        "Native rig and texture | fitted studio views | shared camera for motion samples");
  }

  private static String item(
      ResourceLocation id,
      Path assets,
      Path output,
      PreviewTextures textures,
      Camera camera,
      ToolKind kind,
      boolean refined,
      int tint,
      int tick,
      boolean slim)
      throws IOException, ReflectiveOperationException {
    Path base = assets.resolve(id.getNamespace()).resolve("models/item");
    var wrapper = read(base.resolve(id.getPath() + ".json"));
    var hand = wrapper.getAsJsonObject("perspectives");
    if (hand == null)
      throw new IOException("Select a perspective-routed item state; selector: " + id);
    var parent =
        ResourceLocation.parse(
            hand.getAsJsonObject("firstperson_righthand").get("parent").getAsString());
    for (String context : IronHeldMesh.HANDS)
      if (!parent.toString().equals(hand.getAsJsonObject(context).get("parent").getAsString()))
        throw new IOException("Different hand meshes require independent resolution: " + id);
    var model =
        read(
            assets
                .resolve(parent.getNamespace())
                .resolve("models")
                .resolve(parent.getPath() + ".json"));
    int itemTint = stackTint(id, tint);
    var faces = geometry(model, assets, textures, itemTint);
    var views = new ArrayList<View>();
    views.add(new View("Native inventory sprite", sprite(wrapper, textures, camera, itemTint)));
    views.add(new View("Front | fitted", objectView(faces, 0, 0, camera)));
    views.add(new View("Edge | fitted", objectView(faces, 90, 0, camera)));
    views.add(new View("Oblique | fitted", objectView(faces, 32, 15, camera)));
    var frames =
        HeldViewport.attachments(model, id, kind, refined, HeldEquipmentGrip.usesPalm(id), slim);
    for (var frame : frames) {
      var f = frame.getAsJsonObject();
      boolean left = f.get("left").getAsBoolean();
      f.addProperty("fovY", camera.fov);
      views.add(
          new View(
              f.get("source").getAsString() + " | " + (left ? "left" : "right"),
              firstPersonFrame(faces, f, camera)));
    }
    var close = new ArrayList<View>();
    for (var frame : PaintingEquipmentModelTest.thirdPersonAttachments(model, slim)) {
      var f = frame.getAsJsonObject();
      boolean left = f.get("context").getAsString().contains("LEFT");
      var scene =
          new ArrayList<>(
              transform(faces, matrix(f.getAsJsonArray("item")).translate(-.5f, -.5f, -.5f)));
      for (var entry : f.getAsJsonArray("nativeBodyCubes")) {
        var b = entry.getAsJsonObject();
        String part = b.get("part").getAsString();
        addCube(
            scene,
            b,
            matrix(b.getAsJsonArray("transform")),
            part.endsWith("leg") ? 0x50566d : part.equals("body") ? 0x507e91 : 0xbb9275);
      }
      var view =
          new Matrix4f()
              .translation(0, -.94f, -3.7f)
              .rotateY((float) Math.toRadians(left ? 145 : -145));
      views.add(
          new View(
              "Standing | " + (left ? "left" : "right"),
              PreviewRaster.render(transform(scene, view), camera.width, camera.height, 36)));
      var side =
          new Matrix4f()
              .translation(0, -.94f, -3.7f)
              .rotateY((float) Math.toRadians(left ? -65 : 65));
      views.add(
          new View(
              "Standing reverse | " + (left ? "left" : "right"),
              PreviewRaster.render(transform(scene, side), camera.width, camera.height, 36)));
      var palm = matrix(f.getAsJsonArray("skin")).transformPosition(new Vector3f(0, .5f, 0));
      var detail =
          new Matrix4f()
              .translation(0, 0, -.94f)
              .rotateY((float) Math.toRadians(left ? 145 : -145))
              .translate(palm.negate());
      close.add(
          new View(
              "Grip detail | " + (left ? "left" : "right"),
              PreviewRaster.render(transform(scene, detail), camera.width, camera.height, 36)));
    }
    views.add(new View("Back | fitted", objectView(faces, 180, 0, camera)));
    views.add(new View("Reverse oblique | fitted", objectView(faces, -140, -20, camera)));
    views.addAll(close);
    String name =
        save(
            output,
            id.toString(),
            views,
            camera,
            "Fixed hand viewport "
                + camera.width
                + " x "
                + camera.height
                + " | vertical FOV "
                + camera.fov
                + " | neutral "
                + (slim ? "slim" : "classic")
                + " arm | "
                + kind
                + " | sample tick "
                + tick);
    Files.writeString(
        output.resolve(name.substring(0, name.length() - 4) + ".json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(frames));
    return name;
  }

  private static void gallery(Path output, List<String> sheets) throws IOException {
    StringBuilder html =
        new StringBuilder(
            """
      <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>Offline model previews</title><style>
      body{font:16px system-ui;max-width:1400px;margin:2em auto;padding:0 1em;background:#f1f4f5;color:#20303c}
      main{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(32rem,100%),1fr));gap:1.5em}
      img{width:100%;display:block}a{color:inherit}h2{font-size:1rem}input{margin:0 0 1em .4em;padding:.45em}
      </style><h1>Offline model previews</h1><label>Find a model <input type="search" id="query"></label><main>
      """);
    for (String name : sheets) {
      String id = name.substring(0, name.length() - 4).replaceFirst("/", ":");
      html.append("<section><h2>")
          .append(id)
          .append("</h2><a href='")
          .append(name)
          .append("'><img loading='lazy' alt='")
          .append(id)
          .append(" views' src='")
          .append(name)
          .append("'></a></section>");
    }
    html.append(
        """
      </main><script>document.querySelector('#query').addEventListener('input',event=>{
      for(const section of document.querySelectorAll('section'))section.hidden=!section.querySelector('h2').textContent.includes(event.target.value.trim().toLowerCase())
      })</script></html>
      """);
    Files.writeString(output.resolve("index.html"), html);
  }

  private static List<ResourceLocation> subjects(Path assets, String value) throws IOException {
    if (!value.endsWith(":*")) {
      var id = ResourceLocation.parse(value);
      if (!net.minecraft.FileUtil.decomposePath(id.getNamespace()).isSuccess()
          || !net.minecraft.FileUtil.decomposePath(id.getPath()).isSuccess())
        throw new IllegalArgumentException("Invalid model path " + id);
      if (Files.isRegularFile(
          assets.resolve(id.getNamespace()).resolve("models/item/" + id.getPath() + ".json")))
        return List.of(id);
      var original = HeldItemModels.nativeModel(id.toString());
      var states = new LinkedHashSet<ResourceLocation>();
      if (original.has("overrides"))
        for (var entry : original.getAsJsonArray("overrides")) {
          var target = ResourceLocation.parse(entry.getAsJsonObject().get("model").getAsString());
          if (!target.getPath().startsWith("item/")) continue;
          var leaf = target.withPath(target.getPath().substring(5));
          if (Files.isRegularFile(
              assets
                  .resolve(leaf.getNamespace())
                  .resolve("models/item/" + leaf.getPath() + ".json"))) states.add(leaf);
        }
      if (states.isEmpty()) throw new IOException("No exported held model for " + id);
      return List.copyOf(states);
    }
    String namespace = ResourceLocation.parse(value.replace("*", "all")).getNamespace();
    if (!net.minecraft.FileUtil.decomposePath(namespace).isSuccess())
      throw new IllegalArgumentException("Invalid namespace " + namespace);
    Path held = assets.resolve(namespace).resolve("models/item/equipment/held");
    try (var paths = Files.walk(held)) {
      return paths
          .filter(p -> p.toString().endsWith(".json"))
          .sorted()
          .map(
              p ->
                  ResourceLocation.fromNamespaceAndPath(
                      namespace,
                      held.relativize(p)
                          .toString()
                          .replace(p.getFileSystem().getSeparator(), "/")
                          .replaceFirst("\\.json$", "")))
          .toList();
    }
  }

  private static JsonObject read(Path file) throws IOException {
    return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
  }

  private static int stackTint(ResourceLocation id, int requested) {
    if (!id.getNamespace().equals("hexcasting")) return requested;
    for (String root : List.of("focus", "spellbook", "thought_knot"))
      if (id.getPath().equals(root) || id.getPath().startsWith(root + "_")) return requested;
    return 0xffffff;
  }

  private static BufferedImage sprite(
      JsonObject wrapper, PreviewTextures textures, Camera camera, int tint) throws IOException {
    var layers = wrapper.getAsJsonObject("base").getAsJsonObject("textures");
    if (layers == null || !layers.has("layer0"))
      throw new IOException("The native inventory model needs a different preview adapter");
    var nativeIcon = textures.load(layers.get("layer0").getAsString());
    var image = new BufferedImage(camera.width, camera.height, BufferedImage.TYPE_INT_RGB);
    var draw = image.createGraphics();
    try {
      draw.setColor(new Color(0xdce3ea));
      draw.fillRect(0, 0, camera.width, camera.height);
      int scale =
          Math.max(
              1,
              (int) (camera.height * .68)
                  / Math.max(nativeIcon.getWidth(), nativeIcon.getHeight()));
      int w = nativeIcon.getWidth() * scale, h = nativeIcon.getHeight() * scale;
      int x = (camera.width - w) / 2, y = (camera.height - h) / 2;
      draw.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      draw.drawImage(nativeIcon, x, y, w, h, null);
      if (layers.has("layer1")) {
        var layer = textures.load(layers.get("layer1").getAsString());
        var colored =
            new BufferedImage(layer.getWidth(), layer.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < layer.getHeight(); row++)
          for (int col = 0; col < layer.getWidth(); col++) {
            int source = layer.getRGB(col, row), color = source & 0xff000000;
            for (int shift : new int[] {16, 8, 0})
              color |= (((source >>> shift & 255) * (tint >>> shift & 255) / 255) << shift);
            colored.setRGB(col, row, color);
          }
        draw.drawImage(colored, x, y, w, h, null);
      }
    } finally {
      draw.dispose();
    }
    return image;
  }

  static BufferedImage firstPersonFrame(List<Quad> faces, JsonObject f, Camera camera) {
    var scene =
        new ArrayList<>(
            transform(faces, matrix(f.getAsJsonArray("item")).translate(-.5f, -.5f, -.5f)));
    if (f.has("nativeArmCubes"))
      for (var box : f.getAsJsonArray("nativeArmCubes"))
        addCube(scene, box.getAsJsonObject(), matrix(f.getAsJsonArray("skin")), 0xbb9275);
    return PreviewRaster.render(scene, camera.width, camera.height, camera.fov);
  }

  private static List<Quad> geometry(
      JsonObject model, Path assets, PreviewTextures textures, int tint) throws IOException {
    String loader = model.has("loader") ? model.get("loader").getAsString() : "minecraft:elements";
    var baked =
        switch (loader) {
          case "neoforge:composite" -> VanillaEquipmentModelTest.bakeComposite(model, assets);
          case "neoforge:obj" -> VanillaEquipmentModelTest.bakeObj(model, assets);
          case "minecraft:elements" -> VanillaEquipmentModelTest.bakeCuboids(model);
          default -> throw new IOException("Unsupported preview model loader: " + loader);
        };
    if (baked.isEmpty()) throw new IOException("No baked faces for the selected model");
    return surfaces(model, baked, textures, tint);
  }

  static List<Quad> surfaces(
      JsonObject model,
      List<VanillaEquipmentModelTest.Face> baked,
      PreviewTextures textures,
      int tint)
      throws IOException {
    var result = new ArrayList<Quad>();
    for (var face : baked) {
      String key = face.texture();
      if (key.startsWith("#"))
        key = model.getAsJsonObject("textures").get(key.substring(1)).getAsString();
      var v = new ArrayList<Vertex>();
      for (int i = 0; i < 4; i++) {
        var uv = face.uv().get(i).getAsJsonArray();
        v.add(
            new Vertex(
                face.vertices().get(i), uv.get(0).getAsFloat() / 16, uv.get(1).getAsFloat() / 16));
      }
      result.add(
          new Quad(
              v, textures.load(key), face.alpha() << 24 | (face.tint() == 1 ? tint : 0xffffff)));
    }
    return result;
  }

  private static List<Quad> transform(List<Quad> faces, Matrix4f matrix) {
    return faces.stream().map(q -> q.transformed(matrix)).toList();
  }

  private static BufferedImage objectView(List<Quad> faces, float yaw, float pitch, Camera camera) {
    return PreviewRaster.render(
        transform(faces, studioCamera(faces, yaw, pitch, camera)),
        camera.width,
        camera.height,
        STUDIO_FOV);
  }

  private static final double STUDIO_FOV = 12;

  private static Matrix4f studioCamera(List<Quad> faces, float yaw, float pitch, Camera camera) {
    var lo = new Vector3f(Float.POSITIVE_INFINITY);
    var hi = new Vector3f(Float.NEGATIVE_INFINITY);
    for (var q : faces)
      for (var v : q.vertices()) {
        lo.min(v.position());
        hi.max(v.position());
      }
    var center = new Vector3f(lo).add(hi).mul(.5f);
    var orientation =
        new Matrix4f()
            .rotateX((float) Math.toRadians(pitch))
            .rotateY((float) Math.toRadians(yaw))
            .translate(center.negate());
    var oriented = transform(faces, orientation);
    double vertical = Math.tan(Math.toRadians(STUDIO_FOV * .5)) * .83;
    double horizontal = vertical * camera.width / camera.height;
    double distance = .1;
    for (var face : oriented)
      for (var vertex : face.vertices()) {
        var p = vertex.position();
        distance =
            Math.max(
                distance,
                p.z
                    + Math.max(
                        .06, Math.max(Math.abs(p.x) / horizontal, Math.abs(p.y) / vertical)));
      }
    return new Matrix4f().translation(0, 0, (float) -distance).mul(orientation);
  }

  private static Matrix4f matrix(JsonArray rows) {
    return HeldViewport.fromRows(rows);
  }

  private static void addCube(List<Quad> faces, JsonObject box, Matrix4f matrix, int rgb) {
    var tex = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    tex.setRGB(0, 0, rgb);
    var v = new ArrayList<Vertex>();
    for (int n = 0; n < 8; n++) {
      var p = new Vector3f();
      for (int axis = 0; axis < 3; axis++)
        p.setComponent(
            axis,
            box.getAsJsonArray((n & (1 << axis)) == 0 ? "from" : "to").get(axis).getAsFloat() / 16);
      v.add(new Vertex(matrix.transformPosition(p), 0, 0));
    }
    for (int[] side : CUBE_SIDES)
      faces.add(
          new Quad(
              List.of(v.get(side[0]), v.get(side[1]), v.get(side[2]), v.get(side[3])),
              tex,
              0xffffffff));
  }

  private static final int[][] CUBE_SIDES = {
    {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}
  };

  private static String save(
      Path out, String name, List<View> views, Camera camera, String subtitle) throws IOException {
    int cols = views.size() == 9 ? 3 : 4,
        rows = (views.size() + cols - 1) / cols,
        header = 54,
        label = 26;
    var sheet =
        new BufferedImage(
            cols * camera.width,
            header + rows * (camera.height + label),
            BufferedImage.TYPE_INT_RGB);
    var d = sheet.createGraphics();
    try {
      d.setColor(new Color(0xf2f5f6));
      d.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
      d.setColor(new Color(0x213346));
      d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
      d.drawString(name, 12, 22);
      d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
      d.drawString(subtitle, 12, 44);
      for (int i = 0; i < views.size(); i++) {
        var view = views.get(i);
        int x = i % cols * camera.width, y = header + i / cols * (camera.height + label);
        d.drawString(view.label, x + 8, y + 17);
        d.drawImage(view.image, x, y + label, null);
      }
    } finally {
      d.dispose();
    }
    String file = name.replace(':', '/') + ".png";
    Path destination = out.resolve(file);
    Files.createDirectories(destination.getParent());
    ImageIO.write(sheet, "png", destination.toFile());
    return file;
  }
}
