package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Native state graphs, actual baked faces and functional hand frames of utility equipment. */
public final class UtilityEquipmentModelTest {
  private static final List<String> ROOTS =
      List.of(
          "naturalist:capture_net",
          "spawn:casting_net",
          "chipped:watering_can",
          "chipped:alchemy_book",
          "chipped:chisel",
          "chipped:multimeter",
          "chipped:needles",
          "chipped:saw",
          "littletiles:hammer",
          "hybrid_aquatic:coral_hoe",
          "create:clipboard",
          "create:sand_paper",
          "create:red_sand_paper",
          "create:schematic_and_quill",
          "create:super_glue",
          "simulated:honey_glue",
          "simulated:rope_coupling",
          "create_aeronautics_throwable_rope_connector:throwable_rope_connector",
          "galosphere:echo_bell",
          "galosphere:glow_flare",
          "galosphere:monstrometer",
          "galosphere:palladium_bomb",
          "galosphere:rope_dart",
          "galosphere:saltbound_tablet",
          "galosphere:spectre_flare",
          "galosphere:spectre_bound_spyglass",
          "supplementaries:wrench",
          "toms_storage:wireless_terminal",
          "toms_storage:adv_wireless_terminal",
          "toms_storage:inventory_configurator",
          "toms_storage:paint_kit",
          "sophisticatedcore:ender_linker",
          "sophisticatedstorage:storage_tool",
          "explorerscompass:explorerscompass",
          "naturescompass:naturescompass",
          "legendarysurvivaloverhaul:thermometer",
          "horseman:copper_horn",
          "exposure:interplanar_projector",
          "waystones:warp_stone",
          "immersive_weathering:ice_sickle",
          "immersive_weathering:steel_wool");

