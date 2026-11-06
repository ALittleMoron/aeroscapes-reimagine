package dev.aeroscapes.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import punchy.client.render.FishingLineProjection;

/** Tests preference migration and the emitted handle-to-world rope endpoint without a client. */
public final class HookCompatibilityTest {
  private HookCompatibilityTest() {}

  public static void main(String[] arguments) throws IOException {
    migration();
    nativeContracts();
    renderOrder();
    HookChainGeometryTest.verify(Path.of(arguments[1]));
    require(
        HookItemView.select(false, true, false, true, true) == HookItemView.FIRST_PERSON,
        "Punchy's third-person item transform still captures the first-person rope endpoint");
    require(
        HookItemView.select(true, true, false, true, true) == HookItemView.FIRST_PERSON,
        "Punchy captures cannot be mistaken for world player items");
    require(
        HookItemView.select(true, false, false, true, false) == HookItemView.WORLD,
        "Third-person player items supply current-frame world endpoints");
    require(
        HookItemView.select(true, false, false, true, true) == HookItemView.WORLD,
        "Other players remain world captures with the local camera in first person");
    require(
        HookItemView.select(false, false, false, true, false) == HookItemView.NONE,
        "Inventory player previews cannot change world rope endpoints");
    require(
        HookItemView.select(false, false, true, false, true) == HookItemView.FIRST_PERSON,
        "Native first-person item rendering remains supported");
    for (String model :
        List.of("grappling_hook_handle.json", "grappling_hook_handle_netherite.json")) {
      Vector3f eye = modelEye(Path.of(arguments[0]).resolve(model));
      near(
          HookHandleProjection.project(new Matrix4f(), new Matrix4f()),
          eye,
          "The attachment matches the actual handle's upper eye");
      projection(eye);
    }
    System.out.println(
        "Hook controls: migration decisions, native contracts and handle projection passed");
  }

  private static void renderOrder() {
    for (List<String> input :
        List.of(
            List.<String>of(),
            List.of("hook-a"),
            List.of("player-a", "player-b"),
            List.of("hook-a", "player-a", "hook-b", "player-b"))) {
      List<String> expected = new ArrayList<>();
      input.stream().filter(value -> !value.startsWith("hook")).forEach(expected::add);
      input.stream().filter(value -> value.startsWith("hook")).forEach(expected::add);
      List<String> actual = new ArrayList<>();
      DeferredRenderOrder.last(input.iterator(), value -> value.startsWith("hook"))
          .forEachRemaining(actual::add);
      require(
          actual.equals(expected),
          "All owners precede hooks without dropping or duplicating entries");
    }
    for (float mirror : List.of(-1f, 1f)) {
      Matrix4f worldView = new Matrix4f().rotateX(.35f).rotateY(-.8f);
      Matrix4f modelWorld =
          new Matrix4f()
              .translate(5, -2, 13)
              .rotateY(1.1f)
              .rotateZ(.4f)
              .scale(mirror * .7f, .9f, .7f);
      Vector3f eye = new Vector3f(.5f, 9.8f / 16, .5f);
      near(
          HookHandleProjection.worldOffset(worldView, new Matrix4f(worldView).mul(modelWorld)),
          modelWorld.transformPosition(eye),
          "Third-person endpoint follows the final mirrored arm and ship matrix");
    }
  }

  private static void migration() {
    String up = "key.keyboard.page.up";
    String down = "key.keyboard.page.down";
    require(HookControlsMigration.shouldMigrate(0, up, down, true), "Upgrade the prior pack pair");
    for (int version :
        List.of(-1, HookControlsMigration.VERSION, HookControlsMigration.VERSION + 1))
      require(
          !HookControlsMigration.shouldMigrate(version, up, down, true),
          "A saved or unknown migration version preserves an intentional return to the old pair");
    require(!HookControlsMigration.shouldMigrate(0, up, down, false), "Keep modifier chords");
    for (String custom :
        List.of(
            "key.keyboard.left.shift",
            "key.keyboard.r",
            "key.keyboard.unknown",
            "key.mouse.left",
            "scancode.266")) {
      require(
          !HookControlsMigration.shouldMigrate(0, custom, down, true),
          "Keep custom, partial and non-KEYSYM climb mappings");
      require(
          !HookControlsMigration.shouldMigrate(0, up, custom, true),
          "Keep custom, partial and non-KEYSYM descent mappings");
    }
    require(
        !HookControlsMigration.shouldMigrate(0, down, up, true), "Keep a reversed personal pair");
  }

