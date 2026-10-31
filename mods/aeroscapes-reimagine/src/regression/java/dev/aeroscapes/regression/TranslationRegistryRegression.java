package dev.aeroscapes.regression;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Captures actual registered description IDs without loading client classes or starting a client.
 */
public final class TranslationRegistryRegression {
  private TranslationRegistryRegression() {}

  public static void verify(ServerLevel level, Path output) {
    verifySaplantingLanguages();
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("saplanting_languages", true);
    report.addProperty("seed", level.getSeed());
    report.add("items", descriptions(BuiltInRegistries.ITEM, item -> item.getDescriptionId()));
    JsonObject itemNames = new JsonObject();
    for (var id : BuiltInRegistries.ITEM.keySet().stream().sorted().toList()) {
      var item = BuiltInRegistries.ITEM.get(id);
      if (item == null) throw new IllegalStateException("Missing registered item " + id);
      var name = new ItemStack(item).getHoverName();
      JsonObject row = new JsonObject();
      row.addProperty("text", name.getString());
      Set<String> keys = new TreeSet<>();
      translationKeys(name, keys);
      JsonArray translations = new JsonArray();
      keys.forEach(translations::add);
      row.add("translation_keys", translations);
      itemNames.add(id.toString(), row);
    }
    report.add("item_names", itemNames);
    report.add("storage_wood_names", storageWoodNames());
    report.add("dynamic_config_definitions", dynamicConfigDefinitions());
    report.add("neoforge_config_definitions", neoForgeConfigDefinitions());
    report.add("blocks", descriptions(BuiltInRegistries.BLOCK, block -> block.getDescriptionId()));
    report.add(
        "entities",
        descriptions(BuiltInRegistries.ENTITY_TYPE, entity -> entity.getDescriptionId()));
    report.add(
        "effects", descriptions(BuiltInRegistries.MOB_EFFECT, effect -> effect.getDescriptionId()));

    JsonObject biomes = new JsonObject();
    var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
    biomeRegistry.keySet().stream()
        .sorted()
        .forEach(id -> biomes.addProperty(id.toString(), Util.makeDescriptionId("biome", id)));
    report.add("biomes", biomes);
    JsonArray generatorBiomes = new JsonArray();
    level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
        .map(holder -> holder.unwrapKey().orElseThrow().location().toString())
        .sorted()
        .forEach(generatorBiomes::add);
    report.add("generator_biomes", generatorBiomes);

    JsonObject tabs = new JsonObject();
    for (var id : BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream().sorted().toList()) {
      var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
      if (tab == null) throw new IllegalStateException("Missing registered creative tab " + id);
      JsonObject title = new JsonObject();
      title.addProperty("text", tab.getDisplayName().getString());
      if (tab.getDisplayName().getContents() instanceof TranslatableContents contents)
        title.addProperty("translation_key", contents.getKey());
      tabs.add(id.toString(), title);
    }
    report.add("creative_tabs", tabs);
    Path path = output.toAbsolutePath().normalize();
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException error) {
      throw new IllegalStateException("Cannot write translation registry audit " + path, error);
    }
    LogUtils.getLogger()
        .info(
            "Translation registry audit complete: items={} blocks={} entities={} effects={} biomes={} tabs={} output={}",
            BuiltInRegistries.ITEM.size(),
            BuiltInRegistries.BLOCK.size(),
            BuiltInRegistries.ENTITY_TYPE.size(),
            BuiltInRegistries.MOB_EFFECT.size(),
            biomeRegistry.size(),
            BuiltInRegistries.CREATIVE_MODE_TAB.size(),
            path);
  }

  private static void verifySaplantingLanguages() {
    try {
      Class<?> config = Class.forName("cool.muyucloud.saplanting.util.Config");
      Object languages =
          config.getMethod("getValidLangs").invoke(config.getConstructor().newInstance());
      if (!(languages instanceof Set<?> selectable) || !selectable.contains("ru_ru"))
        throw new IllegalStateException("Saplanting Russian language is not selectable");
      Class<?> translation = Class.forName("cool.muyucloud.saplanting.util.Translation");
      var update = translation.getMethod("updateLanguage", String.class);
      var translate = translation.getMethod("translate", String.class);
      try {
        for (var entry : Map.of("en_us", "Open File", "ru_ru", "Открыть файл").entrySet()) {
          update.invoke(null, entry.getKey());
          Object value = translate.invoke(null, "commands.saplanting.file.open");
          if (!entry.getValue().equals(value))
            throw new IllegalStateException(
                "Saplanting file tooltip in " + entry.getKey() + ": " + value);
          String missing = "aeroscapes_regression.missing_translation";
          if (!missing.equals(translate.invoke(null, missing)))
            throw new IllegalStateException("Saplanting changed unrelated missing-key fallback");
        }
      } finally {
        update.invoke(null, "en_us");
      }
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(
          "Cannot verify pinned Saplanting language integration", error);
    }
  }

  private static JsonObject storageWoodNames() {
    try {
      Class<?> helper =
          Class.forName("net.p3pp3rf1y.sophisticatedstorage.util.GenericWoodStorageHelper");
      var genericWood = helper.getMethod("isGenericWood", WoodType.class);
      var nativeWood = helper.getMethod("isCustomTexturedWood", WoodType.class);
      var woodName = helper.getMethod("getWoodDisplayName", WoodType.class);
      var storageName =
          Class.forName("net.p3pp3rf1y.sophisticatedstorage.item.WoodStorageBlockItem")
              .getMethod("getDisplayName", String.class, WoodType.class);
      JsonObject names = new JsonObject();
      for (var wood : WoodType.values().sorted(Comparator.comparing(WoodType::name)).toList()) {
        boolean generic = Boolean.TRUE.equals(genericWood.invoke(null, wood));
        boolean nativeTexture = Boolean.TRUE.equals(nativeWood.invoke(null, wood));
        if (!generic && !nativeTexture) continue;
        Object woodLabel = woodName.invoke(null, wood);
        Object storageLabel = storageName.invoke(null, "block.sophisticatedstorage.chest", wood);
        if (!(woodLabel instanceof Component woodComponent)
            || !(storageLabel instanceof Component storageComponent))
          throw new IllegalStateException("Invalid storage wood name for " + wood.name());
        JsonObject row = new JsonObject();
        row.addProperty("generic", generic);
        row.addProperty("native_texture", nativeTexture);
        row.addProperty("wood_text", woodComponent.getString());
        row.addProperty("storage_text", storageComponent.getString());
        Set<String> keys = new TreeSet<>();
        translationKeys(woodComponent, keys);
        translationKeys(storageComponent, keys);
        JsonArray translations = new JsonArray();
        keys.forEach(translations::add);
        row.add("translation_keys", translations);
        names.add(wood.name(), row);
      }
      if (names.size() == 0)
        throw new IllegalStateException("No native or generic storage wood names captured");
      return names;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Cannot capture pinned storage wood translations", error);
    }
  }

  private static JsonArray dynamicConfigDefinitions() {
    try {
      Class<?> zetaList = Class.forName("org.violetmoon.zeta.util.zetalist.ZetaList");
      Object instances =
          zetaList.getMethod("getZetas").invoke(zetaList.getField("INSTANCE").get(null));
      if (!(instances instanceof Iterable<?> owners))
        throw new IllegalStateException("Invalid Zeta owner list");
      Class<?> zeta = Class.forName("org.violetmoon.zeta.Zeta");
      Class<?> manager = Class.forName("org.violetmoon.zeta.config.ConfigManager");
      Class<?> section = Class.forName("org.violetmoon.zeta.config.SectionDefinition");
      JsonArray definitions = new JsonArray();
      for (Object owner : owners) {
        Object mod = zeta.getField("modid").get(owner);
        if (!(mod instanceof String modId)) throw new IllegalStateException("Invalid Zeta mod ID");
        Object config = zeta.getField("configManager").get(owner);
        if (config == null) continue;
        Object root = manager.getMethod("getRootConfig").invoke(config);
        Object children = section.getMethod("getAllChildren").invoke(root);
        if (!(children instanceof Iterable<?> entries))
          throw new IllegalStateException("Invalid Zeta root definitions for " + modId);
        for (Object entry : entries) captureConfigDefinition(modId, entry, definitions);
      }
      if (definitions.size() == 0)
        throw new IllegalStateException("No Zeta configuration definitions captured");
      return definitions;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(
          "Cannot capture pinned Zeta configuration translations", error);
    }
  }

  private static JsonArray neoForgeConfigDefinitions() {
    try {
      var field = ConfigTracker.class.getDeclaredField("fileMap");
      field.setAccessible(true);
      Object tracked = field.get(ConfigTracker.INSTANCE);
      if (!(tracked instanceof Map<?, ?> entries))
        throw new IllegalStateException("Invalid tracked NeoForge configurations");
      Map<String, ModConfig> configurations = new TreeMap<>();
      for (Object value : entries.values()) {
        if (!(value instanceof ModConfig configuration))
          throw new IllegalStateException("Invalid tracked NeoForge configuration");
        configurations.put(
            configuration.getModId()
                + "/"
                + configuration.getType()
                + "/"
                + configuration.getFileName(),
            configuration);
      }
      JsonArray definitions = new JsonArray();
      for (ModConfig configuration : configurations.values()) {
        if (configuration.getSpec() instanceof ModConfigSpec spec) {
          captureNeoForgeConfig(configuration, spec, spec.getSpec(), List.of(), definitions);
        }
      }
      if (definitions.isEmpty())
        throw new IllegalStateException("No NeoForge configuration definitions captured");
      return definitions;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(
          "Cannot capture pinned NeoForge configuration metadata", error);
    }
  }

  private static void captureNeoForgeConfig(
      ModConfig configuration,
      ModConfigSpec spec,
      UnmodifiableConfig section,
      List<String> parent,
      JsonArray definitions) {
    for (var entry : section.entrySet()) {
      List<String> path = new ArrayList<>(parent);
      path.add(entry.getKey());
      JsonObject row = new JsonObject();
      row.addProperty("mod", configuration.getModId());
      row.addProperty("type", configuration.getType().name());
      row.addProperty("filename", configuration.getFileName());
      row.addProperty("key", entry.getKey());
      JsonArray parts = new JsonArray();
      path.forEach(parts::add);
      row.add("path", parts);
      Object value = entry.getValue();
      if (value instanceof UnmodifiableConfig child) {
        row.addProperty("category", true);
        row.addProperty("translation_key", spec.getLevelTranslationKey(path));
        row.addProperty("comment", spec.getLevelComment(path));
        definitions.add(row);
        captureNeoForgeConfig(configuration, spec, child, path, definitions);
      } else if (value instanceof ModConfigSpec.ValueSpec option) {
        row.addProperty("category", false);
        row.addProperty("translation_key", option.getTranslationKey());
        row.addProperty("comment", option.getComment());
        Class<?> valueClass = option.getClazz();
        if (valueClass != null && Enum.class.isAssignableFrom(valueClass)) {
          Object defaultValue = option.getDefault();
          if (!(defaultValue instanceof Enum<?> constant))
            throw new IllegalStateException("Invalid enum configuration default: " + path);
          row.addProperty("enum_class", constant.getDeclaringClass().getName());
          JsonArray constants = new JsonArray();
          for (var candidate : constant.getDeclaringClass().getEnumConstants())
            constants.add(candidate.name());
          row.add("enum_constants", constants);
        }
        definitions.add(row);
      } else {
        throw new IllegalStateException("Invalid NeoForge configuration definition: " + path);
      }
    }
  }

  private static void captureConfigDefinition(
      String modId, Object definition, JsonArray definitions) throws ReflectiveOperationException {
    Class<?> base = Class.forName("org.violetmoon.zeta.config.Definition");
    JsonObject row = new JsonObject();
    row.addProperty("mod", modId);
    Object sourceName = base.getField("name").get(definition);
    if (!(sourceName instanceof String name))
      throw new IllegalStateException("Invalid Zeta configuration source name");
    row.addProperty("name", name);
    Function<String, String> nameLookup =
        key -> {
          row.addProperty("name_key", key);
          return key;
        };
    Object displayName =
        base.getMethod("getTranslatedDisplayName", Function.class).invoke(definition, nameLookup);
    if (!(displayName instanceof String englishName))
      throw new IllegalStateException("Invalid Zeta configuration display name: " + name);
    row.addProperty("english_name", englishName);
    Function<String, String> descriptionLookup =
        key -> {
          row.addProperty("description_key", key);
          return key;
        };
    Object comment =
        base.getMethod("getTranslatedComment", Function.class)
            .invoke(definition, descriptionLookup);
    JsonArray lines = new JsonArray();
    if (comment != null) {
      if (!(comment instanceof Iterable<?> comments))
        throw new IllegalStateException("Invalid Zeta configuration comment: " + name);
      for (Object line : comments) {
        if (!(line instanceof String text))
          throw new IllegalStateException("Invalid Zeta configuration comment line: " + name);
        lines.add(text);
      }
    }
    row.add("english_description", lines);
    definitions.add(row);
    Class<?> section = Class.forName("org.violetmoon.zeta.config.SectionDefinition");
    if (section.isInstance(definition)) {
      Object children = section.getMethod("getAllChildren").invoke(definition);
      if (!(children instanceof Iterable<?> entries))
        throw new IllegalStateException("Invalid Zeta configuration section: " + name);
      for (Object entry : entries) captureConfigDefinition(modId, entry, definitions);
    }
  }

  private static <T> JsonObject descriptions(
      Registry<T> registry, Function<T, String> description) {
    Map<String, String> entries = new TreeMap<>();
    for (var entry : registry.entrySet())
      entries.put(entry.getKey().location().toString(), description.apply(entry.getValue()));
    JsonObject result = new JsonObject();
    entries.forEach(result::addProperty);
    return result;
  }

  private static void translationKeys(Component component, Set<String> keys) {
    if (component.getContents() instanceof TranslatableContents contents) {
      keys.add(contents.getKey());
      for (Object argument : contents.getArgs())
        if (argument instanceof Component child) translationKeys(child, keys);
    }
    for (Component child : component.getSiblings()) translationKeys(child, keys);
  }
}
