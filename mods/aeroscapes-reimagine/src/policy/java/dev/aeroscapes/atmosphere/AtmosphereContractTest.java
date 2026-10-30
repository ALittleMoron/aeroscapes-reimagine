package dev.aeroscapes.atmosphere;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.jem_objects.EMFPartData;

/** Checks the provider boundaries used by client adapters without starting Minecraft. */
public final class AtmosphereContractTest {
  private AtmosphereContractTest() {}

  public static void main(String[] arguments) throws IOException {
    quiverComposition();
    fastRodRenderers();
    ClassNode attachment = provider("punchy/client/render/FishingLineAttachment");
    method(
        attachment,
        "beginWorld",
        "(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
    method(
        attachment,
        "capture",
        "(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;Lorg/joml/Matrix4f;)V");
    method(
        attachment,
        "captureModelTip",
        "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;)V");
    method(
        provider("punchy/client/render/FishingLineProjection"),
        "projectTip",
        "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;");
    for (String id : List.of("rain", "heavy_rain", "snow", "heavy_snow")) {
      require(PrecipitationPolicy.isAirbornePrecipitation(id, true), "sky precipitation opts out");
      require(!PrecipitationPolicy.isAirbornePrecipitation(id, false), "surface data stays native");
    }
    for (String id : List.of("fog", "dust", "ripple", "splatter", "new_particle")) {
      require(!PrecipitationPolicy.isAirbornePrecipitation(id, true), "other effects stay native");
    }
    require(
        !PrecipitationPolicy.isAirbornePrecipitation(null, true),
        "uninitialized data stays native");

    ClassNode rain = provider("pigcart/particlerain/ParticleRain");
    MethodNode wind = method(rain, "getWind", "(DDD)Lorg/joml/Vector3f;");
    List<AbstractInsnNode> instructions = executable(wind);
    int zeros = 0;
    for (int index = 0; index < instructions.size(); index++) {
      if (instructions.get(index).getOpcode() != Opcodes.FCONST_0) continue;
      zeros++;
      if (zeros == 1) {
        require(instructions.get(index - 1) instanceof JumpInsnNode, "wind ternary join");
        require(
            instructions.get(index + 1).getOpcode() == Opcodes.FSTORE, "altitude multiplier store");
        require(
            instructions.subList(0, index).stream()
                .anyMatch(
                    instruction ->
                        instruction instanceof FieldInsnNode field
                            && field.name.equals("yLevelAdjustment")),
            "native altitude option");
        require(
            instructions.get(index - 2) instanceof MethodInsnNode call
                && call.name.equals("yLevelWindMultiplier")
                && call.desc.equals("(D)F"),
            "native adjusted branch");
      }
    }
    require(zeros == 2, "only the disabled altitude multiplier and vector Y are zero constants");
    ClassNode version = provider("pigcart/particlerain/VersionUtil");
    for (String name : List.of("getMinY", "getMaxY")) {
      require(
          (method(version, name, "(Lnet/minecraft/world/level/Level;)I").access
                  & Opcodes.ACC_STATIC)
              != 0,
          "static height helper");
    }
    ClassNode custom = provider("pigcart/particlerain/particle/CustomParticle");
    method(
        custom,
        "<init>",
        "(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLpigcart/particlerain/config/ParticleData;)V");
    ClassNode kickable = provider("dev/ryanhcode/sable/api/particle/ParticleSubLevelKickable");
    for (String name :
        List.of(
            "sable$shouldCareAboutIntersectingSubLevels",
            "sable$shouldKickFromTracking",
            "sable$shouldCollideWithTrackingSubLevel")) method(kickable, name, "()Z");

    ClassNode arms = provider("punchy/client/render/PunchyArmRenderer");
    String renderDescriptor =
        "(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    MethodNode render = method(arms, "renderFirstPerson", renderDescriptor);
    require((render.access & Opcodes.ACC_STATIC) != 0, "static Punchy entry point");
    require(
        executable(render).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals(arms.name)
                            && call.name.equals("renderFirstPersonInternal")
                            && call.desc.equals(renderDescriptor))
                .count()
            == 1,
        "one scoped Punchy render invocation");
    ClassNode emf =
        provider("traben/entity_model_features/models/animation/state/EMFEntityRenderState");
    method(emf, "manualPlayerState", "()L" + emf.name + ";");
    method(emf, "setIsFirstPersonHand", "(Z)V");
    ClassNode etf = provider("traben/entity_texture_features/features/state/ETFState");
    method(etf, "mount", "(Ltraben/entity_texture_features/features/state/ETFEntityRenderState;)V");
    method(etf, "unMount", "()V");
    method(
        provider("traben/entity_model_features/models/jem_objects/EMFJemData"),
        "prepare",
        "(Ltraben/entity_model_features/utils/EMFDirectoryHandler;Ltraben/entity_model_features/models/EMFModel_ID;)V");
    loadingOverlay();
    System.out.println(
        "Atmosphere: quiver composition, precipitation scope and native API contracts passed");
  }