  private static Vector3f modelEye(Path path) throws IOException {
    float highest = -Float.MAX_VALUE;
    Vector3f eye = new Vector3f();
    for (JsonElement entry :
        JsonParser.parseString(Files.readString(path))
            .getAsJsonObject()
            .getAsJsonArray("elements")) {
      var element = entry.getAsJsonObject();
      var from = element.getAsJsonArray("from");
      var to = element.getAsJsonArray("to");
      float top = to.get(1).getAsFloat();
      if (top > highest) {
        highest = top;
        eye.set(
            (from.get(0).getAsFloat() + to.get(0).getAsFloat()) / 32,
            top / 16,
            (from.get(2).getAsFloat() + to.get(2).getAsFloat()) / 32);
      }
    }
    require(highest > 0, "The active handle has geometry");
    return eye;
  }

  private static void projection(Vector3f eye) {
    for (float side : List.of(-1f, 1f)) {
      Matrix4f itemView =
          new Matrix4f().translation(side * 0.35f, -0.25f, -1.2f).rotateZ(side * 0.7f).scale(0.7f);
      for (float fov : List.of(35f, 70f, 110f)) {
        Matrix4f handProjection =
            new Matrix4f().perspective((float) Math.toRadians(fov), 16 / 9f, 0.05f, 1024);
        Vector3f actual = HookHandleProjection.project(itemView, handProjection);
        Vector4f expected = new Vector4f(eye, 1).mul(itemView).mul(handProjection);
        near(
            actual,
            new Vector3f(expected.x, expected.y, expected.z).div(expected.w),
            "Follow the emitted mirrored/rotated/scaled handle");
        Matrix4f worldView = new Matrix4f().rotateX(0.4f).rotateY(-0.7f);
        Matrix4f worldProjection =
            new Matrix4f().perspective((float) Math.toRadians(fov + 5), 16 / 9f, 0.05f, 2048);
        Vector3f world = FishingLineProjection.unprojectTip(worldView, worldProjection, actual);
        require(world != null, "A valid item point can be projected into the world");
        near(
            FishingLineProjection.projectTip(worldView, worldProjection, world),
            actual,
            "World rope and hand endpoint share screen coordinates across FOVs");
        Vector3f moved =
            HookHandleProjection.project(
                new Matrix4f(itemView).translate(0.25f, 0.1f, 0), handProjection);
        require(moved.distance(actual) > 0.01f, "The endpoint moves with item animation");
      }
    }
  }

