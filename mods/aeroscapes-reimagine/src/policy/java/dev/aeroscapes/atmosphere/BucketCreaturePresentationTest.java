package dev.aeroscapes.atmosphere;

import com.crispytwig.naturalist.client.model.animal.duck.DuckBabyModel;
import com.li64.tide.registries.entities.models.BlazingSwordfishModel;
import com.li64.tide.registries.entities.models.FishModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.LoadingModList;
import org.joml.Vector3f;

/** Protects surface-relative creature placement and provider-specific illumination. */
public final class BucketCreaturePresentationTest {
  private BucketCreaturePresentationTest() {}

  public static void main(String[] arguments) throws ReflectiveOperationException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    floatingCreatures();
    nativeDuckling();
    nativeSlime();
    protrudingSwordfish();
    light();
    System.out.println("Creature surface placement and native illumination passed");
  }

  private static void nativeDuckling() {
    var pose = new PoseStack();
    pose.mulPose(Axis.ZP.rotationDegrees(-20));
    pose.mulPose(Axis.YP.rotationDegrees(90));
    pose.scale(-1, -1, 1);
    pose.translate(0, -1.501F, 0);
    var bounds = new BucketCreatures.Bounds();
    DuckBabyModel.createBodyLayer()
        .bakeRoot()
        .render(pose, bounds, LightTexture.FULL_BRIGHT, 0, -1);
    var placement = BucketCreatures.floatingPlacement(bounds, 5.4F / 16, -.05F);
    require(
        placement.scale() > 1, "The small native duckling remains readable at the actual opening");
    for (var vertex : bounds.vertices) {
      float x = vertex.x * placement.scale() + placement.x() - .5F;
      float y = (vertex.y * placement.scale() + placement.y()) * 16;
      float z = vertex.z * placement.scale() + placement.z() - .5F;
      float radius = (3.7F + (Math.min(9.8F, y) - 1.8F) / 8 * 1.242F) / 16;
      require(y >= 1.8F - 1E-5F, "Every native vertex clears the physical floor");
      for (int side = 0; side < 8; side++) {
        double angle = Math.PI / 8 + side * Math.PI / 4;
        require(
            x * Math.cos(angle) + z * Math.sin(angle) < radius * Math.cos(Math.PI / 8),
            "Every native vertex clears the tapered wall, including after the opposite-hand"
                + " mirror");
      }
    }
    var duck = ResourceLocation.parse("naturalist:duck");
    require(
        BucketCreatures.facing(duck, false) == 0 && BucketCreatures.facing(duck, true) == 180,
        "The duck's defining head and bill face the exposed side in each hand");
  }

  private static void nativeSlime() {
    var slime = ResourceLocation.parse("minecraft:slime");
    var pose = new PoseStack();
    pose.mulPose(Axis.ZP.rotationDegrees(BucketCreatures.lean(slime)));
    pose.mulPose(Axis.YP.rotationDegrees(90));
    pose.scale(-1, -1, 1);
    pose.translate(0, -1.501F, 0);
    var bounds = new BucketCreatures.Bounds();
    SlimeModel.createOuterBodyLayer()
        .bakeRoot()
        .render(pose, bounds, LightTexture.FULL_BRIGHT, 0, -1);
    SlimeModel.createInnerBodyLayer()
        .bakeRoot()
        .render(pose, bounds, LightTexture.FULL_BRIGHT, 0, -1);
    var placement = BucketCreatures.floatingPlacement(bounds, 5.4F / 16, 0);
    require(
        bounds.minimum.y * placement.scale() + placement.y() < 5.4F / 16
            && bounds.maximum.y * placement.scale() + placement.y() > 10.4F / 16,
        "The native slime emerges from half gel above the physical rim");
    var face = new Vector3f(0, 0, -1);
    pose.last().pose().transformDirection(face);
    require(face.y > .5F, "The native face points up toward the opening instead of into the wall");
    require(
        BucketCreatures.facing(slime, false) == 0 && BucketCreatures.facing(slime, true) == 180,
        "The slime face stays on the exposed side of the fist in each hand");
  }

  private static void protrudingSwordfish() throws ReflectiveOperationException {
    var type = ResourceLocation.parse("tide:blazing_swordfish");
    var pose = new PoseStack();
    pose.mulPose(Axis.ZP.rotationDegrees(BucketCreatures.lean(type)));
    pose.mulPose(Axis.XP.rotationDegrees(BucketCreatures.roll(type)));
    var bounds = swordfish(BucketCreatures.SWORDFISH_REFERENCE_TICK, pose);
    var placement = BucketCreatures.perchedPlacement(bounds, 9F / 16, -.22F);
    require(placement.scale() > .15F, "The native fish retains a readable miniature body");
    for (float age : new float[] {0, 7, 14, 21, 28})
      for (boolean left : new boolean[] {false, true}) {
        var held = new PoseStack();
        held.translate(.5F, placement.y(), .5F + BucketCreatures.depth(type));
        held.mulPose(Axis.YP.rotationDegrees(BucketCreatures.facing(type, left)));
        held.translate(placement.x() - .5F, 0, placement.z() - .5F);
        held.scale(placement.scale(), placement.scale(), placement.scale());
        held.mulPose(Axis.ZP.rotationDegrees(BucketCreatures.lean(type)));
        held.mulPose(Axis.XP.rotationDegrees(BucketCreatures.roll(type)));
        var actual = swordfish(age, held);
        require(
            actual.minimum.y < 9F / 16 && actual.maximum.y > 12F / 16,
            "The native fish exposes its head and bill while its tail remains in lava");
        for (int start = 0; start < actual.vertices.size(); start += 4) {
          var quad = actual.vertices.subList(start, start + 4);
          for (int edge = 0; edge < 4; edge++) {
            var first = quad.get(edge);
            var second = quad.get((edge + 1) % 4);
            swordfishWall(first);
            float rim = 9.8F / 16;
            if ((first.y > rim) != (second.y > rim))
              swordfishWall(
                  new Vector3f(first).lerp(second, (rim - first.y) / (second.y - first.y)));
          }
          // EngineeringBucketModels.vessel's tube rises above this empty central span.
          require(
              quad.stream().allMatch(vertex -> vertex.z * 16 > 8.34F)
                  || quad.stream()
                      .allMatch(
                          vertex ->
                              vertex.x * 16 > 3.5F
                                  && vertex.x * 16 < 12.5F
                                  && vertex.y * 16 < 11.1F),
              "Every native face stays in front of the bail or below its curved middle span");
          for (boolean slim : new boolean[] {false, true}) {
            var arm = quad.stream().map(vertex -> swordfishArm(vertex, left, slim)).toList();
            float minimumX = left ? -1 : (slim ? -2 : -3);
            float maximumX = left ? (slim ? 2 : 3) : 1;
            var minimum = new Vector3f(minimumX - .35F, -2.35F, -2.35F);
            var maximum = new Vector3f(maximumX + .35F, 10.35F, 2.35F);
            for (int[] indices : new int[][] {{0, 1, 2}, {0, 2, 3}})
              require(
                  !triangleBox(
                      arm.get(indices[0]),
                      arm.get(indices[1]),
                      arm.get(indices[2]),
                      minimum,
                      maximum),
                  "The whole native fish clears the actual classic/slim fist and sleeve");
          }
        }
      }
  }

  private static BucketCreatures.Bounds swordfish(float age, PoseStack pose)
      throws ReflectiveOperationException {
    var root = BlazingSwordfishModel.createBodyLayer().bakeRoot();
    var models =
        new EntityModelSet() {
          @Override
          public ModelPart bakeLayer(ModelLayerLocation location) {
            require(
                location.equals(BlazingSwordfishModel.MODEL_LOCATION),
                "The native constructor requests its own swordfish layer");
            return root;
          }
        };
    var model =
        new BlazingSwordfishModel(
            new EntityRendererProvider.Context(null, null, null, null, null, models, null));
    var configurations = FishModel.class.getDeclaredField("swimConfigs");
    configurations.setAccessible(true);
    if (!(configurations.get(model) instanceof List<?> animations))
      throw new AssertionError("The pinned native swim configurations must be a list");
    var animate =
        FishModel.class.getDeclaredMethod(
            "lambda$setupAnim$0",
            float.class,
            float.class,
            Class.forName("com.li64.tide.registries.entities.models.FishModel$SwimAnimConfig"));
    animate.setAccessible(true);
    // Execute Tide's zero-age sentinel and configured offsets instead of copying their formulas.
    for (var animation : animations) animate.invoke(null, age, 1F, animation);
    pose.mulPose(
        Axis.YP.rotationDegrees(
            90 + model.swimAnimScale() * 4.3F * Mth.sin(model.swimAnimSpeed() * age)));
    pose.scale(-1, -1, 1);
    pose.translate(0, -1.501F, 0);
    var bounds = new BucketCreatures.Bounds();
    model.renderToBuffer(pose, bounds, LightTexture.FULL_BRIGHT, 0, -1);
    return bounds;
  }

  private static void swordfishWall(Vector3f point) {
    if (point.y > 9.8F / 16 + 1E-6F) return;
    float x = Math.abs(point.x * 16 - 8);
    float z = Math.abs(point.z * 16 - 8);
    float radius = 3.7F + (point.y * 16 - 1.8F) / 8 * 1.242F;
    require(
        Math.max(x, z) + Math.min(x, z) * .41421356F < radius,
        "Every submerged face and rim-crossing edge clears the tapered cavity");
  }

  private static Vector3f swordfishArm(Vector3f point, boolean left, boolean slim) {
    // Native CREATURE_BUCKET attachment relative to the recorded explicit arm bone.
    float[][] matrix =
        left
            ? new float[][] {
              {-.73735486F, .06451020F, -.21224072F, 7.03749228F},
              {.18034672F, -.25463026F, -.70394489F, 16.93726533F},
              {-.12916175F, -.72381059F, .22872557F, 9.19779019F}
            }
            : new float[][] {
              {-.73735490F, .06451019F, .21224079F, 2.92164108F},
              {-.22182293F, -.21944498F, -.70394487F, 19.65323490F},
              {.00151112F, -.73524306F, .22872559F, 8.31531954F}
            };
    var pixel = new Vector3f(point).mul(16);
    var transformed = new Vector3f();
    for (int row = 0; row < 3; row++)
      transformed.setComponent(
          row,
          matrix[row][0] * pixel.x
              + matrix[row][1] * pixel.y
              + matrix[row][2] * pixel.z
              + matrix[row][3]);
    if (slim)
      transformed.add(
          left
              ? new Vector3f(-.49264119F, .02034316F, .08301097F)
              : new Vector3f(.49276308F, .05398247F, .06534897F));
    return transformed;
  }

  private static boolean triangleBox(
      Vector3f first, Vector3f second, Vector3f third, Vector3f minimum, Vector3f maximum) {
    var edges =
        List.of(
            new Vector3f(second).sub(first),
            new Vector3f(third).sub(second),
            new Vector3f(first).sub(third));
    var axes =
        new ArrayList<>(
            List.of(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)));
    axes.add(new Vector3f(edges.get(0)).cross(edges.get(1)));
    for (var edge : edges)
      for (var axis : List.of(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)))
        axes.add(new Vector3f(edge).cross(axis));
    var center = new Vector3f(minimum).add(maximum).mul(.5F);
    var half = new Vector3f(maximum).sub(minimum).mul(.5F);
    for (var axis : axes) {
      if (axis.lengthSquared() < 1E-12F) continue;
      float offset = axis.dot(center);
      float a = axis.dot(first) - offset;
      float b = axis.dot(second) - offset;
      float c = axis.dot(third) - offset;
      float radius =
          half.x * Math.abs(axis.x) + half.y * Math.abs(axis.y) + half.z * Math.abs(axis.z);
      if (Math.min(a, Math.min(b, c)) > radius || Math.max(a, Math.max(b, c)) < -radius)
        return false;
    }
    return true;
  }

  private static void floatingCreatures() {
    // Pinned native model vertices, including provider inventory poses and the held lean.
    var creatures =
        List.of(
            new Mesh(
                "duckling",
                new Vector3f(-.1651083F, -.0164420F, -.1875F),
                new Vector3f(.1926744F, .4107597F, .1875F)),
            new Mesh(
                "duck",
                new Vector3f(-.2343654F, -.0204365F, -.25F),
                new Vector3f(.4809843F, .9300520F, .25F)),
            new Mesh(
                "baby seal",
                new Vector3f(-.3893063F, -.5963702F, -.3326640F),
                new Vector3f(.4792712F, .7801637F, .5277107F)),
            new Mesh(
                "coastal crab",
                new Vector3f(-.6219748F, -.3650925F, -.6135017F),
                new Vector3f(.3743261F, .7473830F, .3837164F)),
            new Mesh(
                "baby coastal crab",
                new Vector3f(-.3498151F, -.1573908F, -.3395399F),
                new Vector3f(.2979260F, .5509134F, .1871867F)),
            new Mesh(
                "octopus",
                new Vector3f(-.2811168F, -.7072990F, -.6733873F),
                new Vector3f(.5624999F, 1.0322500F, .6878357F)));
    float surface = 5.4F / 16;
    for (var mesh : creatures) {
      var placement = BucketCreatures.placement(mesh.minimum(), mesh.maximum(), surface, true);
      float bottom = mesh.minimum().y * placement.scale() + placement.y();
      float top = mesh.maximum().y * placement.scale() + placement.y();
      require(
          bottom >= 1.8F / 16 && bottom < surface && top > 10.4F / 16,
          mesh.name() + " intersects half water while recognizable parts clear the rim");
      require(
          Math.hypot(mesh.maximum().x - mesh.minimum().x, mesh.maximum().z - mesh.minimum().z)
                  * placement.scale()
              <= .55F + 1E-6F,
          mesh.name() + " retains clearance around the complete native footprint");
      require(
          Math.abs(
                      (mesh.minimum().x + mesh.maximum().x) * placement.scale() / 2
                          + placement.x()
                          - .5F)
                  < 1E-6F
              && Math.abs(
                      (mesh.minimum().z + mesh.maximum().z) * placement.scale() / 2
                          + placement.z()
                          - .5F)
                  < 1E-6F,
          mesh.name() + " remains centered despite provider model offsets");
    }
  }

  private static void light() {
    require(
        BucketCreatures.light(LightTexture.pack(0, 0), LightTexture.pack(15, 0))
            == LightTexture.pack(15, 0),
        "An intrinsically luminous native renderer stays bright in a dark room");
    require(
        BucketCreatures.light(LightTexture.pack(3, 12), LightTexture.pack(9, 0))
            == LightTexture.pack(9, 12),
        "Partial provider brightness preserves the held item's sky lighting");
    require(
        BucketCreatures.light(LightTexture.pack(5, 2), LightTexture.pack(0, 0))
            == LightTexture.pack(5, 2),
        "An ordinary creature retains ambient illumination");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record Mesh(String name, Vector3f minimum, Vector3f maximum) {}
}
