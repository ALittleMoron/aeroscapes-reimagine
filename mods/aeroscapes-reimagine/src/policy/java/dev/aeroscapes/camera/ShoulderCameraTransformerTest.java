package dev.aeroscapes.camera;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Checks the executable rewrite boundary without loading a Minecraft client. */
public final class ShoulderCameraTransformerTest {
  private static final String CAMERA = "net/minecraft/client/Camera";
  private static final String HANDLER_DESCRIPTOR =
      "(Lnet/minecraft/client/Camera;FFFLnet/minecraft/world/level/BlockGetter;"
          + "Lnet/minecraft/world/entity/Entity;ZZF)V";

  private ShoulderCameraTransformerTest() {}

  public static void main(String[] arguments) {
    ClassNode camera = camera();
    MethodNode initialRotation = new MethodNode();
    initialRotation.name = "handler$example$setupRotations";
    initialRotation.desc = "()V";
    initialRotation.instructions.add(rotation());
    camera.methods.add(initialRotation);
    ShoulderCameraTransformer.patch(camera);
    MethodNode position = camera.methods.getFirst();
    List<MethodInsnNode> calls = calls(position);
    require(
        calls.stream()
            .anyMatch(
                call ->
                    call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("dev/aeroscapes/camera/ShoulderCameraSway")
                        && call.desc.equals("(Lnet/minecraft/client/Camera;FFFF)V")),
        "position update passes yaw, pitch and both sway components to the guarded setter");
    require(
        calls.stream().noneMatch(call -> call.name.equals("setRotation")),
        "position update no longer unconditionally reapplies the ship rotation");
    require(
        calls(initialRotation).stream().anyMatch(call -> call.name.equals("setRotation")),
        "initial camera rotation is retained");
    require(position.maxStack >= 5, "inserted sway loads fit the declared operand stack");
    ClassNode absent = camera();
    absent.methods.clear();
    rejects(absent);
    ClassNode duplicate = camera();
    duplicate.methods.add(handler());
    rejects(duplicate);
    ClassNode changedSetter = camera();
    changedSetter.methods.getFirst().instructions.add(rotation());
    rejects(changedSetter);
    System.out.println(
        "Shoulder camera: guarded rewrite, original rotation and stale targets passed");
  }

  private static ClassNode camera() {
    ClassNode camera = new ClassNode();
    camera.name = CAMERA;
    camera.methods.add(handler());
    return camera;
  }

  private static MethodNode handler() {
    MethodNode method = new MethodNode();
    method.name = "redirect$example$shouldersurfing$setupPosition";
    method.desc = HANDLER_DESCRIPTOR;
    AnnotationNode merged =
        new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
    merged.values =
        new ArrayList<>(
            List.of("mixin", "com.github.exopandora.shouldersurfing.mixins.MixinCamera"));
    method.visibleAnnotations = new ArrayList<>(List.of(merged));
    method.instructions.add(
        new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "com/github/exopandora/shouldersurfing/client/ShoulderSurfingCamera",
            "calcSway",
            "(Lnet/minecraft/world/entity/Entity;F)"
                + "Lcom/github/exopandora/shouldersurfing/math/Vec2f;",
            false));
    method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 13));
    method.instructions.add(rotation());
    return method;
  }

  private static MethodInsnNode rotation() {
    return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CAMERA, "setRotation", "(FF)V", false);
  }

  private static List<MethodInsnNode> calls(MethodNode method) {
    List<MethodInsnNode> result = new ArrayList<>();
    for (var instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode call) result.add(call);
    }
    return result;
  }

  private static void rejects(ClassNode camera) {
    try {
      ShoulderCameraTransformer.patch(camera);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("changed camera bytecode must require a new compatibility review");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
