package dev.aeroscapes.equipment;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class AccessoryGuides {
  public record Guide(
      List<ResourceLocation> items,
      String slot,
      List<String> hints,
      List<String> details,
      Optional<String> superseded,
      boolean nativeShiftDetails) {
    private static final Codec<Guide> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(Guide::items),
                        Codec.STRING.fieldOf("slot").forGetter(Guide::slot),
                        Codec.STRING
                            .listOf()
                            .optionalFieldOf("hints", List.of())
                            .forGetter(Guide::hints),
                        Codec.STRING
                            .listOf()
                            .optionalFieldOf("details", List.of())
                            .forGetter(Guide::details),
                        Codec.STRING.optionalFieldOf("superseded").forGetter(Guide::superseded),
                        Codec.BOOL
                            .optionalFieldOf("native_shift_details", false)
                            .forGetter(Guide::nativeShiftDetails))
                    .apply(instance, Guide::new));
  }

  public static final Map<ResourceLocation, Guide> ITEMS = load();

  private AccessoryGuides() {}

  private static Map<ResourceLocation, Guide> load() {
    try (var stream =
            Objects.requireNonNull(
                AccessoryGuides.class.getResourceAsStream(
                    "/aeroscapes_reimagine/accessory-guides.json"));
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var result = new LinkedHashMap<ResourceLocation, Guide>();
      for (var guide :
          Guide.CODEC
              .listOf()
              .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
              .getOrThrow()) {
        for (var item : guide.items()) {
          if (result.putIfAbsent(item, guide) != null)
            throw new IllegalStateException("Duplicate accessory guide for " + item);
        }
      }
      return Map.copyOf(result);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read accessory guides", exception);
    }
  }
}
