package dev.aeroscapes.camera;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ShoulderCameraMixinPlugin implements IMixinConfigPlugin {
  private static final String CAMERA = "net.minecraft.client.Camera";

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    var shoulder = mods.getModFileById("shouldersurfing");
    if (shoulder == null) return false;
    requireVersion(shoulder.getMods(), "shouldersurfing", "1.21.1-4.22.12");
    if (!CAMERA.equals(targetClassName)) return true;
    var sable = mods.getModFileById("sable");
    if (sable == null) return false;
    requireVersion(sable.getMods(), "sable", "2.0.6");
    return true;
  }

  private static void requireVersion(List<IModInfo> mods, String id, String expected) {
    String actual =
        mods.stream()
            .filter(mod -> id.equals(mod.getModId()))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing mod metadata for " + id));
    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          "Unsupported " + id + " version " + actual + "; expected " + expected);
    }
  }

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {
    if (CAMERA.equals(targetClassName)) ShoulderCameraTransformer.patch(targetClass);
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
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {}
}
