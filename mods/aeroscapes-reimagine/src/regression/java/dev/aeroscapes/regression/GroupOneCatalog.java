package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Supplies registered command IDs for the owner's manual acceptance checklist. */
public final class GroupOneCatalog {
  private static final Set<String> NAMESPACES =
      Set.of(
          "hybrid_aquatic",
          "tide",
          "hapi",
          "paraglider",
          "yo_hooks",
          "legendarysurvivaloverhaul",
          "nutritionalbalance");

  private GroupOneCatalog() {}

  public static void write(ServerLevel level, Path output) {
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    JsonArray allItems = new JsonArray();
    BuiltInRegistries.ITEM.keySet().stream()
        .map(ResourceLocation::toString)
        .sorted()
        .forEach(allItems::add);
    report.add("all_items", allItems);
    report.add("items", identifiers(BuiltInRegistries.ITEM.keySet()));
    report.add("entities", identifiers(BuiltInRegistries.ENTITY_TYPE.keySet()));
    report.add(
        "biomes", identifiers(level.registryAccess().registryOrThrow(Registries.BIOME).keySet()));
    report.add(
        "structures",
        identifiers(level.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()));
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException(
          "Cannot write the manual acceptance catalog " + output, failure);
    }
  }

  private static JsonArray identifiers(Set<ResourceLocation> keys) {
    JsonArray result = new JsonArray();
    keys.stream()
        .filter(key -> NAMESPACES.contains(key.getNamespace()))
        .map(ResourceLocation::toString)
        .sorted()
        .forEach(result::add);
    return result;
  }
}
