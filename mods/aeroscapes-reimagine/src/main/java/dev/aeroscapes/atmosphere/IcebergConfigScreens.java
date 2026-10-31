package dev.aeroscapes.atmosphere;

import com.anthonyhilyard.iceberg.neoforge.config.NeoForgeIcebergConfigSpec;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationListScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.ListValueSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Range;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** Reuses the native editor for the two pinned Iceberg consumers. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class IcebergConfigScreens {
  private static final String NATIVE_UI = "neoforge.configuration.uitext.";

  private IcebergConfigScreens() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          install("equipmentcompare", "1.3.13");
          install("legendarytooltips", "1.5.5");
        });
  }

  private static void install(String modId, String version) {
    ModList.get()
        .getModContainerById(modId)
        .ifPresent(
            container -> {
              if (container.getCustomExtension(IConfigScreenFactory.class).isPresent()) return;
              for (var expected :
                  Map.of("neoforge", "21.1.230", "iceberg", "1.3.2", modId, version).entrySet()) {
                String actual =
                    ModList.get()
                        .getModContainerById(expected.getKey())
                        .orElseThrow()
                        .getModInfo()
                        .getVersion()
                        .toString();
                if (!expected.getValue().equals(actual)) {
                  throw new IllegalStateException(
                      "Review the Iceberg editor bridge for " + expected.getKey() + " " + actual);
                }
              }
              container.registerExtensionPoint(
                  IConfigScreenFactory.class, (ignored, parent) -> open(container, parent));
            });
  }

  private static Screen open(ModContainer container, Screen parent) {
    Component title =
        Component.translatableWithFallback(
            "aeroscapes.qol.config." + container.getModId() + ".title",
            container.getModInfo().getDisplayName());
    List<ModConfig> configs = ModConfigs.getModConfigs(container.getModId());
    if (configs.size() != 1) {
      throw new IllegalStateException("Review Iceberg config files for " + container.getModId());
    }
    ModConfig config = configs.getFirst();
    if (!(config.getSpec() instanceof NeoForgeIcebergConfigSpec original)) {
      throw new IllegalStateException("Review Iceberg config spec for " + container.getModId());
    }
    if (!original.isLoaded()) {
      return new AlertScreen(
          () -> Minecraft.getInstance().setScreen(parent),
          title,
          ConfigurationScreen.TOOLTIP_CANNOT_EDIT_NOT_LOADED);
    }
    ModConfigSpec view = IcebergConfigView.create(original);
    return new Section(rootContext(container.getModId(), parent, config, view), title);
  }

  static ConfigurationSectionScreen.Context rootContext(
      String modId, Screen parent, ModConfig config, ModConfigSpec view) {
    return new ConfigurationSectionScreen.Context(
        modId,
        parent,
        config,
        view,
        view.getValues().entrySet(),
        schemaMap(view.getSpec()),
        List.of(),
        (context, key, element) -> element);
  }

  private static Map<String, Object> schemaMap(UnmodifiableConfig schema) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (UnmodifiableConfig.Entry entry : schema.entrySet()) {
      values.put(entry.getKey(), entry.getRawValue());
    }
    return Collections.unmodifiableMap(values);
  }

  private static String authoredKey(ConfigurationSectionScreen.Context context, String key) {
    List<String> path = new ArrayList<>(context.keylist());
    path.add(key);
    return "aeroscapes.qol.config." + context.modId() + "." + String.join(".", path);
  }

  private static String labelKey(
      ConfigurationSectionScreen.Context context, String key, String nativeKey) {
    return I18n.exists(nativeKey) ? nativeKey : authoredKey(context, key);
  }

  private static Component tooltip(
      ConfigurationSectionScreen.Context context,
      String key,
      String nativeKey,
      String comment,
      MutableComponent label,
      Range<?> range) {
    String authored = authoredKey(context, key) + ".tooltip";
    String nativeTooltip = nativeKey + ".tooltip";
    String selected = I18n.exists(nativeTooltip) ? nativeTooltip : authored;
    MutableComponent text = Component.empty().append(label.withStyle(ChatFormatting.BOLD));
    if (I18n.exists(selected) || comment != null && !comment.isBlank()) {
      text.append("\n\n").append(Component.translatableWithFallback(selected, comment));
    }
    String warning =
        I18n.exists(nativeTooltip + ".warning")
            ? nativeTooltip + ".warning"
            : authored + ".warning";
    if (I18n.exists(warning)) {
      text.append("\n\n")
          .append(
              Component.translatable(warning).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }
    if (range != null && I18n.exists(selected)) {
      text.append("\n\n")
          .append(
              Component.translatable(NATIVE_UI + "rangetooltip", range.toString())
                  .withStyle(ChatFormatting.GRAY));
    }
    return text;
  }

  private static final class Section extends ConfigurationSectionScreen {
    private Section(Context context, Component title) {
      super(context, title);
    }

    @Override
    public void onClose() {
      if (context.modConfig().getSpec() instanceof NeoForgeIcebergConfigSpec original) {
        IcebergConfigView.finishEditing(original, super::onClose);
      } else {
        super.onClose();
      }
    }

    @Override
    protected String getTranslationKey(String key) {
      return labelKey(context, key, super.getTranslationKey(key));
    }

    @Override
    protected Component getTooltipComponent(String key, Range<?> range) {
      return tooltip(
          context,
          key,
          super.getTranslationKey(key),
          getComment(key),
          getTranslationComponent(key),
          range);
    }

    @Override
    protected boolean isNonDefault(ConfigValue<?> value) {
      return IcebergConfigView.editable(value) && super.isNonDefault(value);
    }

    @Override
    protected Element createOtherValue(String key, ConfigValue<?> value) {
      Component message = Component.translatable("aeroscapes.qol.config.edit_file");
      StringWidget label =
          new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, message, font).alignLeft();
      label.setTooltip(Tooltip.create(message));
      return new Element(
          getTranslationComponent(key), getTooltipComponent(key, null), label, false);
    }

    @Override
    protected Element createSection(
        String key, UnmodifiableConfig schema, UnmodifiableConfig values) {
      if (schema.isEmpty()) return null;
      return new Element(
          Component.translatable(NATIVE_UI + "section", getTranslationComponent(key)),
          getTooltipComponent(key, null),
          Button.builder(
                  Component.translatable(
                      NATIVE_UI + "section", Component.translatable(NATIVE_UI + "sectiontext")),
                  button ->
                      minecraft.setScreen(
                          sectionCache.computeIfAbsent(
                              key,
                              ignored ->
                                  new Section(
                                      Context.section(
                                          context, this, values.entrySet(), schemaMap(schema), key),
                                      Component.translatable(
                                          NATIVE_UI + "breadcrumb.order",
                                          getTitle(),
                                          ConfigurationScreen.CRUMB_SEPARATOR,
                                          getTranslationComponent(key))))))
              .tooltip(Tooltip.create(getTooltipComponent(key, null)))
              .width(Button.DEFAULT_WIDTH)
              .build(),
          false);
    }

    @Override
    protected <T> Element createList(String key, ListValueSpec spec, ConfigValue<List<T>> values) {
      return new Element(
          Component.translatable(NATIVE_UI + "section", getTranslationComponent(key)),
          getTooltipComponent(key, null),
          Button.builder(
                  Component.translatable(
                      NATIVE_UI + "section", Component.translatable(NATIVE_UI + "sectiontext")),
                  button ->
                      minecraft.setScreen(
                          sectionCache.computeIfAbsent(
                              key,
                              ignored ->
                                  listScreen(
                                      Context.list(context, this),
                                      key,
                                      Component.translatable(
                                          NATIVE_UI + "breadcrumb.order",
                                          getTitle(),
                                          ConfigurationScreen.CRUMB_SEPARATOR,
                                          getTranslationComponent(key)),
                                      spec,
                                      values))))
              .tooltip(Tooltip.create(getTooltipComponent(key, null)))
              .build(),
          false);
    }

    private <T> ConfigurationSectionScreen listScreen(
        Context listContext,
        String key,
        Component title,
        ListValueSpec spec,
        ConfigValue<List<T>> values) {
      List<String> path = values.getPath();
      Object seed = IcebergConfigView.listSeed(context.modId(), path, spec);
      if (seed instanceof String text
          && values.getRaw().stream().allMatch(String.class::isInstance)) {
        ConfigValue<List<String>> strings = context.modSpec().getValues().get(path);
        return new LocalizedList<>(listContext, key, title, spec, strings, () -> text);
      }
      if (seed instanceof Integer number
          && values.getRaw().stream().allMatch(Integer.class::isInstance)) {
        ConfigValue<List<Integer>> integers = context.modSpec().getValues().get(path);
        return new LocalizedList<>(listContext, key, title, spec, integers, () -> number);
      }
      return new LocalizedList<>(listContext, key, title, spec, values, null);
    }

    @Override
    protected <T extends Enum<T>> Element createEnumValue(
        String key, ValueSpec spec, Supplier<T> source, Consumer<T> target) {
      List<T> choices =
          Arrays.stream(source.get().getDeclaringClass().getEnumConstants())
              .filter(spec::test)
              .toList();
      return new Element(
          getTranslationComponent(key),
          getTooltipComponent(key, null),
          new OptionInstance<>(
              getTranslationKey(key),
              getTooltip(key, null),
              (caption, value) -> {
                if (value instanceof TranslatableEnum translated)
                  return translated.getTranslatedName();
                String authored = authoredKey(context, key) + ".value." + value.name();
                if (Language.getInstance().has(authored)) {
                  return Component.translatable(authored);
                }
                String literal = ConfigLiteralTranslations.key(value.name());
                if (Language.getInstance().has(literal)) {
                  return Component.literal(Language.getInstance().getOrDefault(literal));
                }
                return Component.literal(value.name());
              },
              new Custom<>(choices),
              source.get(),
              selected ->
                  undoManager.add(
                      value -> {
                        target.accept(value);
                        onChanged(key);
                      },
                      selected,
                      value -> {
                        target.accept(value);
                        onChanged(key);
                      },
                      source.get())));
    }
  }

  private static final class LocalizedList<T> extends ConfigurationListScreen<T> {
    private final Supplier<T> seed;

    private LocalizedList(
        Context context,
        String key,
        Component title,
        ListValueSpec spec,
        ConfigValue<List<T>> values,
        Supplier<T> seed) {
      super(context, key, title, spec, values);
      this.seed = seed;
    }

    @Override
    public void onClose() {
      if (context.modConfig().getSpec() instanceof NeoForgeIcebergConfigSpec original) {
        IcebergConfigView.finishEditing(original, super::onClose);
      } else {
        super.onClose();
      }
    }

    @Override
    protected String getTranslationKey(String key) {
      return labelKey(context, key, super.getTranslationKey(key));
    }

    @Override
    protected Component getTooltipComponent(String key, Range<?> range) {
      return tooltip(
          context,
          key,
          super.getTranslationKey(key),
          getComment(key),
          getTranslationComponent(key),
          range);
    }

    @Override
    protected void createAddElementButton() {
      if (spec.getNewElementSupplier() != null || seed == null) {
        super.createAddElementButton();
        return;
      }
      if (spec.getSizeRange().test(cfgList.size() + 1)) {
        list.addSmall(
            new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.empty(), font),
            Button.builder(
                    ConfigurationScreen.NEW_LIST_ELEMENT,
                    button -> {
                      List<T> changed = new ArrayList<>(cfgList);
                      changed.add(seed.get());
                      undoManager.add(
                          value -> {
                            cfgList = value;
                            onChanged(key);
                          },
                          changed,
                          value -> {
                            cfgList = value;
                            onChanged(key);
                          },
                          cfgList);
                      rebuild();
                    })
                .build());
      }
    }
  }
}
