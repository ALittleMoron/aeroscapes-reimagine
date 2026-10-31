package dev.aeroscapes.atmosphere;

import com.anthonyhilyard.iceberg.neoforge.config.NeoForgeIcebergConfigSpec;
import com.anthonyhilyard.iceberg.util.Selectors;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.ListValueSpec;

/** Exercises native editing against a real Iceberg spec without opening a client. */
public final class IcebergConfigBridgeTest {
  private IcebergConfigBridgeTest() {}

  public static void verify() {
    NeoForgeIcebergConfigSpec.Builder builder = new NeoForgeIcebergConfigSpec.Builder();
    var fixture =
        builder.finish(
            options -> {
              options.comment("Fixture category").translation("fixture.category").push("client");
              Supplier<Boolean> enabled = options.add("enabled", false);
              Supplier<Integer> count = options.addInRange("count", 3, 1, 10);
              Supplier<Long> color = options.addInRange("color", 4294967295L, 0L, 4294967295L);
              Supplier<Double> speed = options.addInRange("speed", 1.5, 0.0, 50.0);
              Supplier<String> badge = options.add("badge", "Equipped");
              Supplier<Mode> mode = options.addEnum("mode", Mode.NEVER);
              Supplier<List<? extends String>> selectors =
                  options.addListAllowEmpty(
                      "selectors",
                      List.of("!rare"),
                      element ->
                          element instanceof String text && Selectors.validateSelector(text));
              options.addList("colors", List.of("auto"), "auto"::equals);
              options.addList(
                  "priorities",
                  List.of(0),
                  element -> element instanceof Integer number && number >= -1 && number < 16);
              Supplier<Map<String, Object>> definitions =
                  options.addSubconfig(
                      "definitions",
                      Map.of("first", "original"),
                      key -> key instanceof String,
                      value -> value instanceof String);
              options.pop();
              return new Values(enabled, count, color, speed, badge, mode, selectors, definitions);
            });
    require(fixture.getRight() instanceof NeoForgeIcebergConfigSpec, "real Iceberg spec built");
    NeoForgeIcebergConfigSpec original = (NeoForgeIcebergConfigSpec) fixture.getRight();
    Values values = fixture.getLeft();
    MemoryConfig loaded = new MemoryConfig();
    original.correct(loaded.config());
    original.acceptConfig(loaded.nativeConfig);
    loaded.config().set("client.mode", "ALWAYS");
    loaded.config().set("client.speed", 2);
    loaded.config().set("client.color", 4294967295L);
    Config table = loaded.config().get("client.definitions");
    table.set("second", "user selector");
    loaded.config().set("retained_extra", "unchanged");
    loaded.config().setComment("retained_extra", "user comment");
    List<String> keys = keys(loaded.config());
    List<String> tableKeys = keys(table);
    int savedBeforeOpen = loaded.saves;
    ConfigValue<?> originalEnabled = original.getValues().get("client.enabled");
    require(!values.enabled().get(), "original supplier cache primed");
    require(values.selectors().get().equals(List.of("!rare")), "original list cache primed");

    ModConfigSpec view = IcebergConfigView.create(original);
    sectionFilters(view);
    require(
        original.loadedConfig() == loaded.nativeConfig, "original loaded config identity retained");
    require(
        loadedBinding(view) == loaded.nativeConfig,
        "native editor uses original loaded config object");
    require(view.isLoaded(), "display view loaded");
    knownListSeeds(original, view);
    require(loaded.saves == savedBeforeOpen, "opening editor neither saves nor revalidates");
    require(
        original.getValues().get("client.enabled") == originalEnabled,
        "original config value identity retained");
    compareDefinitions(original.getValues(), original.getSpec(), view, List.of());
    require(
        "Fixture category".equals(view.getLevelComment(List.of("client"))),
        "category comment preserved");
    require(
        "fixture.category".equals(view.getLevelTranslationKey(List.of("client"))),
        "category key preserved");

    ConfigValue<Boolean> enabled = view.getValues().get("client.enabled");
    ConfigValue<Integer> count = view.getValues().get("client.count");
    ConfigValue<Long> color = view.getValues().get("client.color");
    ConfigValue<Double> speed = view.getValues().get("client.speed");
    ConfigValue<String> badge = view.getValues().get("client.badge");
    ConfigValue<Mode> mode = view.getValues().get("client.mode");
    ConfigValue<List<String>> selectors = view.getValues().get("client.selectors");
    ConfigValue<?> definitions = view.getValues().get("client.definitions");
    require(color.getRaw() == 4294967295L, "unsigned color survives native long conversion");
    require(speed.getRaw() == 2.0, "native numeric conversion retained");
    require(mode.getRaw() == Mode.ALWAYS, "native enum parses persisted text");
    require(
        !IcebergConfigView.editable(definitions),
        "custom table remains read-only, including reset");
    require(IcebergConfigView.editable(enabled), "native primitive stays editable");
    require(IcebergConfigView.editable(selectors), "native list stays editable");
    require(!count.getSpec().test(100), "original range validator retained");
    require(selectors.getSpec().test(List.of("@example")), "original list validator retained");

    enabled.set(true);
    count.set(7);
    color.set(3500839187L);
    speed.set(3.25);
    badge.set("Player badge");
    mode.set(Mode.NEVER);
    require(!values.enabled().get(), "editing does not rebind or bypass original cached supplier");
    loaded.onSave =
        () -> require(values.enabled().get(), "cache invalidated before reload notification");
    IcebergConfigView.finishEditing(
        original,
        () -> {
          // Native list onClose commits its pending copy before native section onClose saves.
          selectors.set(new ArrayList<>(List.of("@example", "!rare")));
          require(
              values.selectors().get().equals(List.of("@example", "!rare")),
              "deferred list commit sees invalidated original cache");
          loaded.recordSave();
        });
    require(
        loaded.saves == savedBeforeOpen + 1,
        "close callback invoked once after cache invalidation");
    require(values.enabled().get(), "original runtime sees saved boolean");
    require(values.count().get() == 7, "original runtime sees saved integer");
    require(values.color().get() == 3500839187L, "original runtime sees saved color");
    require(values.speed().get() == 3.25, "original runtime sees saved double");
    require(values.badge().get().equals("Player badge"), "literal player string preserved");
    require(values.mode().get() == Mode.NEVER, "original runtime sees saved enum");
    require(
        values.selectors().get().equals(List.of("@example", "!rare")),
        "list content and order preserved");
    require(
        values.definitions().get().get("second").equals("user selector"),
        "custom parser/runtime value retained");
    require(loaded.config().get("client.definitions") == table, "custom table identity retained");
    require(tableKeys.equals(keys(table)), "custom table key order preserved");
    require(keys.equals(keys(loaded.config())), "file key order preserved");
    require(
        "unchanged".equals(loaded.config().get("retained_extra")),
        "unrelated table values preserved");
    require(
        "user comment".equals(loaded.config().getComment("retained_extra")),
        "user comments preserved");

    loaded.onSave = () -> {};
    enabled.set(Boolean.class.cast(enabled.getSpec().correct(null)));
    count.set(Integer.class.cast(count.getSpec().correct(null)));
    IcebergConfigView.finishEditing(original, loaded::recordSave);
    require(
        !values.enabled().get() && values.count().get() == 3,
        "native reset uses original defaults");
    require(values.badge().get().equals("Player badge"), "reset leaves other values alone");
    require(
        loaded.config().get("client.definitions") == table, "reset preserves opaque custom tables");
    ModConfigSpec reopened = IcebergConfigView.create(original);
    ConfigValue<String> reopenedBadge = reopened.getValues().get("client.badge");
    require(reopenedBadge.getRaw().equals("Player badge"), "reopening preserves player choices");
  }

