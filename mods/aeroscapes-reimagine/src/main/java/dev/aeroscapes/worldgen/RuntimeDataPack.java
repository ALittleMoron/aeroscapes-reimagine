package dev.aeroscapes.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Virtual server data pack; transformed provider JSON exists only in this process. */
public final class RuntimeDataPack extends AbstractPackResources {
  private static final byte[] METADATA =
      "{\"pack\":{\"pack_format\":48,\"description\":\"Aeroscapes local compatibility repairs\"}}"
          .getBytes(StandardCharsets.UTF_8);
  private final Map<ResourceLocation, byte[]> resources;

  private RuntimeDataPack(PackLocationInfo location, Map<ResourceLocation, byte[]> resources) {
    super(location);
    this.resources = Map.copyOf(resources);
  }

  public static void register(AddPackFindersEvent event) {
    if (event.getPackType() != PackType.SERVER_DATA) return;
    var title = Component.literal("Aeroscapes local compatibility repairs");
    var location =
        new PackLocationInfo(
            "aeroscapes_reimagine/local_repairs", title, PackSource.BUILT_IN, Optional.empty());
    var supplier =
        new Pack.ResourcesSupplier() {
          @Override
          public PackResources openPrimary(PackLocationInfo info) {
            return new RuntimeDataPack(info, Map.of());
          }

          @Override
          public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
            try {
              return new RuntimeDataPack(info, generate());
            } catch (IOException error) {
              throw new IllegalStateException("Cannot apply pinned local data repairs", error);
            }
          }
        };
    var pack =
        new Pack(
            location,
            supplier,
            new Pack.Metadata(
                title,
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                java.util.List.of(),
                false),
            new PackSelectionConfig(true, Pack.Position.TOP, true));
    event.addRepositorySource(consumer -> consumer.accept(pack));
  }

  private static Map<ResourceLocation, byte[]> generate() throws IOException {
    Map<ResourceLocation, byte[]> generated = new HashMap<>();
    for (JsonElement value : RuntimeJsonPatches.readRecipes().asMap().values()) {
      JsonObject recipe = value.getAsJsonObject();
      String modId = recipe.get("mod").getAsString();
      var file = ModList.get().getModFileById(modId);
      if (file == null) throw new IOException("Missing runtime repair provider: " + modId);
      byte[] original =
          Files.readAllBytes(file.getFile().findResource(recipe.get("source").getAsString()));
      byte[] result =
          RuntimeJsonPatches.apply(recipe, original).toString().getBytes(StandardCharsets.UTF_8);
      generated.put(ResourceLocation.parse(recipe.get("id").getAsString()), result);
    }
    return generated;
  }

  @Override
  @Nullable
  public IoSupplier<InputStream> getRootResource(String... path) {
    return path.length == 1 && path[0].equals("pack.mcmeta")
        ? () -> new ByteArrayInputStream(METADATA)
        : null;
  }

  @Override
  @Nullable
  public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
    byte[] content = type == PackType.SERVER_DATA ? resources.get(id) : null;
    return content == null ? null : () -> new ByteArrayInputStream(content);
  }

  @Override
  public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
    if (type != PackType.SERVER_DATA) return;
    resources.forEach(
        (id, content) -> {
          if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix + "/"))
            output.accept(id, () -> new ByteArrayInputStream(content));
        });
  }

  @Override
  public Set<String> getNamespaces(PackType type) {
    return type == PackType.SERVER_DATA
        ? resources.keySet().stream()
            .map(ResourceLocation::getNamespace)
            .collect(Collectors.toUnmodifiableSet())
        : Set.of();
  }

  @Override
  public void close() {}
}
