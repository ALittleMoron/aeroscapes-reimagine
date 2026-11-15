package dev.aeroscapes.atmosphere;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * One aurora per sky. Complementary draws its own aurora, so the separate aurora renderer stays
 * hidden under that pack; without shaders and under MakeUp, which has no aurora, it draws. Iris is
 * optional: its public API reports whether a pack is in use, and the pinned Iris 1.8.14 names the
 * loaded pack through {@code Iris.getCurrentPackName()}.
 */
public final class AuroraOwnership {
  private static final @Nullable MethodHandle INSTANCE =
      handle("net.irisshaders.iris.api.v0.IrisApi", "getInstance", true);
  private static final @Nullable MethodHandle IN_USE =
      handle("net.irisshaders.iris.api.v0.IrisApi", "isShaderPackInUse", false);
  private static final @Nullable MethodHandle PACK_NAME =
      handle("net.irisshaders.iris.Iris", "getCurrentPackName", true);

  private AuroraOwnership() {}

  public static boolean shaderPackDrawsAurora() {
    if (INSTANCE == null || IN_USE == null || PACK_NAME == null) return false;
    try {
      if (!(boolean) IN_USE.invoke(INSTANCE.invoke())) return false;
      return drawsOwnAurora((String) PACK_NAME.invoke());
    } catch (Throwable failure) {
      return false;
    }
  }

  /** Shader packs in this pack that render an aurora of their own. */
  public static boolean drawsOwnAurora(@Nullable String packName) {
    return packName != null && packName.toLowerCase(Locale.ROOT).contains("complementary");
  }

  private static @Nullable MethodHandle handle(String owner, String name, boolean isStatic) {
    try {
      var type = Class.forName(owner);
      var lookup = MethodHandles.publicLookup();
      if (!isStatic) return lookup.findVirtual(type, name, MethodType.methodType(boolean.class));
      return "getInstance".equals(name)
          ? lookup.findStatic(type, name, MethodType.methodType(type))
          : lookup.findStatic(type, name, MethodType.methodType(String.class));
    } catch (ReflectiveOperationException | LinkageError absent) {
      return null;
    }
  }
}
