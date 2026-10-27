package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.worldgen.RuntimeTemplateRepairs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Exact original-resource and former-override parity, without embedding third-party templates. */
final class RuntimeTemplateRepairsRegression {
  private record NativeCase(
      ResourceLocation id, CompoundTag original, CompoundTag prepared, String loadedHash) {}

  private record CachedTemplate(StructureTemplate template, String fingerprint) {}

  private static final class Measurement {
    private final Map<ResourceLocation, CachedTemplate> cachedTemplates = new HashMap<>();
    private final List<NativeCase> nativeCases = new ArrayList<>();
    private long resourceLoadNanos;
  }

  private RuntimeTemplateRepairsRegression() {}

  static void verify(ServerLevel level, String configuration) {
    try {
      verifyGeneratedPrecedence(level.getServer());
      RuntimeJsonPatchesRegression.verify(level);
      Map<ResourceLocation, CachedTemplate> before = verifyTemplates(level, configuration);
      verifyWarmCache(level.getStructureManager(), before);
      level
          .getServer()
          .reloadResources(level.getServer().getPackRepository().getSelectedIds())
          .join();
      for (var entry : before.entrySet()) {
        StructureTemplate reloaded = level.getStructureManager().get(entry.getKey()).orElseThrow();
        require(
            reloaded != entry.getValue().template(),
            "Reload retained cached template: " + entry.getKey());
        require(
            ResourceFingerprints.nbt(reloaded.save(new CompoundTag()))
                .equals(entry.getValue().fingerprint()),
            "Reload changed template: " + entry.getKey());
      }
      RuntimeJsonPatchesRegression.verify(level);
      verifyWarmCache(level.getStructureManager(), verifyTemplates(level, configuration));
      System.out.println("Template repair lifecycle regression passed");
    } catch (IOException exception) {
      throw new IllegalStateException("Template repair lifecycle regression failed", exception);
    }
  }

