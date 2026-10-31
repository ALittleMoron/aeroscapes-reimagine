package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Captures actual registered description IDs without loading client classes or starting a client.
 */
public final class TranslationRegistryRegression {
  private TranslationRegistryRegression() {}

  public static void verify(ServerLevel level, Path output) {
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
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
