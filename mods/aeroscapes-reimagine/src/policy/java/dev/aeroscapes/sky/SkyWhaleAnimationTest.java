package dev.aeroscapes.sky;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Interpolationd;
import org.joml.Intersectiond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Runs the pinned native controller and GeckoLib processor against the distributed whale assets.
 */
public final class SkyWhaleAnimationTest {
  private static final String NATIVE = "dev/dacite/skywhales/entity/SkyWhale";
  private static final String GECKO_QUERIES = "software/bernie/geckolib/loading/math/MolangQueries";
  private static final String GENERATED = "dev/aeroscapes/sky/NativeWhaleControllers";
  private static final String QUERY_FIXTURE = "dev/aeroscapes/sky/NativeWhaleQueries";
  private static final String ACTOR = Type.getInternalName(QueryActor.class);
  private static final String ENTITY = Type.getInternalName(QueryEntity.class);
  private static final String RAW_ANIMATION = "software/bernie/geckolib/animation/RawAnimation";
  private static final String IDLE = "animation.sky_whale.idle";
  private static final String ATTACK = "animation.sky_whale.attack";

  private SkyWhaleAnimationTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    Path repository = Path.of(arguments[0]);
    var original = SkyWhaleArtwork.nativeArt();
    var controllers = nativeControllers();
    var queries = nativeQueries();
    JsonObject nativeResource = resource("/assets/skywhales/animations/sky_whale.animation.json");
    var baseline =
        new Playback(WhaleVariety.STONE, original.geometry(), nativeResource, controllers, queries);
    double baselineTail = 0;
    for (int tick = 0; tick <= 100; tick++) {
      baseline.tick(tick, 0.12, 0);
      baselineTail = Math.max(baselineTail, Math.abs(baseline.degrees("tail", 0)));
    }
    require(baselineTail > 3.9 && baselineTail <= 4.01, "Reproduce the native four-degree tail");

