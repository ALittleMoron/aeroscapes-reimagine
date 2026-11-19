package dev.aeroscapes.husbandry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Exercises the actual pinned loot handlers after a simulated native Mixin merge. */
public final class HusbandryLootTransformerTest {
  private static final String PROVIDER =
      "org/athisworkshop/animalhusbandry/mixin/LivingEntityMixin";
  private static final String TARGET = "net/minecraft/world/entity/LivingEntity";

  private HusbandryLootTransformerTest() {}

  public static void main(String[] arguments) throws IOException, AnalyzerException {
    ClassNode target = fixture();
    long before = getterCalls(target);
    HusbandryLootTransformer.patch(target);
    require(getterCalls(target) == before + 1, "The missing registry dereference is restored once");
    int adapted = 0;
    for (MethodNode method : target.methods) {
      if (!method.name.startsWith("handler$fixture$")) continue;
      for (var instruction : method.instructions)
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals("dev/aeroscapes/husbandry/HusbandryLootDrops")) {
          adapted++;
          require(
              call.name.equals("spawn") && call.getOpcode() == Opcodes.INVOKESTATIC,
              "Direct trait drop delegates through the shared native-recipe path");
        }
      new Analyzer<>(new BasicVerifier()).analyze(TARGET, method);
    }
    require(adapted == 4, "Every direct beef, leather and both golden-beef site is covered");
    rejects(target);
    ClassNode missing = fixture();
    missing.methods.removeLast();
    rejects(missing);
    ClassNode changed = fixture();
    var handler = changed.methods.getLast();
    for (var instruction : handler.instructions)
      if (instruction instanceof MethodInsnNode call && call.name.equals("spawnAtLocation")) {
        call.name = "changedDrop";
        break;
      }
    int size = changed.methods.get(changed.methods.size() - 2).instructions.size();
    rejects(changed);
    require(
        changed.methods.get(changed.methods.size() - 2).instructions.size() == size,
        "Stale native bytecode is rejected before any partial mutation");
    System.out.println(
        "Husbandry native loot: registry resolution, all direct drops and stack contracts passed");
  }

  private static long getterCalls(ClassNode target) {
    long count = 0;
    for (MethodNode method : target.methods)
      for (var instruction : method.instructions)
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals("dev/architectury/registry/registries/RegistrySupplier")
            && call.name.equals("get")) count++;
    return count;
  }

  private static ClassNode fixture() throws IOException {
    ClassNode source = read(PROVIDER);
    ClassNode target = read(TARGET);
    for (String name :
        List.of("animalhusbandry$extraDeathLoot", "animalhusbandry$onPremiumCowDeath")) {
      MethodNode handler =
          source.methods.stream()
              .filter(method -> method.name.equals(name))
              .findFirst()
              .orElseThrow();
      handler.name = "handler$fixture$" + name;
      AnnotationNode provenance =
          new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
      provenance.values = new ArrayList<>(List.of("mixin", PROVIDER.replace('/', '.')));
      handler.visibleAnnotations = new ArrayList<>(List.of(provenance));
      target.methods.add(handler);
    }
    return target;
  }

  private static ClassNode read(String name) throws IOException {
    try (var stream =
        HusbandryLootTransformerTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
      if (stream == null) throw new IOException("Missing pinned class " + name);
      ClassNode node = new ClassNode();
      new ClassReader(stream).accept(node, 0);
      return node;
    }
  }

  private static void rejects(ClassNode target) {
    try {
      HusbandryLootTransformer.patch(target);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Changed or duplicate native loot must require re-audit");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