  private static Map<ResourceLocation, CachedTemplate> verifyTemplates(
      ServerLevel level, String configuration) {
    try {
      JsonArray templates = readConfiguration(configuration).getAsJsonArray("templates");
      require(templates.size() == 925, "Expected the complete 925-template baseline");
      JsonArray fingerprints = new JsonArray();
      int repairs = 0;
      Measurement measurement = new Measurement();
      for (var value : templates) {
        JsonObject row = value.getAsJsonObject();
        if (verifyTemplate(level.getServer(), row, fingerprints, measurement)) repairs++;
      }
      require(repairs == 95, "Expected 95 field repairs and 830 native DFU resources");
      JsonObject timings = benchmark(level.getServer(), measurement);
      String output = System.getProperty("aeroscapes.regression.templateRepairReport");
      if (output != null) {
        JsonObject report = new JsonObject();
        report.add("templates", fingerprints);
        report.add("native_dfu_benchmark", timings);
        Files.writeString(
            Path.of(output), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      }
      System.out.println(
          "Template repair regression passed: templates=925 repairs=95 nativeDfu=830");
      return Map.copyOf(measurement.cachedTemplates);
    } catch (IOException exception) {
      throw new IllegalStateException("Template repair regression failed", exception);
    }
  }

  private static JsonObject readConfiguration(String configuration) throws IOException {
    if (!configuration.equals("true")) {
      return JsonParser.parseString(Files.readString(Path.of(configuration))).getAsJsonObject();
    }
    try (var reader =
        new InputStreamReader(
            Objects.requireNonNull(
                RuntimeTemplateRepairsRegression.class.getResourceAsStream(
                    "/template-repairs.json")),
            StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static boolean verifyTemplate(
      MinecraftServer server, JsonObject row, JsonArray fingerprints, Measurement measurement)
      throws IOException {
    ResourceLocation id = ResourceLocation.parse(row.get("id").getAsString());
    String path = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
    ResourceLocation resource =
        ResourceLocation.fromNamespaceAndPath(
            id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    byte[] effective;
    try (var input = server.getResourceManager().open(resource)) {
      effective = input.readAllBytes();
    }
    if (row.has("source_jar")) {
      try (var archive = new ZipFile(row.get("source_jar").getAsString());
          var input = archive.getInputStream(Objects.requireNonNull(archive.getEntry(path)))) {
        require(
            Arrays.equals(effective, input.readAllBytes()), "Effective original changed: " + id);
      }
    } else {
      require(
          ResourceFingerprints.sha256(effective).equals(row.get("source_sha256").getAsString()),
          "Original hash differs: " + id);
    }
    CompoundTag original = decode(effective);
    CompoundTag repaired = original.copy();
    RuntimeTemplateRepairs.apply(id, repaired);
    CompoundTag second = repaired.copy();
    RuntimeTemplateRepairs.apply(id, second);
    require(second.equals(repaired), "Repair is not idempotent: " + id);
    var manager = server.getStructureManager();
    manager.remove(id);
    long started = System.nanoTime();
    StructureTemplate loaded = manager.get(id).orElseThrow();
    long elapsed = System.nanoTime() - started;
    if (id.getNamespace().equals("nova_structures")) measurement.resourceLoadNanos += elapsed;
    CompoundTag actual = loaded.save(new CompoundTag());
    boolean applies = RuntimeTemplateRepairs.applies(id);
    String loadedHash = ResourceFingerprints.nbt(actual);
    String typedHash = ResourceFingerprints.nbt(repaired);
    if (row.has("baseline")) {
      Path baselinePath = Path.of(row.get("baseline").getAsString());
      byte[] encodedBaseline = Files.readAllBytes(baselinePath);
      require(
          ResourceFingerprints.sha256(encodedBaseline)
              .equals(row.get("baseline_sha256").getAsString()),
          "Baseline snapshot changed: " + id);
      CompoundTag baseline = decode(encodedBaseline);
      if (applies) {
        require(
            repaired.equals(baseline),
            "Typed original-resource repair differs from override: " + id);
      } else {
        CompoundTag fixed =
            DataFixTypes.STRUCTURE.updateToCurrentVersion(
                server.getFixerUpper(), original.copy(), NbtUtils.getDataVersion(original, 500));
        CompoundTag expected = baseline.copy();
        fixed.remove("DataVersion");
        expected.remove("DataVersion");
        require(fixed.equals(expected), "Native DFU loses baseline typed content: " + id);
      }
      CompoundTag expectedLoaded = manager.readStructure(baseline).save(new CompoundTag());
      require(actual.equals(expectedLoaded), "Resource hook differs from loaded baseline: " + id);
      if (id.getNamespace().equals("nova_structures")) {
        measurement.nativeCases.add(new NativeCase(id, original, baseline, loadedHash));
      }
    } else {
      require(
          typedHash.equals(row.get("repaired_nbt_sha256").getAsString()),
          "Typed repair differs: " + id);
      require(
          loadedHash.equals(row.get("loaded_nbt_sha256").getAsString()),
          "Loaded template differs: " + id);
    }
    measurement.cachedTemplates.put(id, new CachedTemplate(loaded, loadedHash));
    JsonObject fingerprint = new JsonObject();
    fingerprint.addProperty("id", id.toString());
    fingerprint.addProperty("source_sha256", ResourceFingerprints.sha256(effective));
    fingerprint.addProperty("repaired_nbt_sha256", typedHash);
    fingerprint.addProperty("loaded_nbt_sha256", loadedHash);
    fingerprints.add(fingerprint);
    return applies;
  }

  private static JsonObject benchmark(MinecraftServer server, Measurement measurement)
      throws IOException {
    JsonObject report = new JsonObject();
    report.addProperty("fixer_class", server.getFixerUpper().getClass().getName());
    report.addProperty("measured_resource_cache_misses", 830);
    report.addProperty("resource_load_ns", measurement.resourceLoadNanos);
    report.addProperty(
        "includes", "resource I/O, native DFU and StructureTemplate.load; excludes save/hash");
    report.addProperty(
        "batch_scope", "warmed in-memory readStructure; copied input and save/hash outside timing");
    JsonArray pairs = new JsonArray();
    List<NativeCase> cases = measurement.nativeCases;
    if (!cases.isEmpty()) {
      require(cases.size() == 830, "Native DFU benchmark requires the complete 830-template set");
      for (int index = 0; index < 3; index++) {
        boolean originalFirst = index % 2 == 0;
        long originalNanos;
        long preparedNanos;
        if (originalFirst) {
          originalNanos = loadBatch(server.getStructureManager(), cases, false);
          preparedNanos = loadBatch(server.getStructureManager(), cases, true);
        } else {
          preparedNanos = loadBatch(server.getStructureManager(), cases, true);
          originalNanos = loadBatch(server.getStructureManager(), cases, false);
        }
        JsonObject pair = new JsonObject();
        pair.addProperty("pair", index + 1);
        pair.addProperty("templates_per_batch", cases.size());
        pair.addProperty("order", originalFirst ? "original,prepared" : "prepared,original");
        pair.addProperty("original_ns", originalNanos);
        pair.addProperty("prepared_ns", preparedNanos);
        pairs.add(pair);
        System.out.println(
            "Native DFU template batch: pair="
                + (index + 1)
                + " templates=830 order="
                + (originalFirst ? "original,prepared" : "prepared,original")
                + " original_ms="
                + originalNanos / 1_000_000.0
                + " prepared_ms="
                + preparedNanos / 1_000_000.0);
      }
    }
    report.add("alternating_pairs", pairs);
    System.out.println(
        "Native DFU resource cache misses: templates=830 total_ms="
            + measurement.resourceLoadNanos / 1_000_000.0
            + " fixer="
            + server.getFixerUpper().getClass().getName());
    return report;
  }

  private static long loadBatch(
      StructureTemplateManager manager, List<NativeCase> cases, boolean prepared)
      throws IOException {
    List<CompoundTag> inputs = new ArrayList<>(cases.size());
    for (NativeCase sample : cases) {
      inputs.add((prepared ? sample.prepared() : sample.original()).copy());
    }
    StructureTemplate[] loaded = new StructureTemplate[cases.size()];
    long started = System.nanoTime();
    for (int index = 0; index < inputs.size(); index++) {
      loaded[index] = manager.readStructure(inputs.get(index));
    }
    long elapsed = System.nanoTime() - started;
    for (int index = 0; index < loaded.length; index++) {
      require(
          ResourceFingerprints.nbt(loaded[index].save(new CompoundTag()))
              .equals(cases.get(index).loadedHash()),
          "Benchmark changed loaded template: " + cases.get(index).id());
    }
    return elapsed;
  }

  private static CompoundTag decode(byte[] encoded) throws IOException {
    return NbtIo.readCompressed(new ByteArrayInputStream(encoded), NbtAccounter.unlimitedHeap());
  }

  private static void verifyWarmCache(
      StructureTemplateManager manager, Map<ResourceLocation, CachedTemplate> expected)
      throws IOException {
    for (var entry : expected.entrySet()) {
      StructureTemplate cached = manager.get(entry.getKey()).orElseThrow();
      require(
          cached == entry.getValue().template(),
          "Warm lookup replaced template: " + entry.getKey());
      require(
          ResourceFingerprints.nbt(cached.save(new CompoundTag()))
              .equals(entry.getValue().fingerprint()),
          "Warm lookup changed template: " + entry.getKey());
    }
  }

  private static void verifyGeneratedPrecedence(MinecraftServer server) throws IOException {
    var manager = server.getStructureManager();
    ResourceLocation id =
        ResourceLocation.parse("minecraft:ancient_city/city_center/city_center_1");
    Path path =
        manager.createAndValidatePathToGeneratedStructure(id, ".nbt").toAbsolutePath().normalize();
    require(
        path.toString().contains("/.runtime/"),
        "Generated sentinel requires a disposable .runtime world");
    byte[] previous = Files.exists(path) ? Files.readAllBytes(path) : null;
    CompoundTag sentinel = new CompoundTag();
    sentinel.put("size", coordinates(1, 2, 1));
    ListTag palette = new ListTag();
    CompoundTag stone = new CompoundTag();
    stone.putString("Name", "minecraft:stone");
    palette.add(stone);
    sentinel.put("palette", palette);
    ListTag blocks = new ListTag();
    CompoundTag bottom = new CompoundTag();
    bottom.put("pos", coordinates(0, 0, 0));
    bottom.putInt("state", 0);
    blocks.add(bottom);
    sentinel.put("blocks", blocks);
    sentinel.put("entities", new ListTag());
    NbtUtils.addCurrentDataVersion(sentinel);
    CompoundTag expected = manager.readStructure(sentinel.copy()).save(new CompoundTag());
    try {
      Files.createDirectories(path.getParent());
      NbtIo.writeCompressed(sentinel, path);
      manager.remove(id);
      StructureTemplate saved = manager.get(id).orElseThrow();
      require(
          saved.save(new CompoundTag()).equals(expected),
          "Generated template lost precedence or received resource-only repairs");
      require(manager.get(id).orElseThrow() == saved, "Generated template bypassed native cache");
    } finally {
      if (previous == null) Files.deleteIfExists(path);
      else Files.write(path, previous);
      manager.remove(id);
    }
  }

  private static ListTag coordinates(int x, int y, int z) {
    ListTag values = new ListTag();
    values.addAll(List.of(IntTag.valueOf(x), IntTag.valueOf(y), IntTag.valueOf(z)));
    return values;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
