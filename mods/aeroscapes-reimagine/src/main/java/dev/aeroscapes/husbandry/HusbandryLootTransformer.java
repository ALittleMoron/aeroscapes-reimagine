package dev.aeroscapes.husbandry;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Repairs the pinned provider after its loot handlers have merged into LivingEntity. */
public final class HusbandryLootTransformer {
  private static final String TARGET = "net/minecraft/world/entity/LivingEntity";
  private static final String ORIGIN = "org.athisworkshop.animalhusbandry.mixin.LivingEntityMixin";
  private static final String COW = "net/minecraft/world/entity/animal/Cow";
  private static final String SUPPLIER = "dev/architectury/registry/registries/RegistrySupplier";
  private static final String ITEM = "net/minecraft/world/level/ItemLike";
  private static final String ITEM_ENTITY = "Lnet/minecraft/world/entity/item/ItemEntity;";
  private static final String CALLBACK =
      "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
  private static final String DAMAGE = "Lnet/minecraft/world/damagesource/DamageSource;";

  private HusbandryLootTransformer() {}

  public static void patch(ClassNode target) {
    if (!target.name.equals(TARGET)) throw changed();
    MethodNode extra =
        handler(target, "animalhusbandry$extraDeathLoot", "(" + DAMAGE + "Z" + CALLBACK + ")V");
    MethodNode premium =
        handler(target, "animalhusbandry$onPremiumCowDeath", "(" + DAMAGE + CALLBACK + ")V");
    List<MethodInsnNode> extraDrops = drops(extra, "(L" + ITEM + ";I)" + ITEM_ENTITY, 3);
    List<MethodInsnNode> premiumDrops = drops(premium, "(L" + ITEM + ";)" + ITEM_ENTITY, 1);
    var unresolved = new ArrayList<FieldInsnNode>();
    for (var instruction : extra.instructions) {
      if (instruction instanceof FieldInsnNode field
          && field.getOpcode() == Opcodes.GETSTATIC
          && field.owner.equals("org/athisworkshop/animalhusbandry/registry/ModItems")
          && field.name.equals("GOLDEN_BEEF")
          && field.desc.equals("L" + SUPPLIER + ";")) {
        AbstractInsnNode next = nextCode(field);
        if (!(next instanceof TypeInsnNode cast)
            || cast.getOpcode() != Opcodes.CHECKCAST
            || !cast.desc.equals(ITEM)) throw changed();
        unresolved.add(field);
      }
    }
    if (unresolved.size() != 1) throw changed();
    extra.instructions.insert(
        unresolved.getFirst(),
        new MethodInsnNode(Opcodes.INVOKEINTERFACE, SUPPLIER, "get", "()Ljava/lang/Object;", true));
    adapt(extra, extraDrops, false);
    adapt(premium, premiumDrops, true);
  }

  private static MethodNode handler(ClassNode target, String suffix, String descriptor) {
    List<MethodNode> matches =
        target.methods.stream()
            .filter(
                method ->
                    method.name.endsWith("$" + suffix)
                        && method.desc.equals(descriptor)
                        && provider(method.visibleAnnotations))
            .toList();
    if (matches.size() != 1) throw changed();
    return matches.getFirst();
  }

  private static List<MethodInsnNode> drops(MethodNode handler, String descriptor, int expected) {
    var drops = new ArrayList<MethodInsnNode>();
    for (var instruction : handler.instructions) {
      if (instruction instanceof MethodInsnNode call) {
        if (call.owner.equals("dev/aeroscapes/husbandry/HusbandryLootDrops")) throw changed();
        if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
            && call.owner.equals(COW)
            && call.name.equals("spawnAtLocation")
            && call.desc.equals(descriptor)) drops.add(call);
      }
    }
    if (drops.size() != expected) throw changed();
    return drops;
  }

  private static void adapt(MethodNode handler, List<MethodInsnNode> drops, boolean single) {
    for (MethodInsnNode drop : drops) {
      InsnList arguments = new InsnList();
      if (single) arguments.add(new InsnNode(Opcodes.ICONST_1));
      arguments.add(new VarInsnNode(Opcodes.ALOAD, 1));
      handler.instructions.insertBefore(drop, arguments);
      handler.instructions.set(
          drop,
          new MethodInsnNode(
              Opcodes.INVOKESTATIC,
              "dev/aeroscapes/husbandry/HusbandryLootDrops",
              "spawn",
              "(L" + TARGET + ";L" + ITEM + ";I" + DAMAGE + ")" + ITEM_ENTITY,
              false));
    }
    handler.maxStack += 2;
  }

  private static boolean provider(List<AnnotationNode> annotations) {
    if (annotations == null) return false;
    for (AnnotationNode annotation : annotations) {
      if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
          || annotation.values == null) continue;
      for (int index = 0; index + 1 < annotation.values.size(); index += 2)
        if ("mixin".equals(annotation.values.get(index))
            && ORIGIN.equals(annotation.values.get(index + 1))) return true;
    }
    return false;
  }

  private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
    AbstractInsnNode next = instruction.getNext();
    while (next != null && next.getOpcode() < 0) next = next.getNext();
    return next;
  }

  private static IllegalStateException changed() {
    return new IllegalStateException("Re-audit Animal Husbandry 0.6.0 merged death-loot handlers");
  }
}
