package dev.aeroscapes.atmosphere;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jetbrains.annotations.Nullable;

/** Optional Iris bridge for the name of the shader pack actually in use. */
public final class ActiveShaderPack {
  private static final @Nullable MethodHandle INSTANCE =
      handle("net.irisshaders.iris.api.v0.IrisApi", "getInstance", true);
  private static final @Nullable MethodHandle IN_USE =
      handle("net.irisshaders.iris.api.v0.IrisApi", "isShaderPackInUse", false);
  private static final @Nullable MethodHandle PACK_NAME =
      handle("net.irisshaders.iris.Iris", "getCurrentPackName", true);

  private ActiveShaderPack() {}

  public static @Nullable String name() {
    if (INSTANCE == null || IN_USE == null || PACK_NAME == null) return null;
    try {
      return (boolean) IN_USE.invoke(INSTANCE.invoke()) ? (String) PACK_NAME.invoke() : null;
    } catch (Throwable failure) {
      return null;
    }
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