  private UtilityEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional baked preview");
    net.neoforged.fml.loading.LoadingModList.of(
        List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
    net.minecraft.SharedConstants.tryDetectVersion();
    net.minecraft.server.Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject preview = new JsonObject();
    int count = 0;
    for (String root : ROOTS) {
      Set<String> states = new LinkedHashSet<>();
      states(root, states);
      var style = NativeEquipmentModels.designs().get(root);
      for (String id : states) {
        JsonObject model = read(assets, id);
        boolean composite =
            model.has("loader") && model.get("loader").getAsString().equals("neoforge:composite");
        List<Face> faces =
            composite
                ? VanillaEquipmentModelTest.bakeComposite(model, assets)
                : model.has("loader")
                    ? VanillaEquipmentModelTest.bakeObj(model, assets)
                    : VanillaEquipmentModelTest.bakeCuboids(model);
        require(!faces.isEmpty(), "Visible utility model: " + id);
        VanillaEquipmentModelTest.noOverlaps(id, faces);
        if (id.endsWith("copper_horn") || id.equals("waystones:warp_stone"))
          VanillaEquipmentModelTest.closedSurface(id, faces);
        if (EquipmentSculpture.readable(style)
            || style == dev.aeroscapes.atmosphere.EquipmentStyle.WRENCH
            || style == dev.aeroscapes.atmosphere.EquipmentStyle.GLUE) readable(id, model);
        if (UtilityEquipmentModels.supports(id) || GalosphereEquipmentModels.supports(id))
          skin(assets, id);
        if (CreateEquipmentModels.supports(id))
          createSkin(
              assets,
              id,
              composite
                  ? VanillaEquipmentModelTest.bakeObj(
                      model.getAsJsonObject("children").getAsJsonObject("rope"), assets)
                  : faces);
        if (id.equals(root) || GalosphereEquipmentModels.supports(id))
          preview.add(id, preview(model, faces));
        count++;
      }
    }
    clawStates(assets);
    for (String id : List.of("spawn:casting_net", "spawn:casting_net_full")) {
      var net = read(assets, id);
      var threads = VanillaEquipmentModelTest.bakeObj(net, assets, "mesh");
      var weights = VanillaEquipmentModelTest.bakeObj(net, assets, "weights");
      for (Face face : weights)
        for (Vector3f point : face.vertices()) {
          double distance = Double.POSITIVE_INFINITY;
          for (Face cord : threads)
            for (Vector3f vertex : cord.vertices())
              distance = Math.min(distance, point.distance(vertex) * 16);
          require(distance < .6, "Every net weight is attached to a thread node: " + id);
        }
    }
    JsonObject storageTool = read(assets, "sophisticatedstorage:storage_tool");
    var wood = VanillaEquipmentModelTest.bakeObj(storageTool, assets, "wood_handle");
    for (String jaw : List.of("left_jaw", "right_jaw"))
      require(
          touches(VanillaEquipmentModelTest.bakeObj(storageTool, assets, jaw), wood),
          "Both dark tool jaws attach to the wooden neck: " + jaw);
    JsonObject watering = read(assets, "chipped:watering_can");
    var rim = VanillaEquipmentModelTest.bakeObj(watering, assets, "rim");
    require(!rim.isEmpty(), "The watering can has an open filling rim");
    double[] vessel = bounds(VanillaEquipmentModelTest.bakeObj(watering, assets, "body"));
    for (Face face : rim)
      for (Vector3f point : face.vertices())
        require(
            point.x * 16 >= vessel[0] - .0001
                && point.x * 16 <= vessel[3] + .0001
                && point.z * 16 >= vessel[2] - .0001
                && point.z * 16 <= vessel[5] + .0001,
            "The filling rim stays on the vessel rather than adding radial fins");
    for (Face face : VanillaEquipmentModelTest.bakeObj(watering, assets, "handle")) {
      double[] handle = bounds(List.of(face));
      if (handle[4] <= vessel[4] + 1)
        require(
            handle[3] <= vessel[0] + (vessel[3] - vessel[0]) * .25
                || handle[0] >= vessel[3] - (vessel[3] - vessel[0]) * .25,
            "The carry handle leaves the filling mouth clear of a lower crossbar");
    }
    JsonObject hoe = read(assets, "hybrid_aquatic:coral_hoe");
    var shaft = VanillaEquipmentModelTest.bakeObj(hoe, assets, "shaft");
    double[] shaftBounds = bounds(shaft);
    require(
        Math.abs(shaftBounds[3] - shaftBounds[0] - 2) < .0001
            && Math.abs(shaftBounds[5] - shaftBounds[2] - 2) < .0001,
        "The coral hoe retains the reference family's square shaft");
    double[] blade = bounds(VanillaEquipmentModelTest.bakeObj(hoe, assets, "blade"));
    require(
        blade[5] - shaftBounds[5] > 6 && blade[3] - blade[0] > 4,
        "The hoe has a broad working blade projecting from the shaft plane");
    connector(assets);
    throwableConnector(assets);
    clipboardStates(assets);
    iceSickle(assets);
    spectreBottle(assets);
    tabletGrip(assets);
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), preview.toString());
    System.out.println(
        "Utility equipment: "
            + ROOTS.size()
            + " roots, "
            + count
            + " native states baked; source skins, readable hand faces, non-overlapping surfaces and connector placement passed");
  }

  private static void clawStates(Path assets) throws IOException {
    String root = "spawn:coastal_crab_claw";
    String water = "spawn:entity/coastal_crab/bubble";
    for (String suffix : List.of("", "_empty", "_shooting0", "_shooting1", "_shooting2")) {
      String id = root + suffix;
      List<Face> faces = VanillaEquipmentModelTest.bakeObj(read(assets, id), assets);
      require(!faces.isEmpty(), "Visible claw in every native state: " + id);
      VanillaEquipmentModelTest.noOverlaps(id, faces);
      require(
          faces.stream()
              .anyMatch(
                  face ->
                      face.texture()
                          .equals(
                              root.replace(":", ":item/")
                                  + (suffix.equals("_empty") ? suffix : ""))),
          "The empty claw uses its native muted shell palette: " + id);
      require(
          faces.stream().anyMatch(face -> face.texture().equals(water))
              == suffix.startsWith("_shooting"),
          "The native bubble appears only while charging: " + id);
    }
  }

  private static void iceSickle(Path assets) throws IOException {
    JsonObject model = read(assets, "immersive_weathering:ice_sickle");
    var blade = VanillaEquipmentModelTest.bakeObj(model, assets, "ice_blade");
    var handle = VanillaEquipmentModelTest.bakeObj(model, assets, "wood_handle");
    require(!blade.isEmpty() && !handle.isEmpty(), "The sickle has a blade and a separate grip");
    // Refined Tools 3.0's hoe/pickaxe use a 2x2 shaft and solid one-unit blades.
    double[] grip = bounds(handle), head = bounds(blade);
    require(
        Math.abs(grip[3] - grip[0] - 2) < .00001 && Math.abs(grip[5] - grip[2] - 2) < .00001,
        "The sickle has the substantial shaft section of the reference tool family");
    require(
        grip[4] - grip[1] >= 14 && grip[4] - grip[1] <= 18,
        "The shaft has ordinary tool proportions rather than a miniature grip");
    var woodTexture = NativeEquipmentModels.image("immersive_weathering:item/ice_sickle");
    for (Face face : handle) {
      for (var coordinates : face.uv()) {
        var uv = coordinates.getAsJsonArray();
        int x = (int) (uv.get(0).getAsDouble() * woodTexture.getWidth() / 16);
        int y = (int) (uv.get(1).getAsDouble() * woodTexture.getHeight() / 16);
        require(
            (woodTexture.getRGB(x, y) >>> 24) == 255,
            "The shaft samples opaque native wood pixels");
      }
      require(
          Math.max(
                  Math.abs(face.normal().x),
                  Math.max(Math.abs(face.normal().y), Math.abs(face.normal().z)))
              > .9999,
          "The reference shaft has flat rectangular sides and square corners");
      for (Vector3f point : face.vertices())
        require(
            Math.min(Math.abs(point.x * 16 - grip[0]), Math.abs(point.x * 16 - grip[3])) < .00001
                && Math.min(Math.abs(point.z * 16 - grip[2]), Math.abs(point.z * 16 - grip[5]))
                    < .00001,
            "Shaft vertices remain on the prism corners instead of a round cross-section");
    }
    require(
        head[5] - head[2] >= 1,
        "The working head has solid volume comparable to the reference hoe blade");
    require(
        head[0] < 7 && head[3] > 15 && head[4] - head[1] > 3,
        "The native short heel, lateral working blade and downward tip remain distinct");
    var socket = VanillaEquipmentModelTest.bakeObj(model, assets, "ice_socket");
    double[] collar = bounds(socket);
    require(
        collar[0] < grip[0]
            && collar[3] > grip[3]
            && collar[2] < grip[2]
            && collar[5] > grip[5]
            && collar[1] < grip[4],
        "A substantial socket wraps the shaft");
    require(
        blade.stream()
            .flatMap(face -> face.vertices().stream())
            .anyMatch(
                point ->
                    point.x * 16 > collar[0]
                        && point.x * 16 < collar[3]
                        && point.y * 16 > collar[1]
                        && point.y * 16 < collar[4]
                        && point.z * 16 > collar[2]
                        && point.z * 16 < collar[5]),
        "The blade root physically enters the socket, without an air gap");
    for (Face face : socket)
      require(
          Math.max(
                  Math.abs(face.normal().x),
                  Math.max(Math.abs(face.normal().y), Math.abs(face.normal().z)))
              > .9999,
          "The tool socket uses broad rectangular faces rather than a rounded collar");
    for (Face face : blade) {
      require(
          Math.abs(face.normal().y) < .00001 || Math.abs(face.normal().z) < .00001,
          "The blade has crisp rectangular sections without smoothing bevels");
      var a = face.vertices().get(0);
      var normal =
          new Vector3f(face.vertices().get(1))
              .sub(a)
              .cross(new Vector3f(face.vertices().get(2)).sub(a))
              .normalize();
      for (Vector3f point : face.vertices())
        require(
            Math.abs(new Vector3f(point).sub(a).dot(normal)) < .00001,
            "Broad blade facets are planar, without folded or twisted quads");
    }
  }

  private static double[] bounds(List<Face> faces) {
    require(!faces.isEmpty(), "Physical component has visible faces");
    double[] bounds = {
      Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
    };
    for (Face face : faces)
      for (Vector3f point : face.vertices())
        for (int axis = 0; axis < 3; axis++) {
          double value = point.get(axis) * 16;
          bounds[axis] = Math.min(bounds[axis], value);
          bounds[axis + 3] = Math.max(bounds[axis + 3], value);
        }
    return bounds;
  }

  private static boolean touches(List<Face> part, List<Face> base) {
    double distance = Double.POSITIVE_INFINITY;
    for (Face face : part)
      for (Vector3f point : face.vertices())
        for (Face target : base)
          for (int edge = 0; edge < 4; edge++) {
            Vector3f a = target.vertices().get(edge),
                b = target.vertices().get((edge + 1) % 4),
                direction = new Vector3f(b).sub(a);
            if (direction.lengthSquared() == 0) continue;
            float along =
                Math.clamp(
                    new Vector3f(point).sub(a).dot(direction) / direction.lengthSquared(), 0, 1);
            distance =
                Math.min(distance, point.distance(new Vector3f(a).fma(along, direction)) * 16);
          }
    return distance < .001;
  }

  @SuppressWarnings("deprecation") // Exercises the pinned provider's 1.21.1 item transforms.
  private static void connector(Path assets) throws IOException {
    JsonObject actual =
        JsonParser.parseString(
                Files.readString(assets.resolve("simulated/models/item/rope_connector.json")))
            .getAsJsonObject();
    JsonObject original = HeldItemModels.nativeModel("simulated:rope_connector");
    require(
        actual.get("parent").equals(original.get("parent")),
        "The connector retains its native mesh and non-hand presentation");
    String path =
        "/assets/" + original.get("parent").getAsString().replace(":", "/models/") + ".json";
    JsonObject source;
    try (var stream = UtilityEquipmentModelTest.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing native connector model: " + path);
      try (var reader =
          new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
        source = JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
    JsonObject replacement = source.deepCopy();
    for (var hand : actual.getAsJsonObject("display").entrySet())
      replacement.getAsJsonObject("display").add(hand.getKey(), hand.getValue());
    var nativeModel =
        net.minecraft.client.renderer.block.model.BlockModel.fromString(source.toString());
    var held =
        net.minecraft.client.renderer.block.model.BlockModel.fromString(replacement.toString());
    double minimum =
        nativeModel.getElements().stream().mapToDouble(part -> part.from.y).min().orElseThrow();
    double maximum =
        nativeModel.getElements().stream().mapToDouble(part -> part.to.y).max().orElseThrow();
    for (var context :
        List.of(
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      boolean left =
          context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      var reference = new com.mojang.blaze3d.vertex.PoseStack();
      var corrected = new com.mojang.blaze3d.vertex.PoseStack();
      nativeModel.getTransforms().getTransform(context).apply(left, reference);
      held.getTransforms().getTransform(context).apply(left, corrected);
      Vector3f target = reference.last().pose().transformPosition(new Vector3f());
      Vector3f center =
          corrected
              .last()
              .pose()
              .transformPosition(new Vector3f(0, (float) (((minimum + maximum) / 2 - 8) / 16), 0));
      require(
          center.distance(target) < .00001,
          "The low connector center reaches its native hand anchor in " + context);
    }
  }

  private static void states(String id, Set<String> states) throws IOException {
    if (!states.add(id)) return;
    var original = HeldItemModels.nativeModel(id);
    if (original.has("overrides"))
      for (var override : original.getAsJsonArray("overrides"))
        states(
            override.getAsJsonObject().get("model").getAsString().replace(":item/", ":"), states);
  }

  private static void throwableConnector(Path assets) throws IOException {
    var model =
        read(assets, "create_aeronautics_throwable_rope_connector:throwable_rope_connector");
    var nativeRoot =
        JsonParser.parseString(
                Files.readString(assets.resolve("simulated/models/item/rope_connector.json")))
            .getAsJsonObject();
    require(
        model
            .getAsJsonObject("children")
            .getAsJsonObject("connector")
            .get("parent")
            .equals(nativeRoot.get("parent")),
        "The throwable connector uses the very same provider hardware, UVs and materials");
    require(
        model.get("display").equals(nativeRoot.get("display")),
        "Both connectors retain the same physical scale and hand pose");
  }

  private static void skin(Path assets, String id) throws IOException {
    String reference =
        NativeEquipmentModels.inherited(id).getAsJsonObject("textures").get("layer0").getAsString();
    if (id.contains("compass:")) reference = reference.replaceAll("_[0-9]{2}$", "_16");
    var image =
        id.equals("chipped:saw")
            ? javax.imageio.ImageIO.read(
                assets
                    .resolve("aeroscapes_reimagine/textures/item/utility_equipment/saw.png")
                    .toFile())
            : NativeEquipmentModels.image(reference);
    if (image == null) throw new IOException("Unreadable material image for " + id);
    var regions =
        GalosphereEquipmentModels.supports(id)
            ? GalosphereEquipmentModels.regions(id)
            : UtilityEquipmentModels.regions(id);
    for (var entry : regions.entrySet()) {
      if (entry.getKey().equals("native")) continue;
      String materialTexture =
          GalosphereEquipmentModels.materialTexture(id, entry.getKey(), reference);
      var materialImage =
          materialTexture.equals(reference) ? image : NativeEquipmentModels.image(materialTexture);
      int frameHeight = Math.min(materialImage.getWidth(), materialImage.getHeight());
      double xScale = materialImage.getWidth() / 16.0, yScale = frameHeight / 16.0;
      double[] region = entry.getValue();
      for (int frame = 0; frame < materialImage.getHeight() / frameHeight; frame++)
        for (int y = (int) (region[1] * yScale); y < region[3] * yScale; y++)
          for (int x = (int) (region[0] * xScale); x < region[2] * xScale; x++)
            require(
                (materialImage.getRGB(x, y + frame * frameHeight) >>> 24) == 255,
                "A physical material cannot cut holes in its opaque shell: "
                    + id
                    + "/"
                    + entry.getKey());
    }
  }

  private static void spectreBottle(Path assets) throws IOException {
    var model = read(assets, "galosphere:spectre_flare");
    require(
        model.get("loader").getAsString().equals("neoforge:composite"),
        "The bottled spectre needs separate opaque and translucent item passes");
    for (String child : List.of("body", "glass")) {
      var faces =
          VanillaEquipmentModelTest.bakeObj(
              model.getAsJsonObject("children").getAsJsonObject(child), assets);
      require(
          !faces.isEmpty(), "The native OBJ baker loads the spectral bottle component: " + child);
      if (child.equals("glass"))
        VanillaEquipmentModelTest.closedSurface("galosphere:spectre_flare/glass", faces);
    }
    var sprites = singleAtlasSprites(assets);
    var body =
        VanillaEquipmentModelTest.bakeObjWithAtlas(
            model.getAsJsonObject("children").getAsJsonObject("body"), assets, sprites.keySet());
    require(
        body.stream().noneMatch(face -> face.texture().equals("minecraft:missingno")),
        "Bottled Spectre resolves actual atlas sprites instead of black missing-texture crops");
    var location =
        net.minecraft.resources.ResourceLocation.parse("galosphere:entity/spectre/spectre");
    var resource = sprites.get(location);
    require(resource != null, "The native single-file atlas source loads the Spectre texture");
    java.awt.image.BufferedImage image;
    try (var input = resource.open()) {
      image = javax.imageio.ImageIO.read(input);
    }
    if (image == null) throw new IOException("Invalid Spectre atlas PNG");
    boolean pale = false;
    for (Face face : body) {
      if (!face.texture().equals(location.toString())) continue;
      double u = 0, v = 0;
      for (var vertex : face.uv()) {
        u += vertex.getAsJsonArray().get(0).getAsDouble() / 4;
        v += vertex.getAsJsonArray().get(1).getAsDouble() / 4;
      }
      int color =
          image.getRGB(
              Math.clamp((int) (u * image.getWidth() / 16), 0, image.getWidth() - 1),
              Math.clamp((int) (v * image.getHeight() / 16), 0, image.getHeight() - 1));
      pale |= ((color >> 16) & 255) + ((color >> 8) & 255) + (color & 255) > 600;
    }
    require(
        pale,
        "The same baked Spectre UVs resolve its pale artwork through the registered atlas sprite");
  }

  @SuppressWarnings(
      "deprecation") // Exercise native ItemTransform mirroring and the actual hand attachment.
  private static void tabletGrip(Path assets) throws IOException {
    for (String id :
        List.of(
            "galosphere:saltbound_tablet",
            "galosphere:saltbound_tablet_using",
            "galosphere:saltbound_tablet_cooldown")) {
      JsonObject display = new JsonObject();
      display.add("display", read(assets, id).get("display"));
      var model =
          net.minecraft.client.renderer.block.model.BlockModel.fromString(display.toString());
      for (var context :
          List.of(
              net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
              net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
              net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
              net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
        boolean left = context.name().endsWith("LEFT_HAND");
        float side = left ? -1 : 1;
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        pose.translate(side / 16, .125, -.625);
        model.getTransforms().getTransform(context).apply(left, pose);
        var grip =
            pose.last()
                .pose()
                .transformPosition(new Vector3f(left ? 5 : 8, 4.5f, 8).sub(8, 8, 8).div(16));
        require(
            grip.distance(new Vector3f(-side / 16, .5f, 0)) < .00001,
            "Tablet uses the corrected right palm grip and preserves the accepted left grip: "
                + id
                + "/"
                + context);
      }
    }
  }

  private static java.util.Map<
          net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource>
      singleAtlasSprites(Path assets) throws IOException {
    var resources =
        new java.util.HashMap<
            net.minecraft.resources.ResourceLocation,
            net.minecraft.server.packs.resources.Resource>();
    var manager =
        (net.minecraft.server.packs.resources.ResourceManager)
            java.lang.reflect.Proxy.newProxyInstance(
                UtilityEquipmentModelTest.class.getClassLoader(),
                new Class<?>[] {net.minecraft.server.packs.resources.ResourceManager.class},
                (proxy, method, arguments) -> {
                  if (!method.getName().equals("getResource")
                      || arguments == null
                      || !(arguments[0]
                          instanceof net.minecraft.resources.ResourceLocation location))
                    throw new UnsupportedOperationException(
                        "Unexpected atlas resource operation: " + method.getName());
                  byte[] bytes;
                  try (var input =
                      UtilityEquipmentModelTest.class.getResourceAsStream(
                          "/assets/" + location.getNamespace() + "/" + location.getPath())) {
                    if (input == null) return java.util.Optional.empty();
                    bytes = input.readAllBytes();
                  }
                  // The native single-file source only opens bytes; this fixture has no pack
                  // metadata consumer.
                  return java.util.Optional.of(
                      new net.minecraft.server.packs.resources.Resource(
                          null, () -> new java.io.ByteArrayInputStream(bytes)));
                });
    var output =
        new net.minecraft.client.renderer.texture.atlas.SpriteSource.Output() {
          @Override
          public void add(
              net.minecraft.resources.ResourceLocation location,
              net.minecraft.server.packs.resources.Resource resource) {
            resources.put(location, resource);
          }

          @Override
          public void add(
              net.minecraft.resources.ResourceLocation location,
              net.minecraft.client.renderer.texture.atlas.SpriteSource.SpriteSupplier supplier) {
            throw new UnsupportedOperationException("Expected a native single-file resource");
          }

          @Override
          public void removeAll(
              java.util.function.Predicate<net.minecraft.resources.ResourceLocation> predicate) {
            resources.keySet().removeIf(predicate);
          }
        };
    var atlas =
        JsonParser.parseString(Files.readString(assets.resolve("minecraft/atlases/blocks.json")))
            .getAsJsonObject();
    for (var entry : atlas.getAsJsonArray("sources")) {
      var definition = entry.getAsJsonObject();
      if (!definition.get("type").getAsString().equals("minecraft:single")) continue;
      var source =
          net.minecraft.client.renderer.texture.atlas.sources.SingleFile.CODEC
              .codec()
              .parse(com.mojang.serialization.JsonOps.INSTANCE, definition)
              .getOrThrow();
      source.run(manager, output);
    }
    return resources;
  }

  private static void readable(String id, JsonObject model) {
    for (boolean left : new boolean[] {false, true}) {
      JsonObject transform =
          model
              .getAsJsonObject("display")
              .getAsJsonObject("thirdperson_" + (left ? "lefthand" : "righthand"));
      JsonArray angles = transform.getAsJsonArray("rotation");
      Matrix4f pose =
          new Matrix4f()
              .rotateZ(radians(180))
              .rotateZ(radians(left ? 9 : -8.56324))
              .rotateY(radians(left ? 4 : -6.46188))
              .rotateX(radians(left ? -100 : -100.03983))
              .rotateX(radians(-90))
              .rotateY(radians(180))
              .rotateXYZ(
                  radians(angles.get(0).getAsDouble()),
                  radians(angles.get(1).getAsDouble() * (left ? -1 : 1)),
                  radians(angles.get(2).getAsDouble() * (left ? -1 : 1)));
      require(
          pose.transformDirection(new Vector3f(0, 0, 1)).z > .9,
          "The authored front faces the owner in both Punchy hand frames: " + id);
    }
  }

  private static void createSkin(Path assets, String id, List<Face> faces) throws IOException {
    var source =
        NativeEquipmentModels.image(
            NativeEquipmentModels.inherited(id)
                .getAsJsonObject("textures")
                .get("layer0")
                .getAsString());
    Set<Integer> palette = new LinkedHashSet<>();
    for (int y = 0; y < source.getHeight(); y++)
      for (int x = 0; x < source.getWidth(); x++)
        if ((source.getRGB(x, y) >>> 24) == 255) palette.add(source.getRGB(x, y));
    require(
        palette.containsAll(sampledColors(assets, faces)),
        "Physical faces sample opaque native-palette materials: " + id);
  }

  private static void clipboardStates(Path assets) throws IOException {
    for (String id : List.of("create:clipboard_0", "create:clipboard_1", "create:clipboard_2")) {
      var page = VanillaEquipmentModelTest.bakeObj(read(assets, id), assets, "page");
      int colors = sampledColors(assets, page).size();
      require(
          id.endsWith("_0") ? colors == 1 : colors > 1,
          "The empty and written clipboard pages remain visually distinct: " + id);
      var quill = VanillaEquipmentModelTest.bakeObj(read(assets, id), assets, "quill");
      require(
          id.endsWith("_2") != quill.isEmpty(),
          "The editing state alone displays the physical quill: " + id);
    }
  }

  private static Set<Integer> sampledColors(Path assets, List<Face> faces) throws IOException {
    Set<Integer> colors = new LinkedHashSet<>();
    var textures = new java.util.HashMap<String, java.awt.image.BufferedImage>();
    for (Face face : faces) {
      var image = textures.get(face.texture());
      if (image == null) {
        image =
            javax.imageio.ImageIO.read(
                assets.resolve(face.texture().replace(":", "/textures/") + ".png").toFile());
        if (image == null) throw new IOException("Unreadable baked texture: " + face.texture());
        textures.put(face.texture(), image);
      }
      for (int row = 0; row <= 8; row++)
        for (int column = 0; column <= 8; column++) {
          double[] coordinates = new double[2];
          double u = column / 8.0, v = row / 8.0;
          double[] weights = {(1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v};
          for (int corner = 0; corner < 4; corner++)
            for (int axis = 0; axis < 2; axis++)
              coordinates[axis] +=
                  weights[corner] * face.uv().get(corner).getAsJsonArray().get(axis).getAsDouble();
          int
              x =
                  Math.clamp(
                      (int) (coordinates[0] * image.getWidth() / 16), 0, image.getWidth() - 1),
              y =
                  Math.clamp(
                      (int) (coordinates[1] * image.getHeight() / 16), 0, image.getHeight() - 1);
          colors.add(image.getRGB(x, y));
        }
    }
    return colors;
  }

  private static JsonObject read(Path assets, String id) throws IOException {
    return JsonParser.parseString(
            Files.readString(
                assets.resolve(id.replace(":", "/models/item/equipment/held/") + ".json")))
        .getAsJsonObject();
  }

  private static JsonObject preview(JsonObject model, List<Face> faces) {
    JsonObject result = new JsonObject();
    result.add("textures", model.get("textures"));
    result.add("display", model.get("display"));
    JsonArray quads = new JsonArray();
    for (Face face : faces) {
      JsonObject quad = new JsonObject();
      quad.addProperty("texture", face.texture());
      quad.addProperty("alpha", face.alpha() / 255.0);
      JsonArray vertices = new JsonArray();
      for (Vector3f point : face.vertices())
        vertices.add(vector(point.x * 16, point.y * 16, point.z * 16));
      quad.add("vertices", vertices);
      quad.add("uv", face.uv());
      quad.add("normal", vector(face.normal().x, face.normal().y, face.normal().z));
      quads.add(quad);
    }
    result.add("quads", quads);
    return result;
  }

  private static JsonArray vector(float... values) {
    JsonArray result = new JsonArray();
    for (float value : values) result.add(value);
    return result;
  }

  private static float radians(double value) {
    return (float) Math.toRadians(value);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
