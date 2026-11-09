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
    for (var instruction : travel.instructions) {
      if (instruction instanceof FieldInsnNode field
          && field.getOpcode() == Opcodes.GETSTATIC
          && field.owner.equals("com/yori3o/yo_hooks/common/util/PhysicVariables")
          && field.name.equals("climbSpeedMultiplier")
          && field.desc.equals("F")) {
        if (multiplier != null) throw new IllegalStateException("Duplicate hook travel multiplier");
        multiplier = field;
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
    travel.maxStack++;
  }
}