  private static void sectionFilters(ModConfigSpec view) {
    var root = IcebergConfigScreens.rootContext("fixture", null, null, view);
    var element =
        new ConfigurationSectionScreen.Element(
            Component.literal("Enabled"),
            Component.literal("Fixture help"),
            OptionInstance.createBoolean("fixture.enabled", false),
            false);
    require(root.filter() != null, "root editor must supply the native entry filter");
    require(
        root.filter().filterEntry(root, "enabled", element) == element,
        "root entry filter preserves editable controls");
    var client =
        ConfigurationSectionScreen.Context.section(
            root, null, root.entries(), root.valueSpecs(), "client");
    var appearance =
        ConfigurationSectionScreen.Context.section(
            client, null, client.entries(), client.valueSpecs(), "visual_options");
    require(
        appearance.filter().filterEntry(appearance, "enabled", element) == element,
        "nested Appearance section inherits a usable filter");
  }

  private static void knownListSeeds(NeoForgeIcebergConfigSpec original, ModConfigSpec view) {
    ListValueSpec selectors = original.getSpec().get("client.selectors");
    ListValueSpec colors = original.getSpec().get("client.colors");
    ListValueSpec priorities = original.getSpec().get("client.priorities");
    require(selectors.getNewElementSupplier() == null, "provider's missing list seed reproduced");
    require(
        "!rare"
            .equals(
                IcebergConfigView.listSeed(
                    "equipmentcompare",
                    List.of("client", "control_options", "blacklist"),
                    selectors)),
        "equipment blacklist gets a provider-valid selector seed");
    for (int level = 0; level < 16; level++) {
      require(
          "!rare"
              .equals(
                  IcebergConfigView.listSeed(
                      "legendarytooltips",
                      List.of("client", "definitions", "level" + level + "_entries"),
                      selectors)),
          "selector seed for each supported frame level");
      require(
          "auto"
              .equals(
                  IcebergConfigView.listSeed(
                      "legendarytooltips",
                      List.of("client", "colors", "level" + level + "_colors"),
                      colors)),
          "color seed for each supported frame level");
    }
    require(
        Integer.valueOf(0)
            .equals(
                IcebergConfigView.listSeed(
                    "legendarytooltips",
                    List.of("client", "priorities", "priorities"),
                    priorities)),
        "priority seed stays an integer");
    require(
        IcebergConfigView.listSeed(
                "legendarytooltips", List.of("client", "priorities", "priorities"), colors)
            == null,
        "provider validation rejects an incompatible seed");
    require(
        IcebergConfigView.listSeed(
                "other", List.of("client", "control_options", "blacklist"), selectors)
            == null,
        "no speculative editing outside pinned consumers");
    require(
        IcebergConfigView.listSeed(
                "legendarytooltips", List.of("client", "definitions", "level16_entries"), selectors)
            == null,
        "no speculative frame levels");
    require(selectors.getNewElementSupplier() == null, "original list metadata never modified");
    require(
        view.getSpec().get("client.selectors") == selectors,
        "display keeps original validator metadata");
  }

