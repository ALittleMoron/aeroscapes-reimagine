package dev.aeroscapes.ecology;

import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.launch.MixinInitialisationError;

/** Wildcard selectors exclude merged handlers; inspect the exact provider after its merge. */
public final class SpawnTintTransformer {
  private static final String CLIENT = "net/minecraft/client/multiplayer/ClientLevel";
  private static final String PROVIDER = "com.ninni.spawn.mixin.client.ClientLevelMixin";
  private static final String DESCRIPTOR =
      "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

  private SpawnTintTransformer() {}

  public static void patch(ClassNode target) {
    var handlers = target.methods.stream().filter(SpawnTintTransformer::handler).toList();
    if (!target.name.equals(CLIENT) || handlers.size() != 1)
      throw new MixinInitialisationError("Re-audit Spawn's merged water-tint handler");
    MethodNode handler = handlers.getFirst();
    var seas = new ArrayList<AbstractInsnNode>();
    var limits = new ArrayList<AbstractInsnNode>();
    for (var instruction : handler.instructions) {
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("dev/aeroscapes/ecology/SpawnClientWaterline"))
        throw new MixinInitialisationError("Spawn water-tint handler was already adapted");
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals(CLIENT)
          && call.name.equals("getSeaLevel")
          && call.desc.equals("()I")) seas.add(call);
      if (instruction instanceof LdcInsnNode constant && Float.valueOf(67.0F).equals(constant.cst))
        limits.add(constant);
    }
    if (seas.size() != 4 || limits.size() != 1)
      throw new MixinInitialisationError("Changed Spawn water-tint sea calls or transition");
    for (var instruction : seas) append(handler, instruction, "sea", "I");
    append(handler, limits.getFirst(), "tintLimit", "F");
    handler.maxStack += 2;
  }

  private static void append(
      MethodNode handler, AbstractInsnNode instruction, String name, String type) {
    InsnList code = new InsnList();
    code.add(new VarInsnNode(Opcodes.ALOAD, 0));
    code.add(new VarInsnNode(Opcodes.ALOAD, 1));
    code.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "dev/aeroscapes/ecology/SpawnClientWaterline",
            name,
            "(" + type + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)" + type,
            false));
    handler.instructions.insert(instruction, code);
  }

  private static boolean handler(MethodNode method) {
    if (!method.name.endsWith("$spawn$calculateBlockTint")
        || !method.desc.equals(DESCRIPTOR)
        || method.visibleAnnotations == null) return false;
    for (var annotation : method.visibleAnnotations) {
      if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
          || annotation.values == null) continue;
      for (int index = 0; index + 1 < annotation.values.size(); index += 2)
        if (annotation.values.get(index).equals("mixin")
            && annotation.values.get(index + 1).equals(PROVIDER)) return true;
    }
    return false;
  }
}
