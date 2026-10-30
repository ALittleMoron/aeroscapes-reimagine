package dev.aeroscapes.atmosphere;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Keeps the logo texture that NeoForge's early display already owns. */
public final class PuzzleLoadingOverlayTransformer {
  private static final String ORIGIN =
      "net.puzzlemc.splashscreen.mixin.MixinNeoForgeLoadingOverlay";
  private static final String DESCRIPTOR =
      "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/resources/ReloadInstance;"
          + "Ljava/util/function/Consumer;Lnet/neoforged/fml/earlydisplay/DisplayWindow;"
          + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

  private PuzzleLoadingOverlayTransformer() {}

  public static void patch(ClassNode overlay) {
    List<MethodNode> handlers =
        overlay.methods.stream().filter(PuzzleLoadingOverlayTransformer::isPuzzleHandler).toList();
    if (!overlay.name.equals("net/neoforged/neoforge/client/loading/NeoForgeLoadingOverlay")
        || handlers.size() != 1) {
      throw new IllegalStateException("Expected Puzzle 2.3.0's merged NeoForge logo handler");
    }
    MethodNode handler = handlers.getFirst();
    MethodInsnNode registration = null;
    for (var instruction : handler.instructions) {
      if (instruction instanceof MethodInsnNode call
          && call.getOpcode() == Opcodes.INVOKESTATIC
          && call.owner.equals("net/minecraft/client/gui/screens/LoadingOverlay")
          && call.name.equals("registerTextures")
          && call.desc.equals("(Lnet/minecraft/client/Minecraft;)V")) {
        if (registration != null)
          throw new IllegalStateException("Duplicate Puzzle logo registration");
        registration = call;
      }
    }
    if (registration == null) throw new IllegalStateException("Changed Puzzle logo handler");
    // Consume its Minecraft argument; keep the original early-window texture alive.
    handler.instructions.set(registration, new InsnNode(Opcodes.POP));
  }

  private static boolean isPuzzleHandler(MethodNode method) {
    if (!method.name.endsWith("puzzle$initTexturesNeoforge")
        || !method.desc.equals(DESCRIPTOR)
        || method.visibleAnnotations == null) return false;
    for (AnnotationNode annotation : method.visibleAnnotations) {
      if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
          || annotation.values == null) continue;
      for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
        if ("mixin".equals(annotation.values.get(index))
            && ORIGIN.equals(annotation.values.get(index + 1))) return true;
      }
    }
    return false;
  }
}
