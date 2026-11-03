package dev.aeroscapes.movement;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
