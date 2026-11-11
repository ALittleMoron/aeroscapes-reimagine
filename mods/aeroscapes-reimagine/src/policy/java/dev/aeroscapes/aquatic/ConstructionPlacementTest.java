package dev.aeroscapes.aquatic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aeroscapes.atmosphere.HeldEquipmentGrip;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import punchy.client.render.PunchyArmRenderer;
import punchy.client.render.ToolKindResolver;
import punchy.client.render.ToolTuning;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;
import punchy.config.PunchyTuningConfig;

/**
 * Tests selected display consumers and full native hand contact, without a client or camera claim.
 */
public final class ConstructionPlacementTest {
  private ConstructionPlacementTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional baked preview output");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    for (String id :
        List.of(
            "minecraft:brush",
            "littletiles:paint_brush",
            "littletiles:bag",
            "betterarcheology:iron_brush",
            "betterarcheology:diamond_brush",
            "betterarcheology:netherite_brush",
            "spawn:casting_net",
            "nutritionalbalance:lunchbox"))
      require(
          HeldEquipmentGrip.usesPalm(ResourceLocation.parse(id)),
          "Selected palm attachment: " + id);
    for (String id :
        List.of(
            "minecraft:iron_sword",
            "littletiles:saw",
            "littletiles:wrench",
            "littletiles:measure_tape",
            "littletiles:glove",
            "hapi:creature_net"))
      require(
          !HeldEquipmentGrip.usesPalm(ResourceLocation.parse(id)),
          "Unselected attachment retained: " + id);
    JsonObject previews = new JsonObject();
    for (String item :
        List.of("paint_brush", "bag", "saw", "wrench", "measure_tape", "chisel", "glove")) {
      JsonObject model =
          read(assets.resolve("littletiles/models/item/equipment/held/" + item + ".json"));
      var faces =
          model.has("loader")
              ? VanillaEquipmentModelTest.bakeObj(model, assets)
              : physicalCuboids(model, assets, item);
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      preview.add("attachments", attachments(model, item, ToolKind.INGREDIENT));
      previews.add("littletiles:" + item, preview);
    }
    JsonObject net = read(assets.resolve("hapi/models/item/equipment/creature_net_held.json"));
    JsonObject provider = HeldItemModels.nativeModel("hapi:creature_net");
    provider.add("display", net.get("display"));
    JsonObject netPreview =
        VanillaEquipmentModelTest.preview(
            provider, physicalCuboids(provider, assets, "creature_net"));
    netPreview.add("attachments", attachments(net, "creature_net", ToolKind.INGREDIENT));
    previews.add("hapi:creature_net", netPreview);
    for (String item : List.of("casting_net", "casting_net_full")) {
      JsonObject castingNet =
          read(assets.resolve("spawn/models/item/equipment/held/" + item + ".json"));
      JsonArray poses = attachments(castingNet, item, ToolKind.INGREDIENT);
      castingNetCamera(
          VanillaEquipmentModelTest.bakeObj(castingNet, assets, "weighted_hem"), poses);
      JsonObject preview =
          VanillaEquipmentModelTest.preview(
              castingNet, VanillaEquipmentModelTest.bakeObj(castingNet, assets));
      preview.add("attachments", poses);
      previews.add("spawn:" + item, preview);
    }
    Path lunchboxAssets =
        Path.of(arguments[0])
            .resolve(
                "pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets/nutritionalbalance");
    JsonObject lunchbox = read(lunchboxAssets.resolve("models/item/lunchbox.json"));
    JsonObject lunchboxMesh =
        read(lunchboxAssets.resolve("models/item/equipment/held/lunchbox.json"));
    JsonObject lunchboxPreview =
        VanillaEquipmentModelTest.preview(
            lunchboxMesh, VanillaEquipmentModelTest.bakeCuboids(lunchboxMesh));
    lunchboxPreview.add("attachments", attachments(lunchbox, "lunchbox", ToolKind.INGREDIENT));
    previews.add("nutritionalbalance:lunchbox", lunchboxPreview);
    directions(
        net,
        read(assets.resolve("littletiles/models/item/equipment/held/measure_tape.json")),
        read(assets.resolve("littletiles/models/item/equipment/held/saw.json")));
    for (var entry : aquaticKinds().entrySet()) {
      String item = entry.getKey();
      JsonObject model =
          read(assets.resolve("hybrid_aquatic/models/item/equipment/held/" + item + ".json"));
      JsonObject preview =
          VanillaEquipmentModelTest.preview(
              model, VanillaEquipmentModelTest.bakeObj(model, assets));
      preview.add("attachments", attachments(model, item, entry.getValue()));
      preview.addProperty("nativeToolKind", entry.getValue().name());
      previews.add("hybrid_aquatic:" + item, preview);
    }
    theurgyModels(assets, previews);
    engineeringModels(assets, previews);
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Construction placement: scoped palm selection, native hand contact, selected Punchy contexts, forward tape and native net pouch direction passed");
  }

  private static void engineeringModels(Path assets, JsonObject previews)
      throws IOException, ReflectiveOperationException {
    for (String id :
        List.of(
            "createbigcannons:block_armor_inspection_tool",
            "createbigcannons:gas_mask",
            "rsgauges:switchlink_pearl")) {
      String[] parts = id.split(":", 2);
      String name = parts[1];
      Path models = assets.resolve(parts[0] + "/models/item");
      JsonObject root = read(models.resolve(name + ".json"));
      JsonObject base = root.getAsJsonObject("base");
      require(
          base.get("parent").getAsString().equals("minecraft:item/generated")
              && base.getAsJsonObject("textures")
                  .get("layer0")
                  .getAsString()
                  .equals(parts[0] + ":item/" + name),
          "Native inventory icon: " + id);
      JsonObject model = read(models.resolve("equipment/held/" + name + ".json"));
      var faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      require(!faces.isEmpty(), "Visible engineering equipment: " + id);
      VanillaEquipmentModelTest.noOverlaps(id, faces);
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      preview.add("attachments", attachments(model, name, ToolKind.INGREDIENT));
      previews.add(id, preview);
    }
  }

  private static void theurgyModels(Path assets, JsonObject previews)
      throws IOException, ReflectiveOperationException {
    for (String rod : TheurgyHeldModels.RODS) {
      theurgyModel(assets, previews, rod, rod + "/0");
      theurgyModel(assets, previews, rod, rod + "/searching");
    }
    theurgyModel(assets, previews, "mercurial_wand", "mercurial_wand");
    ConstructionIconRenderingTest.verifyHermetica(assets);
    theurgyModel(assets, previews, "the_hermetica_icon", "the_hermetica_icon");
  }

  private static void theurgyModel(Path assets, JsonObject previews, String item, String state)
      throws IOException, ReflectiveOperationException {
    JsonObject child =
        read(assets.resolve("theurgy/models/item/equipment/held/" + state + ".json"));
    JsonObject model = child;
    if (child.has("parent")) {
      String geometry = child.get("parent").getAsString().replace(":", "/models/");
      model = read(assets.resolve(geometry + ".json"));
      model.add("textures", child.get("textures"));
    }
    var faces = physicalCuboids(model, assets, state);
    require(!faces.isEmpty(), "Theurgy held state has volume: " + state);
    JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
    preview.add("attachments", attachments(model, item, ToolKind.INGREDIENT));
    previews.add("theurgy:" + state, preview);
  }

  private static JsonArray attachments(JsonObject source, String item, ToolKind kind)
      throws IOException, ReflectiveOperationException {
    return attachments(source, item, kind, 0);
  }

  @SuppressWarnings("deprecation") // Apply the exact ItemTransform consumer used by ItemRenderer.
  static JsonArray attachments(JsonObject source, String item, ToolKind kind, float armOffsetY)
      throws IOException, ReflectiveOperationException {
    JsonArray frames;
    try (var stream =
        ConstructionPlacementTest.class.getResourceAsStream("/held-arm-frames.json")) {
      if (stream == null) throw new IOException("Missing recorded arm frames");
      frames =
          JsonParser.parseReader(
                  new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonArray();
    }
    var model = handModel(source);
    JsonArray attachments = new JsonArray();
    boolean castingNet = item.equals("casting_net") || item.equals("casting_net_full");
    boolean lunchbox = item.equals("lunchbox");
    boolean palm = castingNet || lunchbox || item.equals("paint_brush") || item.equals("bag");
    for (boolean left : new boolean[] {false, true})
      for (boolean slim : new boolean[] {false, true})
        for (int index = 0; index < frames.size(); index++) {
          Matrix4f frame = new Matrix4f();
          var rows = frames.get(index).getAsJsonArray();
          for (int row = 0; row < 4; row++)
            for (int column = 0; column < 4; column++)
              frame.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
          var nativeRoot =
              LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
                  .bakeRoot();
          ModelPart arm = nativeRoot.getChild(left ? "left_arm" : "right_arm");
          arm.setPos(frame.m30(), frame.m31() + armOffsetY * 16, frame.m32());
          arm.xRot = (float) Math.atan2(frame.m12(), frame.m22());
          arm.yRot = (float) Math.asin(-frame.m02());
          arm.zRot = (float) Math.atan2(frame.m01(), frame.m00());
          Matrix4f entryRoot = new Matrix4f().rotateZ((float) Math.PI);
          PoseStack skin = new PoseStack();
          skin.mulPose(entryRoot);
          if (slim) skin.translate((left ? 1 : -1) * .5f / 16, 0, 0);
          arm.translateAndRotate(skin);
          ItemDisplayContext context =
              left
                  ? (palm
                      ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                      : ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                  : (palm
                      ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                      : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
          PoseStack held =
              palm
                  ? HeldEquipmentGrip.palm(entryRoot, frame, left, slim)
                  : neutralPunchy(skin.last().pose(), left, kind);
          model.getTransforms().getTransform(context).apply(left, held);
          Matrix4f relative = new Matrix4f(skin.last().pose()).invert().mul(held.last().pose());
          if (item.equals("seashell_hoe"))
            require(
                relative.transformDirection(new Vector3f(0, 0, 1)).normalize().y > .999f,
                "The native shell hoe tip points forward through the complete HOE attachment in both hands");
          if (kind == ToolKind.AXE || kind == ToolKind.PICKAXE) {
            Vector3f working = new Vector3f(kind == ToolKind.AXE ? 1 : -1, 0, 0);
            require(
                relative.transformDirection(working).normalize().y > .9f,
                "The working axe/pick tip extends forward through the actual mirrored category consumer: "
                    + item);
          }
          if (lunchbox) {
            lunchboxCarry(relative, held.last().pose(), arm, index == (left ? 0 : 2));
          } else if (palm) {
            Vector3f grip =
                castingNet
                    ? new Vector3f(5.6f, 8.35f, 8)
                    : new Vector3f(8, item.equals("bag") ? 3 : 3.5f, 8);
            Vector3f contact = relative.transformPosition(grip.sub(8, 8, 8).div(16));
            Vector3f expected =
                new Vector3f(
                    (left ? 1 : -1) * (slim ? .5f : 1) / 16, .5f, castingNet ? -1.5f / 16 : 0);
            require(
                contact.distance(expected) < .00001f, "Grip follows the actual arm frame: " + item);
            if (item.equals("paint_brush"))
              for (float x : new float[] {6.1f, 9.9f})
                for (float z : new float[] {6.9f, 9.1f})
                  require(
                      relative.transformPosition(new Vector3f(x, 8.5f, z).sub(8, 8, 8).div(16)).z
                          < -2.05f / 16,
                      "Ferrule clears the full native fist, leaving visible grip");
            else if (item.equals("bag"))
              bagCarry(relative, held.last().pose(), arm, contact, index == (left ? 0 : 2));
          }
          if (!slim && index == (left ? 0 : 2)) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("context", context.name());
            attachment.addProperty("nativeToolKind", kind.name());
            attachment.add("nativeItemTuning", new Gson().toJsonTree(tuning(kind, left)));
            if (AquaticToolModels.ITEMS.contains(item)) {
              Vector3f grip = relative.transformPosition(new Vector3f(0, -3f / 16, 0)).mul(16);
              attachment.add("gripInArmPixels", vector(grip.x, grip.y, grip.z));
            }
            attachment.addProperty(
                "consumer",
                palm
                    ? "live-palm bridge; recorded arm frame"
                    : "Punchy neutral native grip+" + kind.name() + " tuning; recorded arm frame");
            attachment.add("item", matrix(held.last().pose()));
            attachment.add("skin", matrix(skin.last().pose()));
            JsonArray cubes = new JsonArray();
            arm.visit(
                new PoseStack(),
                (pose, path, cubeIndex, cube) -> {
                  JsonObject bounds = new JsonObject();
                  bounds.add("from", vector(cube.minX, cube.minY, cube.minZ));
                  bounds.add("to", vector(cube.maxX, cube.maxY, cube.maxZ));
                  cubes.add(bounds);
                });
            attachment.add("nativeArmCubes", cubes);
            attachments.add(attachment);
          }
        }
    return attachments;
  }

  private static void lunchboxCarry(
      Matrix4f relative, Matrix4f camera, ModelPart arm, boolean neutral) {
    Vector3f base = new Vector3f(0, -6f / 16, 0);
    Vector3f support = relative.transformPosition(new Vector3f(base));
    boolean[] contact = new boolean[1];
    arm.visit(new PoseStack(), (pose, path, index, cube) -> contact[0] |= contains(cube, support));
    require(contact[0] && support.y >= 6f / 16, "The lunchbox base rests on the animated fist");
    if (!neutral) return;
    var low = camera.transformPosition(base);
    var high = camera.transformPosition(new Vector3f(0, .25f, 0));
    var leftEdge = camera.transformPosition(new Vector3f(-.25f, .06f, .1875f));
    var rightEdge = camera.transformPosition(new Vector3f(.25f, .06f, .1875f));
    double width = Math.abs(rightEdge.x / rightEdge.z - leftEdge.x / leftEdge.z);
    double rise = Math.abs(rightEdge.y / rightEdge.z - leftEdge.y / leftEdge.z);
    require(
        high.z < 0 && low.z < 0 && high.y / -high.z > low.y / -low.z && rise < width * .3,
        "The lunchbox stands upright across the neutral viewport in both hands");
  }

  private static void castingNetCamera(List<VanillaEquipmentModelTest.Face> hem, JsonArray poses) {
    require(!hem.isEmpty(), "The net has a visible weighted hem");
    double vertical = Math.tan(Math.toRadians(35)), horizontal = vertical * 16 / 9;
    for (var pose : poses) {
      JsonArray rows = pose.getAsJsonObject().getAsJsonArray("item");
      Matrix4f transform = new Matrix4f();
      for (int row = 0; row < 4; row++)
        for (int column = 0; column < 4; column++)
          transform.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
      for (var face : hem)
        for (var vertex : face.vertices()) {
          var point = transform.transformPosition(new Vector3f(vertex).sub(.5f, .5f, .5f));
          require(
              point.z < 0
                  && Math.abs(point.y) < -point.z * vertical
                  && Math.abs(point.x) < -point.z * horizontal,
              "The casting net's hem stays inside the recorded neutral first-person viewport");
        }
    }
  }

  private static void bagCarry(
      Matrix4f relative, Matrix4f camera, ModelPart arm, Vector3f support, boolean neutralFrame) {
    Vector3f mouth = relative.transformPosition(new Vector3f(0, 4f / 16, 0));
    boolean[] contact = new boolean[2];
    arm.visit(
        new PoseStack(),
        (pose, path, cubeIndex, cube) -> {
          contact[0] |= contains(cube, support);
          contact[1] |= contains(cube, mouth);
        });
    require(
        contact[0] && !contact[1], "The native fist supports the pouch below its gathered mouth");
    if (neutralFrame) {
      Vector3f cameraMouth = camera.transformPosition(new Vector3f(0, 4f / 16, 0));
      Vector3f cameraBelly = camera.transformPosition(new Vector3f(0, -3f / 16, 0));
      require(
          cameraMouth.z < 0
              && cameraBelly.z < 0
              && cameraMouth.y > cameraBelly.y
              && cameraMouth.y / -cameraMouth.z > cameraBelly.y / -cameraBelly.z,
          "The selected live-palm camera keeps the gathered mouth above the pouch belly in both hands");
    }
  }

  private static boolean contains(ModelPart.Cube cube, Vector3f point) {
    return point.x >= cube.minX / 16
        && point.x <= cube.maxX / 16
        && point.y >= cube.minY / 16
        && point.y <= cube.maxY / 16
        && point.z >= cube.minZ / 16
        && point.z <= cube.maxZ / 16;
  }

  private static PoseStack neutralPunchy(Matrix4f skin, boolean left, ToolKind kind)
      throws ReflectiveOperationException, IOException {
    PoseStack pose = new PoseStack();
    pose.mulPose(skin);
    pose.mulPose(Axis.XP.rotationDegrees(-90));
    pose.mulPose(Axis.YP.rotationDegrees(180));
    pose.translate((left ? -1 : 1) / 16f, .125f, -.625f);
    ModelPart grip = new ModelPart(List.of(), Map.of());
    var tuning = tuning(kind, left);
    // Native itemgrip and item share the same static pivot; the relative grip is zero.
    invoke(
        "applyGripTranslation",
        new Class<?>[] {PoseStack.class, Vector3f.class},
        pose,
        new Vector3f());
    Vector3f pivot =
        (Vector3f)
            invoke(
                "getTuningPivot",
                new Class<?>[] {ToolTuning.Profile.class, boolean.class},
                tuning,
                left);
    invoke(
        "applyGripLocalTransform",
        new Class<?>[] {PoseStack.class, ModelPart.class, Vector3f.class},
        pose,
        grip,
        pivot);
    pose.scale(tuning.scale, tuning.scale, tuning.scale);
    pose.translate(tuning.pivotX, tuning.pivotY, tuning.pivotZ);
    Vector3f marker = new Vector3f(pivot).sub(tuning.pivotX, tuning.pivotY, tuning.pivotZ);
    invoke(
        "applyItemPreTransform",
        new Class<?>[] {PoseStack.class, Vector3f.class, ToolTuning.Profile.class, boolean.class},
        pose,
        marker,
        tuning,
        false);
    invoke(
        "applyItemTranslation",
        new Class<?>[] {PoseStack.class, ToolTuning.Profile.class, boolean.class},
        pose,
        tuning,
        left);
    return pose;
  }

  private static Object invoke(String name, Class<?>[] types, Object... arguments)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Method method = PunchyArmRenderer.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, arguments);
  }

  private static ToolTuning.Profile tuning(ToolKind kind, boolean left) throws IOException {
    ToolTuning.resetDefaults();
    JsonObject template;
    try (var stream =
        ConstructionPlacementTest.class.getResourceAsStream(
            "/resourcepacks/punchy/assets/minecraft/punchy/compat/tuning_template.json")) {
      if (stream == null) throw new IOException("Missing pinned Punchy tuning template");
      template =
          JsonParser.parseReader(
                  new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
    }
    JsonObject entry = template.getAsJsonObject("item").getAsJsonObject(kind.name());
    if (entry != null && entry.has("transforms")) {
      var sides = new Gson().fromJson(entry.get("transforms"), PunchyTuningConfig.SideSet.class);
      ToolTuning.apply(kind, left, (left ? sides.left : sides.right).toTool());
    }
    return ToolTuning.get(kind, left);
  }

  private static Map<String, ToolKind> aquaticKinds()
      throws IOException, ReflectiveOperationException {
    var resource =
        ConstructionPlacementTest.class.getResource("/dev/hybridlabs/aquatic/item/HAItems.class");
    if (resource == null || !(resource.openConnection() instanceof JarURLConnection connection))
      throw new IOException("Missing pinned Hybrid Aquatic item registry artifact");
    connection.setUseCaches(false);
    Map<String, ToolKind> kinds = new LinkedHashMap<>();
    var holder = BuiltInRegistries.ITEM.wrapAsHolder(Items.PAPER);
    var originalTags = holder.tags().toList();
    Method bind = holder.getClass().getDeclaredMethod("bindTags", Collection.class);
    bind.setAccessible(true);
    try (var archive = connection.getJarFile()) {
      for (String item : AquaticToolModels.ITEMS) {
        java.util.List<TagKey<Item>> tags = new java.util.ArrayList<>();
        for (String tag : List.of("swords", "axes", "pickaxes", "shovels", "hoes")) {
          var entry = archive.getJarEntry("data/minecraft/tags/item/" + tag + ".json");
          if (entry == null) continue;
          try (var stream = archive.getInputStream(entry)) {
            var values =
                JsonParser.parseReader(
                        new java.io.InputStreamReader(
                            stream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("values");
            if (values.asList().stream()
                .anyMatch(
                    value ->
                        value.isJsonPrimitive()
                            && value.getAsString().equals("hybrid_aquatic:" + item)))
              tags.add(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(tag)));
          }
        }
        require(tags.size() == 1, "Exactly one native tool-category tag identifies " + item);
        bind.invoke(holder, tags);
        ToolTuning.resetDefaults();
        ToolKind kind =
            ToolKindResolver.resolveItemKind(new ItemStack(Items.PAPER), ToolKind.INGREDIENT);
        require(
            kind != ToolKind.INGREDIENT,
            "Actual Punchy resolver classifies the provider tags: " + item);
        String[] parts = item.split("_");
        String className =
            "dev/hybridlabs/aquatic/item/"
                + parts[0]
                + "/"
                + Character.toUpperCase(parts[0].charAt(0))
                + parts[0].substring(1)
                + Character.toUpperCase(parts[1].charAt(0))
                + parts[1].substring(1)
                + "Item.class";
        try (var stream = archive.getInputStream(archive.getJarEntry(className))) {
          String base = new ClassReader(stream).getSuperName();
          String expected =
              switch (kind) {
                case SWORD -> "SwordItem";
                case AXE -> "AxeItem";
                case PICKAXE -> "PickaxeItem";
                case SHOVEL -> "ShovelItem";
                case HOE -> "HoeItem";
                default -> throw new AssertionError("Unexpected native category: " + kind);
              };
          require(
              base.equals("net/minecraft/world/item/" + expected),
              "Native registered tool class and category agree: " + item);
        }
        kinds.put(item, kind);
      }
    } finally {
      bind.invoke(holder, originalTags);
    }
    return kinds;
  }

  @SuppressWarnings(
      "deprecation") // Direction is checked after the real hand transform, for both hands.
  private static void directions(JsonObject net, JsonObject tape, JsonObject saw) {
    for (var context :
        List.of(
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      boolean left = context.name().endsWith("LEFT_HAND");
      PoseStack netPose = new PoseStack();
      netPose.mulPose(Axis.XP.rotationDegrees(-90));
      netPose.mulPose(Axis.YP.rotationDegrees(180));
      handModel(net).getTransforms().getTransform(context).apply(left, netPose);
      Vector3f shaft = netPose.last().pose().transformDirection(new Vector3f(0, 0, -1)).normalize();
      Vector3f pouch = netPose.last().pose().transformDirection(new Vector3f(0, -1, 0)).normalize();
      require(shaft.z < -.9f, "Net keeps its head dominantly above the hand: " + context);
      require(
          pouch.y < -.9f, "Native net pouch points toward the holder and opening away: " + context);
      JsonObject before = net.deepCopy();
      for (var entry : before.getAsJsonObject("display").entrySet())
        entry
            .getValue()
            .getAsJsonObject()
            .getAsJsonArray("rotation")
            .set(2, new com.google.gson.JsonPrimitive(0));
      PoseStack baseline = new PoseStack();
      baseline.mulPose(Axis.XP.rotationDegrees(-90));
      baseline.mulPose(Axis.YP.rotationDegrees(180));
      handModel(before).getTransforms().getTransform(context).apply(left, baseline);
      require(
          baseline
                  .last()
                  .pose()
                  .transformDirection(new Vector3f(0, 0, -1))
                  .normalize()
                  .distance(shaft)
              < .00001f,
          "Pouch correction preserves the exact shaft/head direction");
      require(
          baseline.last().pose().transformDirection(new Vector3f(0, -1, 0)).normalize().dot(pouch)
              < -.999f,
          "Pouch correction reverses only the face around the shaft");
      for (var entry : Map.of("Tape", tape, "Saw", saw).entrySet()) {
        PoseStack working = new PoseStack();
        working.mulPose(Axis.XP.rotationDegrees(-90));
        working.mulPose(Axis.YP.rotationDegrees(180));
        handModel(entry.getValue()).getTransforms().getTransform(context).apply(left, working);
        require(
            working.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize().y > .999f,
            entry.getKey() + " blade reaches forward along the fingers: " + context);
      }
    }
  }

  private static JsonArray matrix(Matrix4f matrix) {
    JsonArray rows = new JsonArray();
    for (int row = 0; row < 4; row++)
      rows.add(
          vector(matrix.get(0, row), matrix.get(1, row), matrix.get(2, row), matrix.get(3, row)));
    return rows;
  }

  private static List<VanillaEquipmentModelTest.Face> physicalCuboids(
      JsonObject source, Path assets, String item) throws IOException {
    var actual = ConstructionIconRenderingTest.actualCuboidFaces(source, assets);
    var authored = VanillaEquipmentModelTest.bakeCuboids(source);
    for (int axis = 0; axis < 3; axis++) {
      int coordinate = axis;
      var actualRange =
          actual.stream()
              .flatMap(face -> face.vertices().stream())
              .mapToDouble(point -> point.get(coordinate))
              .summaryStatistics();
      var authoredRange =
          authored.stream()
              .flatMap(face -> face.vertices().stream())
              .mapToDouble(point -> point.get(coordinate))
              .summaryStatistics();
      require(
          Math.abs(actualRange.getMin() - authoredRange.getMin()) < .00001
              && Math.abs(actualRange.getMax() - authoredRange.getMax()) < .00001,
          "The normal NeoForge loader retains authored volume instead of regenerating the native flat sprite: "
              + item);
    }
    return actual;
  }

  private static JsonArray vector(float... values) {
    JsonArray vector = new JsonArray();
    for (float value : values) vector.add(value);
    return vector;
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  @SuppressWarnings("deprecation") // The native ItemTransform parser needs only display slots.
  private static BlockModel handModel(JsonObject source) {
    JsonObject model = new JsonObject();
    model.add("display", source.get("display"));
    return BlockModel.fromString(model.toString());
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
