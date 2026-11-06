package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ordana.immersive_weathering.reg.ModBlocks;
import com.ordana.immersive_weathering.reg.ModItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.mehvahdjukaar.moonlight.api.events.AfterLanguageLoadEvent;
import net.mehvahdjukaar.moonlight.api.resources.assets.LangBuilder;
import net.mehvahdjukaar.moonlight.api.set.BlockType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;

/** Actual native creative/search catalogs and the loaded cross-mod species roster. */
public final class WeatheringCatalogRegression {
  private static final Set<ResourceLocation> OPERATOR_ITEMS =
      Set.of(
          ResourceLocation.parse("immersive_weathering:fire"),
          ResourceLocation.parse("immersive_weathering:soul_fire"));

  private WeatheringCatalogRegression() {}

  public static JsonObject verify(ServerLevel level) {
    CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
    Set<Item> parentItems = new LinkedHashSet<>();
    Set<Item> searchItems = new LinkedHashSet<>();
    for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
      tab.getDisplayItems().forEach(stack -> parentItems.add(stack.getItem()));
      tab.getSearchTabDisplayItems().forEach(stack -> searchItems.add(stack.getItem()));
    }
    var missingParent = new JsonArray();
    var missingSearch = new JsonArray();
    var operatorItems = new JsonArray();
    int registered = 0;
    for (var entry : BuiltInRegistries.ITEM.entrySet()) {
      ResourceLocation id = entry.getKey().location();
      if (!id.getNamespace().equals("immersive_weathering")) continue;
      registered++;
      if (OPERATOR_ITEMS.contains(id)) {
        operatorItems.add(id.toString());
        continue;
      }
      if (!parentItems.contains(entry.getValue())) missingParent.add(id.toString());
      if (!searchItems.contains(entry.getValue())) missingSearch.add(id.toString());
    }
    require(
        missingParent.isEmpty(), "Weathering items missing from creative tabs: " + missingParent);
    require(missingSearch.isEmpty(), "Weathering items missing from search tabs: " + missingSearch);
    require(parentItems.contains(ModItems.STEEL_WOOL.get()), "Steel wool in a parent creative tab");
    require(
        searchItems.contains(ModItems.STEEL_WOOL.get()), "Steel wool in the creative search index");

    translatedCatalog();
    var leaves = new JsonArray();
    ModBlocks.LEAF_PILES.forEach(
        (type, pile) -> {
          var entry = new JsonObject();
          entry.addProperty("type", type.getId().toString());
          entry.addProperty("source_block", BuiltInRegistries.BLOCK.getKey(type.leaves).toString());
          entry.addProperty("pile_block", BuiltInRegistries.BLOCK.getKey(pile).toString());
          entry.addProperty("pile_item", BuiltInRegistries.ITEM.getKey(pile.asItem()).toString());
          leaves.add(entry);
        });
    var barks = new JsonArray();
    ModItems.BARK.forEach(
        (type, bark) -> {
          var entry = new JsonObject();
          entry.addProperty("type", type.getId().toString());
          entry.addProperty("source_block", BuiltInRegistries.BLOCK.getKey(type.log).toString());
          entry.addProperty("bark_item", BuiltInRegistries.ITEM.getKey(bark).toString());
          barks.add(entry);
        });
    var report = new JsonObject();
    report.addProperty("registered_items", registered);
    report.addProperty("static_items", registered - leaves.size() - barks.size());
    report.addProperty("ordinary_items", registered - operatorItems.size());
    report.add("operator_items", operatorItems);
    report.add("missing_parent", missingParent);
    report.add("missing_search", missingSearch);
    report.add("leaf_piles", leaves);
    report.add("bark_items", barks);
    return report;
  }

  private static void translatedCatalog() {
    Map<String, String> english = nativeLanguage("en_us");
    var englishEvent = new AfterLanguageLoadEvent(english, List.of("en_us"));
    Map<String, String> russian = nativeLanguage("ru_ru");
    var russianEvent = new AfterLanguageLoadEvent(russian, List.of("en_us", "ru_ru"));
    for (var event : List.of(englishEvent, russianEvent)) {
      ModBlocks.LEAF_PILES.forEach(
          (type, pile) -> generatedName(event, "block.immersive_weathering.leaf_pile", type, pile));
      ModItems.BARK.forEach(
          (type, bark) -> generatedName(event, "item.immersive_weathering.bark", type, bark));
    }
    for (var entry : BuiltInRegistries.ITEM.entrySet()) {
      ResourceLocation id = entry.getKey().location();
      if (!id.getNamespace().equals("immersive_weathering") || OPERATOR_ITEMS.contains(id))
        continue;
      String key = entry.getValue().getDescriptionId();
      require(english.containsKey(key), "Missing actual English item name: " + key);
      require(russian.containsKey(key), "Missing actual Russian item name: " + key);
      require(
          !englishEvent.getEntry(key).equals(russianEvent.getEntry(key)),
          "Untranslated weathering item: " + key);
    }
  }

  private static void generatedName(
      AfterLanguageLoadEvent event, String template, BlockType type, ItemLike content) {
    String key = content.asItem().getDescriptionId();
    String expected = event.getEntry(key);
    require(expected != null, "Full item name precedes native generation: " + key);
    // A different type label exposes accidental replacement by a generated default.
    event.addEntry(type.getTranslationKey(), "Native generated type");
    LangBuilder.addDynamicEntry(event, template, type, content.asItem());
    require(
        expected.equals(event.getEntry(key)),
        "Native generator preserves the full item name: " + key);
    require(
        !event.getExtraLanguageLines().containsKey(key),
        "No generated component shadows the item name: " + key);
  }

  private static Map<String, String> nativeLanguage(String locale) {
    String path = "assets/immersive_weathering/lang/" + locale + ".json";
    var provider = ModList.get().getModFileById("immersive_weathering");
    require(provider != null, "Native weathering provider exists");
    try (var reader =
        Files.newBufferedReader(provider.getFile().findResource(path), StandardCharsets.UTF_8)) {
      var document = JsonParser.parseReader(reader);
      require(document.isJsonObject(), "Native dictionary is an object: " + path);
      Map<String, String> lines = new HashMap<>();
      for (var entry : document.getAsJsonObject().entrySet()) {
        var value = entry.getValue();
        require(
            value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
            "Native dictionary value is text: " + entry.getKey());
        lines.put(entry.getKey(), value.getAsString());
      }
      return lines;
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot load native weathering dictionary", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
