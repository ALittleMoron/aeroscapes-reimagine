package dev.aeroscapes.camera;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Guards the legacy shoulder camera's redundant rotation after Sable has oriented it. */
public final class ShoulderCameraTransformer {
  private static final String CAMERA = "net/minecraft/client/Camera";
  private static final String SWAY = "com/github/exopandora/shouldersurfing/math/Vec2f";
  private static final String HANDLER_DESCRIPTOR =
      "(Lnet/minecraft/client/Camera;FFFLnet/minecraft/world/level/BlockGetter;"
          + "Lnet/minecraft/world/entity/Entity;ZZF)V";

  private ShoulderCameraTransformer() {}

  public static void patch(ClassNode camera) {
    List<MethodNode> handlers =
        camera.methods.stream().filter(ShoulderCameraTransformer::isPositionHandler).toList();
    if (!CAMERA.equals(camera.name) || handlers.size() != 1) {
      throw new IllegalStateException(
          "Expected one merged Shoulder Surfing 4.22.12 position handler");
    }
    MethodNode handler = handlers.getFirst();
    MethodInsnNode rotation = null;
    int swayLocal = -1;
    for (AbstractInsnNode instruction : handler.instructions) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.owner.equals(CAMERA)
          && call.name.equals("setRotation")
          && call.desc.equals("(FF)V")) {
        if (rotation != null) throw new IllegalStateException("Multiple shoulder rotation updates");
        rotation = call;
      }
      if (call.name.equals("calcSway") && call.desc.endsWith(")L" + SWAY + ";")) {
        if (swayLocal != -1
            || !(call.getNext() instanceof VarInsnNode store)
            || store.getOpcode() != Opcodes.ASTORE) {
          throw new IllegalStateException("Changed shoulder camera sway calculation");
        }
        swayLocal = store.var;
      }
    }
    if (rotation == null || swayLocal == -1) {
      throw new IllegalStateException("Missing shoulder camera rotation or sway calculation");
    }
    InsnList components = new InsnList();
    for (String component : List.of("x", "y")) {
      components.add(new VarInsnNode(Opcodes.ALOAD, swayLocal));
      components.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SWAY, component, "()F", false));
    }
    handler.instructions.insertBefore(rotation, components);
    handler.instructions.set(
        rotation,
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "dev/aeroscapes/camera/ShoulderCameraSway",
            "apply",
            "(Lnet/minecraft/client/Camera;FFFF)V",
            false));
    handler.maxStack = Math.max(handler.maxStack, 5);
  }

  private static boolean isPositionHandler(MethodNode method) {
    if (!method.name.endsWith("$setupPosition") || !method.desc.equals(HANDLER_DESCRIPTOR)) {
      return false;
    }
    if (method.visibleAnnotations == null) return false;
    for (AnnotationNode annotation : method.visibleAnnotations) {
      if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
          || annotation.values == null) continue;
      for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
        if ("mixin".equals(annotation.values.get(index))
            && "com.github.exopandora.shouldersurfing.mixins.MixinCamera"
                .equals(annotation.values.get(index + 1))) return true;
      }
    }
    return false;
  }
}