  private static void compareDefinitions(
      UnmodifiableConfig values,
      UnmodifiableConfig schema,
      ModConfigSpec view,
      List<String> parent) {
    for (UnmodifiableConfig.Entry entry : values.entrySet()) {
      List<String> path = new ArrayList<>(parent);
      path.add(entry.getKey());
      if (entry.getRawValue() instanceof UnmodifiableConfig section) {
        compareDefinitions(section, schema.get(entry.getKey()), view, path);
      } else if (entry.getRawValue() instanceof ConfigValue<?> originalValue) {
        ConfigValue<?> display = view.getValues().get(path);
        require(display != originalValue, "editor must clone values: " + path);
        require(
            display.getClass() == originalValue.getClass(),
            "native value subtype preserved: " + path);
        require(
            display.getPath().equals(originalValue.getPath()), "config path preserved: " + path);
        require(
            display.getSpec() == schema.get(entry.getKey()),
            "schema record identity preserved: " + path);
        require(
            display.getDefault().equals(originalValue.getDefault()), "default preserved: " + path);
        require(binding(display) == view, "only display value bound to native view: " + path);
      }
    }
  }

  private static Object binding(ConfigValue<?> value) {
    try {
      Field binding = ConfigValue.class.getDeclaredField("spec");
      binding.setAccessible(true);
      return binding.get(value);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Native value binding must remain inspectable", failure);
    }
  }

  private static Object loadedBinding(ModConfigSpec view) {
    try {
      Field loaded = ModConfigSpec.class.getDeclaredField("loadedConfig");
      loaded.setAccessible(true);
      return loaded.get(view);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Native loaded config must remain inspectable", failure);
    }
  }

  private static List<String> keys(UnmodifiableConfig config) {
    List<String> result = new ArrayList<>();
    for (UnmodifiableConfig.Entry entry : config.entrySet()) result.add(entry.getKey());
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private enum Mode {
    NEVER,
    ALWAYS
  }

  private record Values(
      Supplier<Boolean> enabled,
      Supplier<Integer> count,
      Supplier<Long> color,
      Supplier<Double> speed,
      Supplier<String> badge,
      Supplier<Mode> mode,
      Supplier<List<? extends String>> selectors,
      Supplier<Map<String, Object>> definitions) {}

  private static final class MemoryConfig {
    private final CommentedConfig data =
        CommentedConfig.of(
            LinkedHashMap::new, com.electronwill.nightconfig.toml.TomlFormat.instance());
    private final IConfigSpec.ILoadedConfig nativeConfig = nativeLoaded(data);
    private int saves;
    private Runnable onSave = () -> {};

    private CommentedConfig config() {
      return data;
    }

    private void recordSave() {
      saves++;
      onSave.run();
    }
  }

  private static IConfigSpec.ILoadedConfig nativeLoaded(CommentedConfig data) {
    try {
      Constructor<?> constructor =
          Class.forName("net.neoforged.fml.config.LoadedConfig")
              .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
      constructor.setAccessible(true);
      Object loaded = constructor.newInstance(data, null, null);
      if (loaded instanceof IConfigSpec.ILoadedConfig nativeConfig) return nativeConfig;
      throw new AssertionError("FML native loaded config type changed");
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Native loaded config fixture cannot be created", failure);
    }
  }
}