    JsonArray evidence = new JsonArray();
    JsonArray measurements = new JsonArray();
    List<String> occludedEyes = new ArrayList<>();
    JsonObject layouts = resource("/assets/aeroscapes_reimagine/whales/material_layout.json");
    List<WhaleVariety> selected =
        arguments.length > 2
            ? java.util.Arrays.stream(arguments[2].split(","))
                .map(id -> WhaleVariety.valueOf(id.toUpperCase(java.util.Locale.ROOT)))
                .toList()
            : List.of(WhaleVariety.values());
    for (WhaleVariety variety : selected) {
      JsonObject geometry =
          JsonParser.parseString(
                  Files.readString(
                      repository.resolve(
                          "mods/aeroscapes-reimagine/src/main/resources/assets/"
                              + "aeroscapes_reimagine/geo/whales/"
                              + variety.id()
                              + ".geo.json")))
              .getAsJsonObject();
      JsonObject animation =
          resource("/assets/" + variety.animation().toString().replace(':', '/'));
      require(
          animation
              .getAsJsonObject("animations")
              .get(ATTACK)
              .equals(nativeResource.getAsJsonObject("animations").get(ATTACK)),
          variety + " preserves the exact native sonic animation");
      int duration =
          (int)
              Math.round(
                  animation
                          .getAsJsonObject("animations")
                          .getAsJsonObject(IDLE)
                          .get("animation_length")
                          .getAsDouble()
                      * 20);
      var resting = new Playback(variety, geometry, animation, controllers, queries);
      var cruising = new Playback(variety, geometry, animation, controllers, queries);
      var turning = new Playback(variety, geometry, animation, controllers, queries);
      var opposite = new Playback(variety, geometry, animation, controllers, queries);
      var attacking = new Playback(variety, geometry, animation, controllers, queries);
      occludedEyes.addAll(
          checkEyes(variety.id(), geometry, layouts.getAsJsonObject(variety.id()), cruising));
      double restTail = 0;
      double cruiseTail = 0;
      double eyelid = 0;
      double jaw = 0;
      int quietTicks = 0;
      int longestGlide = 0;
      long checkedVertices = 0;
      var clearance = clearance(geometry, layouts.getAsJsonObject(variety.id()));
      JsonArray restFrames = new JsonArray();
      JsonArray cruiseFrames = new JsonArray();
      JsonArray turnFrames = new JsonArray();
      JsonArray attackFrames = new JsonArray();
      for (int tick = 0; tick <= duration + 10; tick++) {
        resting.tick(tick, 0, 0);
        cruising.tick(tick, 0.12, 0);
        turning.tick(tick, 0.12, tick >= 20 && tick < 100 ? 3 : 0);
        opposite.tick(tick, 0.12, tick >= 20 && tick < 100 ? -3 : 0);
        if (tick == 40) attacking.manager.tryTriggerAnimation("attack", "sonic_boom");
        attacking.tick(tick, 0.12, 0);
        double rotationStep = cruising.rotationStep();
        restTail = Math.max(restTail, Math.abs(resting.degrees("tail", 0)));
        cruiseTail = Math.max(cruiseTail, Math.abs(cruising.degrees("tail", 0)));
        eyelid = Math.max(eyelid, cruising.bone("eyelid_left").getScaleY());
        jaw = Math.max(jaw, Math.abs(attacking.degrees("lower_jaw", 0)));
        if (tick > 10 && Math.abs(cruising.degrees("tail", 0)) < 0.15) {
          quietTicks++;
          longestGlide = Math.max(longestGlide, quietTicks);
        } else quietTicks = 0;
        if (tick == 60) {
          require(
              turning.degrees("body", 2) > 2 && opposite.degrees("body", 2) < -2,
              variety + " two interleaved actors bank independently in opposite directions");
          require(
              Math.abs(cruising.degrees("body", 2)) < 1,
              variety + " the straight actor does not inherit another whale's turn query");
        }
        if (tick > 10) {
          require(
              rotationStep < Math.toRadians(2.7),
              variety + " every animated bone and loop seam remain smooth at 20 ticks/second");
          require(Math.abs(cruising.degrees("tail", 1)) < 0.001, "Propulsion is vertical");
          require(Math.abs(turning.degrees("body", 2)) <= 8.001, "Bank remains bounded");
          require(Math.abs(cruising.degrees("body", 0)) <= 3.001, "Pitch remains bounded");
          require(cruising.bone("body").getPosY() == 0, "The habitat does not bounce");
          require(cruising.bone("body").getScaleY() == 1, "The habitat remains solid");
        }
        if (arguments.length > 1 && tick % 2 == 0) {
          restFrames.add(resting.frame(tick));
          cruiseFrames.add(cruising.frame(tick));
          if (tick <= 160) turnFrames.add(turning.frame(tick));
          if (tick <= 160) attackFrames.add(attacking.frame(tick));
        }
        if (tick % 2 == 0) {
          for (Playback playback : List.of(resting, cruising, turning, opposite, attacking))
            checkedVertices += checkPose(playback, clearance, variety.id() + " at tick " + tick);
        }
      }
      require(restTail > 9, variety + " retains visible strokes while NoAI/stationary");
      require(cruiseTail > baselineTail * 3.5, variety + " has substantial cruising strokes");
      require(cruiseTail > restTail * 1.4, variety + " responds to real horizontal speed");
      require(eyelid > 0.99, variety + " actually blinks through native scale playback");
      require(jaw > 30, variety + " actual sonic trigger overrides idle and opens the jaw");
      require(
          longestGlide >= 20 && longestGlide <= 84,
          variety + " alternates strokes with a bounded visible glide");
      require(
          attacking.manager.getAnimationControllers().get("attack").getTriggeredAnimation() == null,
          variety + " finishes the native sonic animation");
      JsonObject measured = new JsonObject();
      measured.addProperty("variety", variety.id());
      measured.addProperty("stationary_tail_degrees", restTail);
      measured.addProperty("cruise_tail_degrees", cruiseTail);
      measured.addProperty("longest_glide_seconds", longestGlide / 20.0);
      measured.addProperty("sonic_jaw_degrees", jaw);
      measured.addProperty("blink_scale_y", eyelid);
      measured.addProperty("native_transformed_vertices_checked", checkedVertices);
      measurements.add(measured);
      if (arguments.length > 1) {
        evidence.add(scenario(variety, "stationary", restFrames));
        evidence.add(scenario(variety, "cruise", cruiseFrames));
        evidence.add(scenario(variety, "turn", turnFrames));
        evidence.add(scenario(variety, "sonic", attackFrames));
      }
    }
    require(occludedEyes.isEmpty(), "Buried authored eye ink:\n" + String.join("\n", occludedEyes));
    wrappedYaw(controllers, queries, repository);
    if (arguments.length > 1) {
      JsonObject output = new JsonObject();
      output.addProperty("provider", "Sky Whales 0.1.9 / GeckoLib 4.9.3 native CPU playback");
      output.addProperty("coordinates", "GeoBone local coordinates; rotations in radians");
      output.addProperty("sample_interval_seconds", 0.1);
      output.addProperty("sonic_trigger_tick", 40);
      output.addProperty("native_tail_degrees", baselineTail);
      output.add("measurements", measurements);
      output.add("scenarios", evidence);
      Path target = Path.of(arguments[1]);
      Files.createDirectories(target.toAbsolutePath().getParent());
      Files.writeString(target, new GsonBuilder().create().toJson(output) + "\n");
    }
    System.out.println(
        "Whale animations: native baseline, rigs="
            + selected.size()
            + ", stationary/cruise/glide, independent turns,"
            + " blink and native sonic overlay passed");
  }

  private static void wrappedYaw(Method controllers, QueryBindings queries, Path repository)
      throws IOException {
    JsonObject geometry =
        JsonParser.parseString(
                Files.readString(
                    repository.resolve(
                        "mods/aeroscapes-reimagine/src/main/resources/assets/"
                            + "aeroscapes_reimagine/geo/whales/stone.geo.json")))
            .getAsJsonObject();
    JsonObject animation =
        resource("/assets/aeroscapes_reimagine/animations/whales/mineral.animation.json");
    var wrapped = new Playback(WhaleVariety.STONE, geometry, animation, controllers, queries);
    var ordinary = new Playback(WhaleVariety.STONE, geometry, animation, controllers, queries);
    var shared = new Playback(ordinary, controllers, queries);
    for (int tick = 0; tick <= 20; tick++) {
      wrapped.tick(tick, 0.12, 359);
      ordinary.tick(tick, 0.12, -1);
    }
    require(
        Math.abs(wrapped.degrees("body", 2) - ordinary.degrees("body", 2)) < 0.001,
        "Yaw wrap produces the same bank as the equivalent one-degree turn");
    for (int tick = 21; tick <= 60; tick++) {
      ordinary.tick(tick, 0.12, 3);
      double first = ordinary.degrees("body", 2);
      shared.tick(tick, 0, -3);
      double second = shared.degrees("body", 2);
      ordinary.tick(tick, 0.12, 3);
      require(
          tick < 31
              || first > 2 && second < -2 && Math.abs(first - ordinary.degrees("body", 2)) < 0.001,
          "A shared renderer/baked rig restores each actor's native query and controller state: "
              + tick
              + " / "
              + first
              + " / "
              + second
              + " / "
              + ordinary.degrees("body", 2));
    }
    Animation cached =
        ordinary
            .manager
            .getAnimationControllers()
            .get("movement")
            .getCurrentAnimation()
            .animation();
    JsonObject snowGeometry = resource("/assets/aeroscapes_reimagine/geo/whales/snow.geo.json");
    JsonObject snowAnimation =
        resource("/assets/aeroscapes_reimagine/animations/whales/cloud.animation.json");
    ordinary.model.variety = WhaleVariety.SNOW;
    ordinary.model.animations =
        KeyFramesAdapter.GEO_GSON.fromJson(
            snowAnimation.getAsJsonObject("animations"), BakedAnimations.class);
    ordinary
        .model
        .getAnimationProcessor()
        .setActiveModel(
            BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(
                GeometryTree.fromModel(
                    KeyFramesAdapter.GEO_GSON.fromJson(snowGeometry, Model.class))));
    ordinary.tick(61, 0.12, 0);
    require(
        ordinary.manager.getAnimationControllers().get("movement").getCurrentAnimation().animation()
            == cached,
        "Changing a live model/resource does not reload the native identical RawAnimation");
    var replacement =
        new Playback(WhaleVariety.SNOW, snowGeometry, snowAnimation, controllers, queries);
    for (int tick = 0; tick <= 20; tick++) replacement.tick(tick, 0.12, 0);
    require(
        replacement
                .manager
                .getAnimationControllers()
                .get("movement")
                .getCurrentAnimation()
                .animation()
            == replacement.model.animations.getAnimation(IDLE),
        "A replacement preview entity starts its selected animation family with a fresh native cache");
  }

  private static JsonObject scenario(WhaleVariety variety, String name, JsonArray frames) {
    JsonObject result = new JsonObject();
    result.addProperty("variety", variety.id());
    result.addProperty("scenario", name);
    result.addProperty("resource", variety.animation().toString());
    result.add("frames", frames);
    return result;
  }

  private static List<String> checkEyes(
      String variety, JsonObject geometry, JsonObject layout, Playback playback) {
    int neutral = 0xffa1b2c3;
    Map<String, WhaleTextureComposer.Pixels> fixtures = new LinkedHashMap<>();
    for (String texture : WhaleTextureComposer.requiredTextureIds(layout)) {
      int[] pixels = new int[256];
      java.util.Arrays.fill(pixels, neutral);
      fixtures.put(texture, new WhaleTextureComposer.Pixels(16, 16, pixels));
    }
    var painted = WhaleTextureComposer.compose(variety, geometry, layout, fixtures);
    Set<String> sprites = new java.util.HashSet<>();
    for (var value : layout.getAsJsonArray("decorations")) {
      JsonObject surface = value.getAsJsonObject();
      if (surface.has("sampling") && surface.get("sampling").getAsString().equals("sprite"))
        sprites.add(surface.get("bone").getAsString() + ":" + surface.get("cube").getAsInt());
    }
    List<NativeFace> faces = new ArrayList<>();
    PoseStack poses = new PoseStack();
    for (GeoBone bone : playback.model.getAnimationProcessor().getRegisteredBones())
      if (bone.getParent() == null) eyeFaces(poses, bone, sprites, faces);
    JsonArray sourceBones =
        geometry
            .getAsJsonArray("minecraft:geometry")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("bones");
    List<String> failures = new ArrayList<>();
    require(layout.getAsJsonArray("eyes").size() == 2, variety + " has both authored eyes");
    for (var value : layout.getAsJsonArray("eyes")) {
      JsonObject eye = value.getAsJsonObject();
      String key = eye.get("bone").getAsString() + ":" + eye.get("cube").getAsInt();
      JsonObject sourceBone =
          java.util.stream.StreamSupport.stream(sourceBones.spliterator(), false)
              .map(com.google.gson.JsonElement::getAsJsonObject)
              .filter(bone -> bone.get("name").getAsString().equals(eye.get("bone").getAsString()))
              .findFirst()
              .orElseThrow();
      JsonObject cube =
          sourceBone.getAsJsonArray("cubes").get(eye.get("cube").getAsInt()).getAsJsonObject();
      int[] uv = WhaleTextureComposer.face(cube, eye.get("face").getAsString());
      List<NativeFace> candidates =
          faces.stream().filter(face -> face.cube().equals(key) && face.covers(uv)).toList();
      require(candidates.size() == 1, variety + " eye identifies one actual baked cheek UV face");
      NativeFace cheek = candidates.getFirst();
      int ink = 0;
      int blocked = 0;
      String first = "";
      for (int row = 0; row < Math.abs(uv[3]); row++) {
        for (int column = 0; column < uv[2]; column++) {
          int x = uv[0] + column;
          int y = uv[3] < 0 ? uv[1] - 1 - row : uv[1] + row;
          if (painted.get(x, y) == neutral) continue;
          ink++;
          Vector3d point = cheek.point((x + 0.5) / painted.width(), (y + 0.5) / painted.height());
          for (NativeFace other : faces) {
            if (other.cube().equals(key) || !other.blocks(point, cheek.normal())) continue;
            blocked++;
            if (first.isEmpty()) first = " atlas(" + x + "," + y + ") behind " + other.cube();
            break;
          }
        }
      }
      require(ink > 0, variety + " paints authored eye ink on its actual cheek UV face");
      if (blocked > 0)
        failures.add(
            variety + " " + eye.get("face").getAsString() + ": " + blocked + "/" + ink + first);
    }
    return failures;
  }

  private static void eyeFaces(
      PoseStack poses, GeoBone bone, Set<String> sprites, List<NativeFace> faces) {
    if (Set.of("eyelid_left", "eyelid_right").contains(bone.getName())) return;
    poses.pushPose();
    RenderUtil.prepMatrixForBone(poses, bone);
    for (int index = 0; index < bone.getCubes().size(); index++) {
      String key = bone.getName() + ":" + index;
      if (sprites.contains(key)) continue;
      GeoCube cube = bone.getCubes().get(index);
      poses.pushPose();
      RenderUtil.translateToPivotPoint(poses, cube);
      RenderUtil.rotateMatrixAroundCube(poses, cube);
      RenderUtil.translateAwayFromPivotPoint(poses, cube);
      for (var quad : cube.quads()) {
        if (quad == null) continue;
        List<NativeVertex> vertices = new ArrayList<>();
        for (var vertex : quad.vertices()) {
          Vector4f transformed = poses.last().pose().transform(new Vector4f(vertex.position(), 1));
          vertices.add(
              new NativeVertex(
                  new Vector3d(transformed.x(), transformed.y(), transformed.z()),
                  vertex.texU(),
                  vertex.texV()));
        }
        Vector3f normal = poses.last().normal().transform(new Vector3f(quad.normal())).normalize();
        faces.add(new NativeFace(key, vertices, new Vector3d(normal)));
      }
      poses.popPose();
    }
    for (GeoBone child : bone.getChildBones()) eyeFaces(poses, child, sprites, faces);
    poses.popPose();
  }

  private record NativeVertex(Vector3d point, double u, double v) {}

  private record NativeFace(String cube, List<NativeVertex> vertices, Vector3d normal) {
    boolean covers(int[] uv) {
      double minimumU = vertices.stream().mapToDouble(NativeVertex::u).min().orElseThrow();
      double maximumU = vertices.stream().mapToDouble(NativeVertex::u).max().orElseThrow();
      double minimumV = vertices.stream().mapToDouble(NativeVertex::v).min().orElseThrow();
      double maximumV = vertices.stream().mapToDouble(NativeVertex::v).max().orElseThrow();
      return Math.abs(minimumU - uv[0] / 1024.0) < 0.000001
          && Math.abs(maximumU - (uv[0] + uv[2]) / 1024.0) < 0.000001
          && Math.abs(minimumV - Math.min(uv[1], uv[1] + uv[3]) / 1024.0) < 0.000001
          && Math.abs(maximumV - Math.max(uv[1], uv[1] + uv[3]) / 1024.0) < 0.000001;
    }

    Vector3d point(double u, double v) {
      for (int third : new int[] {2, 3}) {
        NativeVertex first = vertices.get(0);
        NativeVertex second = vertices.get(third - 1);
        NativeVertex last = vertices.get(third);
        Vector3d weights =
            Interpolationd.interpolationFactorsTriangle(
                first.u(),
                first.v(),
                second.u(),
                second.v(),
                last.u(),
                last.v(),
                u,
                v,
                new Vector3d());
        if (weights.x < -0.000001 || weights.y < -0.000001 || weights.z < -0.000001) continue;
        return new Vector3d(first.point())
            .mul(weights.x)
            .fma(weights.y, second.point())
            .fma(weights.z, last.point());
      }
      throw new IllegalStateException("Eye ink texel is outside its actual baked cheek quad");
    }

    boolean blocks(Vector3d point, Vector3d direction) {
      for (int third : new int[] {2, 3})
        if (Intersectiond.intersectRayTriangle(
                point,
                direction,
                vertices.getFirst().point(),
                vertices.get(third - 1).point(),
                vertices.get(third).point(),
                0.000000001)
            > 0.00001) return true;
      return false;
    }
  }

  private static Clearance clearance(JsonObject geometry, JsonObject layout) {
    JsonArray bones =
        geometry
            .getAsJsonArray("minecraft:geometry")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("bones");
    JsonArray regions = layout.getAsJsonArray("spawn_clearance");
    Map<String, Bounds> byBone = new LinkedHashMap<>();
    int region = 0;
    for (var value : bones) {
      JsonObject bone = value.getAsJsonObject();
      if (!bone.has("cubes") || bone.getAsJsonArray("cubes").isEmpty()) continue;
      byBone.put(bone.get("name").getAsString(), bounds(regions.get(region++).getAsJsonObject()));
    }
    require(region == regions.size(), "Every rendered bone has exactly one spawn clearance region");
    return new Clearance(bounds(layout.getAsJsonObject("model_bounds")), byBone);
  }

  private static Bounds bounds(JsonObject bounds) {
    require(
        bounds.getAsJsonArray("min").size() == 3 && bounds.getAsJsonArray("max").size() == 3,
        "Whale bounds require three coordinates per corner");
    double[] minimum = new double[3];
    double[] maximum = new double[3];
    for (int axis = 0; axis < 3; axis++) {
      minimum[axis] = bounds.getAsJsonArray("min").get(axis).getAsDouble();
      maximum[axis] = bounds.getAsJsonArray("max").get(axis).getAsDouble();
      require(
          Double.isFinite(minimum[axis])
              && Double.isFinite(maximum[axis])
              && minimum[axis] <= maximum[axis],
          "Whale bounds require ordered finite coordinates");
    }
    return new Bounds(minimum, maximum);
  }

  private static long checkPose(Playback playback, Clearance clearance, String label) {
    long count = 0;
    PoseStack poses = new PoseStack();
    for (GeoBone bone : playback.model.getAnimationProcessor().getRegisteredBones())
      if (bone.getParent() == null) count += checkBone(poses, bone, clearance, label);
    return count;
  }

  private static long checkBone(PoseStack poses, GeoBone bone, Clearance clearance, String label) {
    poses.pushPose();
    RenderUtil.prepMatrixForBone(poses, bone);
    long count = 0;
    String boneLabel = label + " bone/" + bone.getName();
    String modelLabel = label + " model/" + bone.getName();
    for (GeoCube cube : bone.getCubes()) {
      poses.pushPose();
      RenderUtil.translateToPivotPoint(poses, cube);
      RenderUtil.rotateMatrixAroundCube(poses, cube);
      RenderUtil.translateAwayFromPivotPoint(poses, cube);
      for (var quad : cube.quads()) {
        if (quad == null) continue;
        for (var vertex : quad.vertices()) {
          Vector4f transformed = poses.last().pose().transform(new Vector4f(vertex.position(), 1));
          double[] source = {-transformed.x() * 16, transformed.y() * 16, transformed.z() * 16};
          clearance.model().contains(source, modelLabel);
          clearance.bones().get(bone.getName()).contains(source, boneLabel);
          count++;
        }
      }
      poses.popPose();
    }
    for (GeoBone child : bone.getChildBones()) count += checkBone(poses, child, clearance, label);
    poses.popPose();
    return count;
  }

  private record Clearance(Bounds model, Map<String, Bounds> bones) {}

  private record Bounds(double[] minimum, double[] maximum) {
    void contains(double[] point, String label) {
      for (int axis = 0; axis < 3; axis++)
        if (!Double.isFinite(point[axis])
            || point[axis] < minimum[axis] - 0.02
            || point[axis] > maximum[axis] + 0.02)
          throw new IllegalStateException(
              label
                  + " native rendered corner escapes axis "
                  + axis
                  + ": "
                  + point[axis]
                  + " outside ["
                  + minimum[axis]
                  + ", "
                  + maximum[axis]
                  + "]");
    }
  }

  private static JsonObject resource(String path) throws IOException {
    try (var input = SkyWhaleAnimationTest.class.getResourceAsStream(path)) {
      if (input == null) throw new IOException("Missing whale animation resource " + path);
      return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private static ClassNode read(String name) throws IOException {
    try (var input = SkyWhaleAnimationTest.class.getResourceAsStream("/" + name + ".class")) {
      if (input == null) throw new IOException("Missing pinned native class " + name);
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, 0);
      return node;
    }
  }

  private static Method nativeControllers()
      throws IOException, IllegalAccessException, NoSuchMethodException {
    ClassNode source = read(NATIVE);
    ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    output.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, GENERATED, null, "java/lang/Object", null);
    for (String field : List.of("IDLE", "ATTACK"))
      output
          .visitField(
              Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
              field,
              "L" + RAW_ANIMATION + ";",
              null,
              null)
          .visitEnd();
    MethodNode initializer = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    boolean copying = false;
    int animations = 0;
    for (MethodNode method : source.methods) {
      if (method.name.equals("<clinit>")) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
          if (instruction instanceof MethodInsnNode call
              && call.owner.equals(RAW_ANIMATION)
              && call.name.equals("begin")) copying = true;
          if (copying
              && instruction.getOpcode() >= 0
              && instruction.getOpcode() != Opcodes.RETURN) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(NATIVE)) {
              field.owner = GENERATED;
              animations++;
            }
            instruction.accept(initializer);
          }
        }
      } else if (method.name.equals("registerControllers")
          || method.name.startsWith("lambda$registerControllers$")) {
        if (method.name.equals("registerControllers")) {
          method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
          method.desc = "(" + Type.getDescriptor(Fixture.class) + method.desc.substring(1);
          method.signature = null;
        }
        for (AbstractInsnNode instruction : method.instructions) {
          if (instruction instanceof FieldInsnNode field && field.owner.equals(NATIVE))
            field.owner = GENERATED;
          if (instruction instanceof InvokeDynamicInsnNode dynamic)
            for (int index = 0; index < dynamic.bsmArgs.length; index++)
              if (dynamic.bsmArgs[index] instanceof Handle handle
                  && handle.getOwner().equals(NATIVE))
                dynamic.bsmArgs[index] =
                    new Handle(
                        handle.getTag(),
                        GENERATED,
                        handle.getName(),
                        handle.getDesc(),
                        handle.isInterface());
        }
        method.accept(output);
      }
    }
    require(animations == 2, "Extract both native RawAnimation initializers");
    initializer.instructions.add(new InsnNode(Opcodes.RETURN));
    initializer.accept(output);
    output.visitEnd();
    return MethodHandles.lookup()
        .defineClass(output.toByteArray())
        .getMethod(
            "registerControllers", Fixture.class, AnimatableManager.ControllerRegistrar.class);
  }

  private static QueryBindings nativeQueries()
      throws IOException, IllegalAccessException, NoSuchMethodException {
    ClassNode source = read(GECKO_QUERIES);
    Map<String, Handle> handles = new LinkedHashMap<>();
    for (MethodNode method : source.methods) {
      if (!method.name.startsWith("setDefault")) continue;
      String query = "";
      for (AbstractInsnNode instruction : method.instructions) {
        if (instruction instanceof LdcInsnNode literal && literal.cst instanceof String value)
          query = value;
        if (instruction instanceof InvokeDynamicInsnNode dynamic
            && List.of("query.ground_speed", "query.yaw_speed").contains(query))
          for (Object argument : dynamic.bsmArgs)
            if (argument instanceof Handle handle && handle.getOwner().equals(GECKO_QUERIES))
              handles.put(query, handle);
      }
    }
    require(handles.size() == 2, "Find the actual entity-backed ground/yaw query suppliers");
    ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    output.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, QUERY_FIXTURE, null, "java/lang/Object", null);
    for (var binding : handles.entrySet()) {
      MethodNode method =
          source.methods.stream()
              .filter(candidate -> candidate.name.equals(binding.getValue().getName()))
              .findFirst()
              .orElseThrow();
      method.name = binding.getKey().substring("query.".length());
      method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
      method.desc = "(L" + ACTOR + ";)D";
      method.signature = null;
      for (AbstractInsnNode instruction : method.instructions) {
        if (instruction instanceof FieldInsnNode field) field.owner = queryType(field.owner);
        if (instruction instanceof MethodInsnNode call) call.owner = queryType(call.owner);
        if (instruction instanceof TypeInsnNode type) type.desc = queryType(type.desc);
      }
      method.accept(output);
    }
    output.visitEnd();
    Class<?> generated = MethodHandles.lookup().defineClass(output.toByteArray());
    return new QueryBindings(
        generated.getMethod("ground_speed", QueryActor.class),
        generated.getMethod("yaw_speed", QueryActor.class));
  }

  private static String queryType(String type) {
    if (type.equals(GECKO_QUERIES + "$Actor")) return ACTOR;
    if (type.equals("net/minecraft/world/entity/Entity")
        || type.equals("net/minecraft/world/entity/LivingEntity")) return ENTITY;
    return type;
  }

  private static double query(Method method, QueryActor actor) {
    try {
      return (Double) method.invoke(null, actor);
    } catch (IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Cannot evaluate the native entity query", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private record QueryBindings(Method groundSpeed, Method yawSpeed) {}

  public static final class QueryActor {
    public final Object animatable;

    QueryActor(QueryEntity entity) {
      animatable = entity;
    }
  }

  public static final class QueryEntity {
    public float yRotO;
    public float yaw;
    public Vec3 velocity = Vec3.ZERO;

    public float getYRot() {
      return yaw;
    }

    public Vec3 getDeltaMovement() {
      return velocity;
    }
  }

  public static final class Fixture implements GeoAnimatable {
    private final Method registration;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    Fixture(Method registration) {
      this.registration = registration;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
      try {
        registration.invoke(null, this, registrar);
      } catch (IllegalAccessException | InvocationTargetException failure) {
        throw new IllegalStateException(
            "Cannot execute native whale controller registration", failure);
      }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
    }

    @Override
    public double getTick(Object context) {
      return 0;
    }
  }

  @SuppressWarnings(
      "deprecation") // GeckoLib 4.9.3 still requires both deprecated abstract resource methods.
  private static final class PlaybackModel extends GeoModel<Fixture> {
    private WhaleVariety variety;
    private BakedAnimations animations;

    PlaybackModel(WhaleVariety variety, BakedAnimations animations) {
      this.variety = variety;
      this.animations = animations;
    }

    @Override
    public ResourceLocation getModelResource(Fixture fixture) {
      return variety.model();
    }

    @Override
    public ResourceLocation getTextureResource(Fixture fixture) {
      return variety.texture();
    }

    @Override
    public ResourceLocation getAnimationResource(Fixture fixture) {
      return variety.animation();
    }

    @Override
    public Animation getAnimation(Fixture fixture, String name) {
      Animation result = animations.getAnimation(name);
      if (result == null)
        throw new IllegalStateException("Native controller requested missing " + name);
      return result;
    }
  }

  private static final class Playback {
    private final Fixture fixture;
    private final PlaybackModel model;
    private final AnimatableManager<Fixture> manager;
    private final QueryEntity entity = new QueryEntity();
    private final QueryActor actor = new QueryActor(entity);
    private final QueryBindings queries;
    private final Map<String, float[]> previousRotations = new LinkedHashMap<>();

    Playback(
        WhaleVariety variety,
        JsonObject geometry,
        JsonObject animation,
        Method controllers,
        QueryBindings queries) {
      this.queries = queries;
      fixture = new Fixture(controllers);
      manager = new AnimatableManager<>(fixture);
      BakedAnimations bakedAnimations =
          KeyFramesAdapter.GEO_GSON.fromJson(
              animation.getAsJsonObject("animations"), BakedAnimations.class);
      model = new PlaybackModel(variety, bakedAnimations);
      Model raw = KeyFramesAdapter.GEO_GSON.fromJson(geometry, Model.class);
      BakedGeoModel bakedModel =
          BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
      model.getAnimationProcessor().setActiveModel(bakedModel);
    }

    Playback(Playback shared, Method controllers, QueryBindings queries) {
      this.queries = queries;
      fixture = new Fixture(controllers);
      manager = new AnimatableManager<>(fixture);
      model = shared.model;
    }

    void tick(int tick, double speed, float yawSpeed) {
      entity.velocity = new Vec3(speed, 0, 0);
      entity.yaw = yawSpeed;
      MathParser.setVariable("query.ground_speed", () -> query(queries.groundSpeed(), actor));
      MathParser.setVariable("query.yaw_speed", () -> query(queries.yawSpeed(), actor));
      MathParser.setVariable("query.vertical_speed", () -> entity.velocity.y);
      var state = new AnimationState<>(fixture, 0, 0, 0, speed > 0);
      state.animationTick = tick;
      model.getAnimationProcessor().tickAnimation(fixture, model, manager, tick, state, true);
    }

    GeoBone bone(String name) {
      GeoBone result = model.getAnimationProcessor().getBone(name);
      if (result == null) throw new IllegalStateException("Missing animated bone " + name);
      return result;
    }

    double degrees(String name, int axis) {
      GeoBone value = bone(name);
      return Math.toDegrees(
          switch (axis) {
            case 0 -> value.getRotX();
            case 1 -> value.getRotY();
            case 2 -> value.getRotZ();
            default -> throw new IllegalArgumentException("Unknown rotation axis");
          });
    }

    double rotationStep() {
      double maximum = 0;
      for (GeoBone bone : model.getAnimationProcessor().getRegisteredBones()) {
        float[] current = {bone.getRotX(), bone.getRotY(), bone.getRotZ()};
        float[] previous = previousRotations.put(bone.getName(), current);
        if (previous == null) continue;
        for (int axis = 0; axis < current.length; axis++)
          maximum = Math.max(maximum, Math.abs(current[axis] - previous[axis]));
      }
      return maximum;
    }

    JsonObject frame(int tick) {
      JsonObject result = new JsonObject();
      result.addProperty("tick", tick);
      result.addProperty("seconds", tick / 20.0);
      JsonObject bones = new JsonObject();
      List<GeoBone> ordered = new ArrayList<>(model.getAnimationProcessor().getRegisteredBones());
      ordered.sort(java.util.Comparator.comparing(GeoBone::getName));
      for (GeoBone bone : ordered) {
        JsonObject pose = new JsonObject();
        pose.add("rotation", vector(bone.getRotX(), bone.getRotY(), bone.getRotZ()));
        pose.add("position", vector(bone.getPosX(), bone.getPosY(), bone.getPosZ()));
        pose.add("scale", vector(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ()));
        pose.add("pivot", vector(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
        bones.add(bone.getName(), pose);
      }
      result.add("bones", bones);
      return result;
    }

    private static JsonArray vector(float x, float y, float z) {
      JsonArray values = new JsonArray();
      values.add(x);
      values.add(y);
      values.add(z);
      return values;
    }
  }
}
