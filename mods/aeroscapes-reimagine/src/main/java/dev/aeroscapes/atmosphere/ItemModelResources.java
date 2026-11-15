package dev.aeroscapes.atmosphere;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Select native resources before model/state loaders run, including Modefite's separate item graph.
 */
public final class ItemModelResources {
  private static volatile boolean enabled = true;

  private ItemModelResources() {}

  public static boolean enabled() {
    return enabled;
  }

  public static boolean requested() {
    return !PresentationConfig.SPEC.isLoaded() || PresentationConfig.ITEM_MODELS_3D.get();
  }

  public static List<PackResources> prepare(PackType type, List<PackResources> packs) {
    if (type != PackType.CLIENT_RESOURCES) return packs;
    // Config screens update values before Save; render seams follow the resources actually loaded.
    enabled = requested();
    return select(type, packs, enabled);
  }

  static List<PackResources> select(PackType type, List<PackResources> packs, boolean use3d) {
    if (type != PackType.CLIENT_RESOURCES || use3d) return packs;
    return packs.stream()
        .map(
            pack ->
                switch (pack.packId()) {
                  case "file/Refined Tools 3.0.zip", "file/aeroscapes-reimagine-refined-compat" ->
                      (PackResources) new NativeResources(pack, false);
                  case "file/aeroscapes-reimagine-atmosphere" -> new NativeResources(pack, true);
                  default -> pack;
                })
        .toList();
  }

  private record NativeResources(PackResources original, boolean atmosphere)
      implements PackResources {
    private boolean hidden(PackType type, ResourceLocation location) {
      return type == PackType.CLIENT_RESOURCES
          && (!atmosphere || location.getPath().startsWith("models/item/"));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
      return original.getRootResource(path);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
      return hidden(type, location) ? null : original.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      original.listResources(
          type,
          namespace,
          path,
          (location, supplier) -> {
            if (!hidden(type, location)) output.accept(location, supplier);
          });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES && !atmosphere
          ? Set.of()
          : original.getNamespaces(type);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
      return original.getMetadataSection(serializer);
    }

    @Override
    public PackLocationInfo location() {
      return original.location();
    }

    @Override
    public boolean isHidden() {
      return original.isHidden();
    }

    @Override
    public void close() {
      original.close();
    }
  }
}