  private static void quiverComposition() {
    for (String mob : List.of("skeleton", "stray", "bogged")) {
      EMFJemData base = new EMFJemData();
      EMFPartData animation = new EMFPartData();
      animation.model = "skeleton_animations.jpm";
      base.models.add(animation);
      EMFPartData head = new EMFPartData();
      head.id = "head";
      base.models.add(head);
      EMFPartData mushrooms = new EMFPartData();
      mushrooms.model = "bogged_mushroom_animations.jpm";
      if (mob.equals("bogged")) base.models.add(mushrooms);
      EMFJemData selected = new EMFJemData();
      EMFPartData quiver = new EMFPartData();
      quiver.part = "body";
      quiver.id = "quiver";
      quiver.model = mob + "_quiver.jpm";
      quiver.attach = true;
      selected.models.add(quiver);
      require(QuiverCompatibility.compose(selected, base, mob), "quiver composition applies");
      require(selected.models.getFirst() == animation, "native animation reference retained");
      require(selected.models.contains(head), "native head and family flags retained");
      require(selected.models.getLast() == quiver, "original body attachment retained");
      require(base.models.size() == (mob.equals("bogged") ? 3 : 2), "base list unchanged");
      require(
          selected.models.contains(mushrooms) == mob.equals("bogged"),
          "bogged mushroom animation retained");
      selected.models.clear();
      require(!QuiverCompatibility.compose(selected, base, mob), "disabled quiver stays absent");
      selected.models.add(quiver);
      quiver.model = "other_pack.jpm";
      require(!QuiverCompatibility.compose(selected, base, mob), "other models stay unchanged");
      require(selected.models.size() == 1, "unrelated model list unchanged");
    }
  }

