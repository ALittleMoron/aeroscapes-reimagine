package dev.aeroscapes.aquatic;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;

/**
 * Native Mixin selector matching against the exact provider classes, without initializing clients.
 */
final class ConstructionMixinContract {
  private ConstructionMixinContract() {}

  static void verify() throws IOException {
    var loader = ConstructionMixinContract.class.getClassLoader();
    try (var stream = loader.getResourceAsStream("aeroscapes_reimagine.construction.mixins.json")) {
      if (stream == null) throw new IOException("Missing construction Mixin configuration");
      var config =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      int sites = 0;
      for (String side : List.of("mixins", "client"))
        for (var entry : config.getAsJsonArray(side))
          sites += verifyMixin(read("dev/aeroscapes/mixin/construction/" + entry.getAsString()));
      if (sites != 21)
        throw new AssertionError("Expected twenty-one guarded construction injection sites");
    }
    var outer = read("team/creative/littletiles/common/packet/action/BlockPacket");
    var literal =
        TargetSelector.parseAndValidate(
            "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
            null);
    boolean found =
        outer.methods.stream()
            .flatMap(
                method ->
                    java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
            .filter(MethodInsnNode.class::isInstance)
            .anyMatch(instruction -> literal.match(ElementNode.of(instruction)).isExactMatch());
    if (found)
      throw new AssertionError(
          "Re-audit outer packet: native grid action belongs to its enum subclass");
    System.out.println(
        "Construction Mixin selectors: twenty-one exact-artifact sites and enum-owner regression passed");
  }

  private static int verifyMixin(ClassNode mixin) throws IOException {
    AnnotationNode metadata =
        annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
    List<String> targets = strings(value(metadata, "targets"));
    if (targets.size() != 1)
      throw new AssertionError("Construction adapters must bind one actual owner: " + mixin.name);
    ClassNode target = read(targets.getFirst().replace('.', '/'));
    int sites = 0;
    for (MethodNode handler : mixin.methods) {
      if (handler.visibleAnnotations == null) continue;
      for (AnnotationNode injection : handler.visibleAnnotations) {
        if (!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
            && !injection.desc.equals("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;")
            && !injection.desc.equals(
                "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
        Object rawAt = value(injection, "at");
        if (rawAt instanceof List<?> points && points.size() == 1) rawAt = points.getFirst();
        if (!(rawAt instanceof AnnotationNode at))
          throw new AssertionError("Expected single construction invocation point");
        boolean head = value(at, "value").equals("HEAD");
        boolean returning = value(at, "value").equals("RETURN");
        if (!head && !returning && !value(at, "value").equals("INVOKE"))
          throw new AssertionError("Expected native entry or invocation boundary");
        Object rawTarget = head || returning ? "" : value(at, "target");
        if (!(rawTarget instanceof String invocation))
          throw new AssertionError("Missing invocation selector");
        var callSelector =
            head || returning ? null : TargetSelector.parseAndValidate(invocation, null);
        int calls = 0;
        for (String selector : strings(value(injection, "method"))) {
          if (selector.equals("*"))
            throw new AssertionError("Construction literal sites require explicit methods");
          var methodSelector = TargetSelector.parseAndValidate(selector, null);
          List<MethodNode> selected =
              target.methods.stream()
                  .filter(
                      method -> methodSelector.match(ElementNode.of(target, method)).isExactMatch())
                  .toList();
          if (selected.size() != 1)
            throw new AssertionError(
                "Missing or ambiguous native method " + target.name + "." + selector);
          if (head) calls++;
          else if (returning) {
            for (var instruction : selected.getFirst().instructions)
              if (instruction.getOpcode() == Opcodes.RETURN) calls++;
          } else
            for (var instruction : selected.getFirst().instructions)
              if (instruction instanceof MethodInsnNode
                  && callSelector != null
                  && callSelector.match(ElementNode.of(instruction)).isExactMatch()) calls++;
        }
        Object rawRequired = optionalValue(injection, "require");
        int required = rawRequired instanceof Integer count ? count : 1;
        Object rawOrdinal = optionalValue(at, "ordinal");
        if (rawOrdinal instanceof Integer ordinal) {
          if (ordinal < 0 || ordinal >= calls)
            throw new AssertionError("Missing exact construction call ordinal " + ordinal);
          calls = 1;
        }
        if (calls < required || required < 1)
          throw new AssertionError(
              "Native injection has "
                  + calls
                  + "/"
                  + required
                  + " candidates: "
                  + mixin.name
                  + "."
                  + handler.name);
        sites++;
      }
    }
    if (sites == 0) throw new AssertionError("No construction injections inspected: " + mixin.name);
    return sites;
  }

  private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
    if (annotations != null)
      for (var annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
    throw new AssertionError("Missing annotation " + descriptor);
  }

  private static Object value(AnnotationNode annotation, String name) {
    Object result = optionalValue(annotation, name);
    if (result == null) throw new AssertionError("Missing annotation value " + name);
    return result;
  }

  private static Object optionalValue(AnnotationNode annotation, String name) {
    if (annotation.values != null)
      for (int index = 0; index < annotation.values.size(); index += 2)
        if (annotation.values.get(index).equals(name)) return annotation.values.get(index + 1);
    return null;
  }

  private static List<String> strings(Object input) {
    if (!(input instanceof List<?> values))
      throw new AssertionError("Expected annotation string list");
    List<String> result = new ArrayList<>();
    for (Object value : values) {
      if (!(value instanceof String text)) throw new AssertionError("Expected annotation string");
      result.add(text);
    }
    return result;
  }

  private static ClassNode read(String name) throws IOException {
    try (var stream =
        ConstructionMixinContract.class.getClassLoader().getResourceAsStream(name + ".class")) {
      if (stream == null)
        throw new IOException("Missing exact provider or integration class " + name);
      ClassNode result = new ClassNode();
      new ClassReader(stream).accept(result, 0);
      return result;
    }
  }
}