  private static void nativeContracts() throws IOException {
    hangingContracts();
    ClassNode options = provider("net/minecraft/client/Options");
    method(options, "processOptions", "(Lnet/minecraft/client/Options$FieldAccess;)V");
    method(options, "load", "(Z)V");
    method(
        provider("net/minecraft/client/Options$FieldAccess"), "process", "(Ljava/lang/String;I)I");
    ClassNode attachment = provider("punchy/client/render/FishingLineAttachment");
    method(attachment, "reset", "()V");
    method(
        attachment,
        "beginWorld",
        "(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
    ClassNode hooks = provider("com/yori3o/yo_hooks/common/YoHooksClient");
    ClassNode renderer = provider("com/yori3o/yo_hooks/common/client/render/LegacyHookRenderer");
    method(
        renderer,
        "vertex",
        "(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFFFFI)V");
    calls(
        method(
            renderer,
            "render",
            "(Lcom/yori3o/yo_hooks/common/entity/HookEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        renderer.name,
        "vertex",
        "(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFFFFI)V",
        8);
    int mappings = 0;
    for (var instruction : method(hooks, "<clinit>", "()V").instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("net/minecraft/client/KeyMapping")
          && call.name.equals("<init>")
          && call.desc.equals(
              "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V"))
        mappings++;
    require(mappings == 4, "The pinned provider registers exactly four native key mappings");
    MethodNode hand =
        method(
            provider("com/yori3o/yo_hooks/common/client/render/LegacyHookRenderer"),
            "getHandPosition",
            "(Lcom/yori3o/yo_hooks/common/entity/HookEntity;Lnet/minecraft/world/entity/player/Player;FLnet/minecraft/client/renderer/entity/EntityRenderDispatcher;Z)Lnet/minecraft/world/phys/Vec3;");
    require((hand.access & Opcodes.ACC_STATIC) != 0, "Native hand projection remains static");
    method(
        provider("net/minecraft/client/renderer/ItemInHandRenderer"),
        "renderItem",
        "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V");
    MethodNode world =
        method(
            provider("net/minecraft/client/renderer/LevelRenderer"),
            "renderLevel",
            "(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
    calls(world, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", 1);
  }

  private static void hangingContracts() throws IOException {
    ClassNode arms = provider("punchy/client/render/PunchyArmRenderer");
    String matrix = "Lorg/joml/Matrix4f;";
    String player = "Lnet/minecraft/client/player/LocalPlayer;";
    String arm = "Lnet/minecraft/world/entity/HumanoidArm;";
    MethodNode apply =
        method(
            arms,
            "applyGeoArmToPlayer",
            "(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;"
                + matrix
                + player
                + arm
                + "F)V");
    require(
        (apply.access & Opcodes.ACC_STATIC) != 0,
        "The selected first-person pose seam remains static with one partial-tick argument");
    calls(apply, arms.name, "cacheArmWorld", "(" + player + arm + matrix + ")V", 1);
    method(arms, "cacheArmWorld", "(" + player + arm + matrix + ")V");
    MethodNode render =
        method(
            provider("net/minecraft/client/renderer/entity/LivingEntityRenderer"),
            "render",
            "(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V");
    calls(
        render,
        "net/minecraft/client/model/EntityModel",
        "setupAnim",
        "(Lnet/minecraft/world/entity/Entity;FFFFF)V",
        1);
    ClassNode hook = provider("com/yori3o/yo_hooks/common/entity/HookEntity");
    method(
        hook,
        "getHoldingHand",
        "(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionHand;");
    method(hook, "isControlledBy", "(Lnet/minecraft/world/entity/player/Player;)Z");
    method(hook, "getLength", "()F");
    MethodNode hit = method(hook, "onHitBlock", "(Lnet/minecraft/world/phys/BlockHitResult;)V");
    calls(hit, hook.name, "setInSableBlock", "(Z)V", 1);
    calls(hit, hook.name, "setInBlock", "(Z)V", 1);
    method(
        provider("com/yori3o/yo_hooks/common/compat/sable/SableCompat"),
        "getHookPos",
        "(Lnet/minecraft/world/phys/Vec3;Lcom/yori3o/yo_hooks/common/entity/HookEntity;)Lnet/minecraft/world/phys/Vec3;");
  }

  private static void calls(
      MethodNode method, String owner, String name, String descriptor, int expected) {
    int count = 0;
    for (var instruction : method.instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals(owner)
          && call.name.equals(name)
          && call.desc.equals(descriptor)) count++;
    require(count == expected, "Changed render/attachment seam " + owner + "." + name);
  }

  private static ClassNode provider(String name) throws IOException {
    try (var stream =
        HookCompatibilityTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
      if (stream == null) throw new AssertionError("Missing pinned class " + name);
      ClassNode node = new ClassNode();
      new ClassReader(stream).accept(node, 0);
      return node;
    }
  }

  private static MethodNode method(ClassNode owner, String name, String descriptor) {
    return owner.methods.stream()
        .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
        .findFirst()
        .orElseThrow(() -> new AssertionError(owner.name + "." + name + descriptor));
  }

  private static void near(Vector3f actual, Vector3f expected, String message) {
    require(actual != null && actual.distance(expected) < 0.0001f, message);
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }
}
