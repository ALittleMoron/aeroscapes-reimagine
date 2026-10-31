package dev.aeroscapes.atmosphere;

import com.anthonyhilyard.iceberg.neoforge.config.NeoForgeIcebergConfigSpec;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.EnumGetMethod;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

/** A native editor view which leaves Iceberg's registered spec and cached suppliers in place. */
public final class IcebergConfigView {
  private static final Set<Class<?>> VALUE_TYPES =
      Set.of(
          ConfigValue.class,
          ModConfigSpec.BooleanValue.class,
          ModConfigSpec.IntValue.class,
          ModConfigSpec.LongValue.class,
          ModConfigSpec.DoubleValue.class,
          ModConfigSpec.EnumValue.class);

  private IcebergConfigView() {}

  public static ModConfigSpec create(NeoForgeIcebergConfigSpec original) {
    Objects.requireNonNull(original.loadedConfig(), "Iceberg configuration is not loaded");
    try {
      ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
      Object schema = field(ModConfigSpec.Builder.class, "spec").get(builder);
      if (!(schema instanceof Config nativeSchema)) {
        throw new IllegalStateException("Native configuration builder has no schema");
      }
      nativeSchema.putAll(original.getSpec());
      copyValues(original, original.getValues(), builder, List.of());
      ModConfigSpec view = builder.build();
      // acceptConfig would run the native parser over Iceberg-specific values and tables.
      field(ModConfigSpec.class, "loadedConfig").set(view, original.loadedConfig());
      return view;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(
          "Review the NeoForge 21.1.230 / Iceberg 1.3.2 editor bridge", failure);
    }
  }

  private static void copyValues(
      NeoForgeIcebergConfigSpec original,
      UnmodifiableConfig values,
      ModConfigSpec.Builder builder,
      List<String> parent)
      throws ReflectiveOperationException {
    for (UnmodifiableConfig.Entry entry : values.entrySet()) {
      List<String> path = new ArrayList<>(parent);
      path.add(entry.getKey());
      Object value = entry.getRawValue();
      if (value instanceof UnmodifiableConfig section) {
        String comment = original.getLevelComment(path);
        String translation = original.getLevelTranslationKey(path);
        if (comment != null) builder.comment(comment);
        if (translation != null) builder.translation(translation);
        builder.push(List.of(entry.getKey()));
        copyValues(original, section, builder, path);
        builder.pop();
      } else if (value instanceof ConfigValue<?> configValue) {
        cloneValue(configValue, builder);
      } else {
        throw new IllegalStateException("Unsupported Iceberg configuration entry: " + path);
      }
    }
  }

  private static void cloneValue(ConfigValue<?> original, ModConfigSpec.Builder builder)
      throws ReflectiveOperationException {
    Class<?> type = original.getClass();
    if (!VALUE_TYPES.contains(type)) {
      throw new IllegalStateException("Unsupported Iceberg configuration value: " + type.getName());
    }
    Supplier<?> defaults = original::getDefault;
    if (original instanceof ModConfigSpec.EnumValue<?>) {
      Constructor<?> constructor =
          type.getDeclaredConstructor(
              ModConfigSpec.Builder.class,
              List.class,
              Supplier.class,
              EnumGetMethod.class,
              Class.class);
      constructor.setAccessible(true);
      constructor.newInstance(
          builder,
          original.getPath(),
          defaults,
          field(type, "converter").get(original),
          field(type, "clazz").get(original));
    } else {
      Constructor<?> constructor =
          type.getDeclaredConstructor(ModConfigSpec.Builder.class, List.class, Supplier.class);
      constructor.setAccessible(true);
      constructor.newInstance(builder, original.getPath(), defaults);
    }
  }

  private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  public static boolean editable(ConfigValue<?> value) {
    Object defaults = value.getDefault();
    return defaults instanceof Boolean
        || defaults instanceof String
        || defaults instanceof Integer
        || defaults instanceof Long
        || defaults instanceof Double
        || defaults instanceof Enum<?>
        || defaults instanceof List<?>;
  }

  public static Object listSeed(String modId, List<String> path, ModConfigSpec.ListValueSpec spec) {
    String joined = String.join(".", path);
    Object seed = null;
    if (modId.equals("equipmentcompare") && joined.equals("client.control_options.blacklist")) {
      seed = "!rare";
    } else if (modId.equals("legendarytooltips")) {
      if (joined.equals("client.definitions.blacklist")
          || joined.matches("client\\.definitions\\.level(?:[0-9]|1[0-5])_entries")) {
        seed = "!rare";
      } else if (joined.matches("client\\.colors\\.level(?:[0-9]|1[0-5])_colors")) {
        seed = "auto";
      } else if (joined.equals("client.priorities.priorities")) {
        seed = 0;
      }
    }
    return seed != null && spec.testElement(seed) ? seed : null;
  }

  public static void finishEditing(NeoForgeIcebergConfigSpec original, Runnable closeScreen) {
    // Native LoadedConfig.save fires the original reload event, but does not clear its caches.
    original.afterReload();
    closeScreen.run();
  }
}
