package dev.aeroscapes.movement;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class HookReforgeTravelTransformer {
  private HookReforgeTravelTransformer() {}

  static void patch(ClassNode player) {
    // The provider's injected travel handler has a generated name after mixin merging.
    var handlers =
        player.methods.stream()
            .filter(method -> method.name.endsWith("yo_hooks$onTravel"))
            .filter(
                method ->
                    method.desc.equals(
                        "(Lnet/minecraft/world/phys/Vec3;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"))
            .toList();
    if (handlers.size() != 1) throw new IllegalStateException("Review hook travel handler");
    var travel = handlers.getFirst();
    FieldInsnNode multiplier = null;
    MethodInsnNode velocity = null;
    for (var instruction : travel.instructions) {
      if (instruction instanceof FieldInsnNode field
          && field.getOpcode() == Opcodes.GETSTATIC
          && field.owner.equals("com/yori3o/yo_hooks/common/util/PhysicVariables")
          && field.name.equals("climbSpeedMultiplier")
          && field.desc.equals("F")) {
        if (multiplier != null) throw new IllegalStateException("Duplicate hook travel multiplier");
        multiplier = field;
      }
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("net/minecraft/world/entity/player/Player")
          && call.name.equals("setDeltaMovement")
          && call.desc.equals("(Lnet/minecraft/world/phys/Vec3;)V")) {
        if (velocity != null) throw new IllegalStateException("Duplicate hook travel velocity");
        velocity = call;
      }
    }
    if (multiplier == null) throw new IllegalStateException("Missing hook travel multiplier");
    var adjust = new InsnList();
    adjust.add(new VarInsnNode(Opcodes.ALOAD, 0));
    adjust.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "dev/aeroscapes/movement/HookReforgeSpeed",
            "climbingMotion",
            "(FLnet/minecraft/world/entity/player/Player;)F",
            false));
    travel.instructions.insert(multiplier, adjust);
    if (velocity == null) throw new IllegalStateException("Missing hook travel velocity");
    var stabilize = new InsnList();
    stabilize.add(new VarInsnNode(Opcodes.ALOAD, 0));
    stabilize.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "dev/aeroscapes/movement/HookReforgeSpeed",
            "stabilize",
            "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/phys/Vec3;",
            false));
    travel.instructions.insertBefore(velocity, stabilize);
    travel.maxStack++;
  }
}
