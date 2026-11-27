package dev.aeroscapes.magic;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Covers the Minecraft background pass that would blur inscription modal content. */
public final class RepeatStudyRenderOrderTest {
  private static final String SCREEN = "net/minecraft/client/gui/screens/Screen";
  private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";
  private static final String RENDER = "(L" + GRAPHICS + ";IIF)V";

  private RepeatStudyRenderOrderTest() {}

  public static void verify() throws IOException {
    var vanilla = read(SCREEN);
    var frame = calls(vanilla, "render");
    require(
        first(frame, SCREEN, "renderBackground") >= 0
            && first(frame, SCREEN, "renderBackground")
                < first(frame, "net/minecraft/client/gui/components/Renderable", "render")
            && first(calls(vanilla, "renderBackground"), SCREEN, "renderBlurredBackground") >= 0,
        "The pinned screen background applies blur before widgets");

    var repeat = read("dev/aeroscapes/magic/client/SpellStudyScreen$RepeatStudy");
    var body = calls(repeat, "renderBackground");
    int background = first(body, SCREEN, "renderBackground");
    require(
        background >= 0
            && first(body, GRAPHICS, "fill") > background
            && first(body, GRAPHICS, "drawString") > background,
        "The modal shade and readable study content must draw after the background blur");
    var overlay = calls(repeat, "render");
    int widgetFrame = first(overlay, SCREEN, "render");
    require(
        widgetFrame >= 0
            && overlay.subList(0, widgetFrame).stream()
                .noneMatch(call -> call.owner.equals(GRAPHICS)),
        "The modal may only draw overlays after its background and widgets");
  }

  private static ClassNode read(String name) throws IOException {
    var result = new ClassNode();
    try (var stream = RepeatStudyRenderOrderTest.class.getResourceAsStream("/" + name + ".class")) {
      if (stream == null) throw new AssertionError("Missing class " + name);
      new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG);
    }
    return result;
  }

  private static List<MethodInsnNode> calls(ClassNode owner, String method) {
    return owner.methods.stream()
        .filter(candidate -> candidate.name.equals(method) && candidate.desc.equals(RENDER))
        .findFirst()
        .stream()
        .flatMap(found -> Arrays.stream(found.instructions.toArray()))
        .filter(MethodInsnNode.class::isInstance)
        .map(MethodInsnNode.class::cast)
        .toList();
  }

  private static int first(List<MethodInsnNode> calls, String owner, String method) {
    for (int index = 0; index < calls.size(); index++) {
      var call = calls.get(index);
      if (call.owner.equals(owner) && call.name.equals(method)) return index;
    }
    return -1;
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new AssertionError(reason);
  }
}
