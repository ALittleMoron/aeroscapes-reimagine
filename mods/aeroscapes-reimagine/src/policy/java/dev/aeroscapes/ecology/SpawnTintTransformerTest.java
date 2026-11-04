package dev.aeroscapes.ecology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.spongepowered.asm.launch.MixinInitialisationError;

/** Reads the pinned provider bytecode without initializing or starting a client. */
public final class SpawnTintTransformerTest {
  private static final String PROVIDER = "com/ninni/spawn/mixin/client/ClientLevelMixin";
  private static final String CLIENT = "net/minecraft/client/multiplayer/ClientLevel";

  private SpawnTintTransformerTest() {}

  public static void main(String[] arguments) throws IOException, AnalyzerException {
    ClassNode target = fixture();
    MethodNode handler = target.methods.getLast();
    SpawnTintTransformer.patch(target);
    long calls = 0;
    for (var instruction : handler.instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("dev/aeroscapes/ecology/SpawnClientWaterline")) calls++;
    require(calls == 5, "Four sea-level reads and one transition are adapted");
    new Analyzer<>(new BasicVerifier()).analyze(CLIENT, handler);
    rejects(target);
    ClassNode absent = fixture();
    absent.methods.removeLast();
    rejects(absent);
    ClassNode duplicate = fixture();
    duplicate.methods.add(fixture().methods.getLast());
    rejects(duplicate);
    ClassNode changed = fixture();
    var changedHandler = changed.methods.getLast();
    for (var instruction : changedHandler.instructions)
      if (instruction instanceof MethodInsnNode call && call.name.equals("getSeaLevel")) {
        call.name = "changedSeaLevel";
        break;
      }
    int before = changedHandler.instructions.size();
    rejects(changed);
    require(before == changedHandler.instructions.size(), "Reject before partial mutation");
    System.out.println(
        "Spawn actual tint bytecode: stack verification and stale/duplicate targets passed");
  }

  private static ClassNode fixture() throws IOException {
    ClassNode source = read(PROVIDER);
    ClassNode target = read(CLIENT);
    MethodNode handler =
        source.methods.stream()
            .filter(method -> method.name.equals("spawn$calculateBlockTint"))
            .findFirst()
            .orElseThrow();
    handler.name = "handler$fixture$spawn$calculateBlockTint";
    for (var instruction : handler.instructions)
      if (instruction instanceof MethodInsnNode call && call.owner.equals(PROVIDER))
        call.owner = CLIENT;
    AnnotationNode provenance =
        new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
    provenance.values = new ArrayList<>(List.of("mixin", PROVIDER.replace('/', '.')));
    handler.visibleAnnotations = new ArrayList<>(List.of(provenance));
    target.methods.add(handler);
    return target;
  }

  private static ClassNode read(String name) throws IOException {
    try (var stream =
        SpawnTintTransformerTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
      if (stream == null) throw new IOException("Missing pinned class " + name);
      ClassNode node = new ClassNode();
      new ClassReader(stream).accept(node, 0);
      return node;
    }
  }

  private static void rejects(ClassNode target) {
    try {
      SpawnTintTransformer.patch(target);
    } catch (MixinInitialisationError expected) {
      return;
    }
    throw new AssertionError("Changed provider must require re-audit");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
