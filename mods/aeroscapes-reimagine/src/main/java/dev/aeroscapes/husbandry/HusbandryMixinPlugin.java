package dev.aeroscapes.husbandry;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class HusbandryMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    return available("animalhusbandry", "0.6.0")
        && available("neoforge", "21.1.244")
        && (!mixin.endsWith("FowlGeneticAppearanceMixin") || available("fowlplay", "1.2.3"));
  }

  private static boolean available(String id, String expected) {
    var file = FMLLoader.getLoadingModList().getModFileById(id);
    if (file == null) return false;
    String actual =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals(id))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!expected.equals(actual))
      throw new IllegalStateException("Re-audit husbandry adapter: " + id + " " + actual);
    return true;
  }

  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  @Override
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    if (mixin.endsWith("HusbandryMergedLanguageMixin")
        && node.name.equals("net/minecraft/world/entity/LivingEntity"))
      HusbandryLootTransformer.patch(node);
    if (!mixin.endsWith("AnimalCareClockMixin") && !mixin.endsWith("HusbandryMergedLanguageMixin"))
      return;
    // All mixins for this target have completed their merge and injection passes.
    for (MethodNode method : node.methods) {
      if (!animalHusbandryHandler(method.visibleAnnotations)
          && !animalHusbandryHandler(method.invisibleAnnotations)) continue;
      for (var instruction : method.instructions) {
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals("net/minecraft/network/chat/Component")
            && call.name.equals("literal")
            && call.desc.equals(
                "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;")) {
          call.owner = "dev/aeroscapes/husbandry/HusbandryLanguage";
          call.itf = false;
        }
      }
    }
  }

  private static boolean animalHusbandryHandler(List<AnnotationNode> annotations) {
    if (annotations == null) return false;
    for (AnnotationNode annotation : annotations) {
      if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")
          || annotation.values == null) continue;
      for (int index = 0; index < annotation.values.size() - 1; index += 2) {
        if ("mixin".equals(annotation.values.get(index))
            && annotation.values.get(index + 1) instanceof String origin
            && origin.startsWith("org.athisworkshop.animalhusbandry.mixin.")) return true;
      }
    }
    return false;
  }
}