  private static void loadingOverlay() throws IOException {
    ClassNode nativeMixin = provider("net/puzzlemc/splashscreen/mixin/MixinNeoForgeLoadingOverlay");
    MethodNode nativeHandler =
        nativeMixin.methods.stream()
            .filter(method -> method.name.equals("puzzle$initTexturesNeoforge"))
            .findFirst()
            .orElseThrow();
    ClassNode overlay = mergedOverlay(nativeHandler);
    MethodNode unrelated = new MethodNode();
    unrelated.name = "nativeLogoRegistration";
    unrelated.desc = "()V";
    unrelated.instructions.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "net/minecraft/client/gui/screens/LoadingOverlay",
            "registerTextures",
            "(Lnet/minecraft/client/Minecraft;)V",
            false));
    overlay.methods.add(unrelated);
    PuzzleLoadingOverlayTransformer.patch(overlay);
    require(
        executable(overlay.methods.getFirst()).stream()
            .map(AbstractInsnNode::getOpcode)
            .toList()
            .equals(List.of(Opcodes.ALOAD, Opcodes.POP, Opcodes.RETURN)),
        "only Puzzle's redundant registration is consumed without deleting the original logo");
    require(
        unrelated.instructions.getFirst() instanceof MethodInsnNode,
        "native logo registration is retained");
    rejectsOverlay(overlay);
    ClassNode absent = mergedOverlay(nativeHandler);
    absent.methods.getFirst().visibleAnnotations = null;
    rejectsOverlay(absent);
    ClassNode duplicate = mergedOverlay(nativeHandler);
    duplicate.methods.add(mergedOverlay(nativeHandler).methods.getFirst());
    rejectsOverlay(duplicate);
  }

  private static ClassNode mergedOverlay(MethodNode nativeHandler) {
    ClassNode overlay = new ClassNode();
    overlay.name = "net/neoforged/neoforge/client/loading/NeoForgeLoadingOverlay";
    MethodNode handler =
        new MethodNode(
            Opcodes.ASM9,
            nativeHandler.access,
            "handler$fixture$puzzle$initTexturesNeoforge",
            nativeHandler.desc,
            null,
            null);
    nativeHandler.accept(handler);
    AnnotationNode merged =
        new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
    merged.values =
        new ArrayList<>(
            List.of("mixin", "net.puzzlemc.splashscreen.mixin.MixinNeoForgeLoadingOverlay"));
    if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
    handler.visibleAnnotations.add(merged);
    overlay.methods.add(handler);
    return overlay;
  }

  private static void rejectsOverlay(ClassNode overlay) {
    try {
      PuzzleLoadingOverlayTransformer.patch(overlay);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Changed Puzzle overlay must require a new compatibility review");
  }

  private static void fastRodRenderers() throws IOException {
    String fastDescriptor =
        "(Lnet/minecraft/client/resources/model/SimpleBakedModel;ILnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lnet/minecraft/client/color/item/ItemColors;)V";
    for (String name :
        List.of(
            "com/moepus/flerovium/functions/FastSimpleBakedModelRenderer",
            "com/moepus/flerovium/Iris/IrisSimpleBakedItemRenderer")) {
      MethodNode renderer = method(provider(name), "render", fastDescriptor);
      require(
          (renderer.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC))
              == (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC),
          "public static fast renderer");
      List<AbstractInsnNode> code = executable(renderer);
      require(
          code.getFirst() instanceof org.objectweb.asm.tree.VarInsnNode local
              && local.getOpcode() == Opcodes.ALOAD
              && local.var == 5,
          "fast renderer uses actual incoming pose");
      require(
          code.get(1) instanceof MethodInsnNode call
              && call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")
              && call.name.equals("last"),
          "same pose passed to mesh emission");
      require(
          code.stream()
              .anyMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call && call.name.equals("getQuads")),
          "real model geometry still drives fast draw");
      require(
          code.stream()
              .noneMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call
                          && call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")
                          && !call.name.equals("last")),
          "entry pose stays unchanged before vertices");
    }
  }

  private static ClassNode provider(String name) throws IOException {
    for (String file : System.getProperty("aeroscapes.atmosphere.apis").split(File.pathSeparator)) {
      try (ZipFile archive = new ZipFile(Path.of(file).toFile())) {
        var entry = archive.getEntry(name + ".class");
        if (entry == null) continue;
        ClassNode type = new ClassNode();
        try (var input = archive.getInputStream(entry)) {
          new ClassReader(input.readAllBytes()).accept(type, 0);
        }
        return type;
      }
    }
    throw new AssertionError("Missing provider class " + name);
  }

  private static MethodNode method(ClassNode type, String name, String descriptor) {
    return type.methods.stream()
        .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Changed provider method " + type.name + "." + name));
  }

  private static List<AbstractInsnNode> executable(MethodNode method) {
    List<AbstractInsnNode> result = new ArrayList<>();
    for (var instruction : method.instructions)
      if (instruction.getOpcode() >= 0) result.add(instruction);
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
