package dev.aeroscapes.backup;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

/** Exercises configured file selection through real snapshot capture and archive publication. */
public final class DistantHorizonsBackupPolicyTest {
  private DistantHorizonsBackupPolicyTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Expected the pack Simple Backups configuration path");
    }
    CommentedConfig config;
    try (var reader = Files.newBufferedReader(Path.of(arguments[0]), StandardCharsets.UTF_8)) {
      config = new TomlParser().parse(reader);
    }
    Object configuredExpression = config.get("to_ignore.ignored_files_regex");
    if (!(configuredExpression instanceof String expression)) {
      throw new IllegalArgumentException("to_ignore.ignored_files_regex must be a string");
    }
    Predicate<Path> include =
        WorldBackupFilePolicy.create(
            paths(config.get("to_ignore.ignored_paths"), "ignored_paths"),
            paths(config.get("to_ignore.ignored_files"), "ignored_files"),
            expression);
    captureExcludesOnlyCaches(include);
    ownerExclusionsRemainEffective();
  }

  private static List<Path> paths(Object value, String key) {
    if (!(value instanceof List<?> values)) {
      throw new IllegalArgumentException(key + " must be a list");
    }
    List<Path> result = new ArrayList<>();
    for (Object entry : values) {
      if (!(entry instanceof String path)) {
        throw new IllegalArgumentException(key + " entries must be strings");
      }
      result.add(Path.of(path));
    }
    return List.copyOf(result);
  }

  private static void captureExcludesOnlyCaches(Predicate<Path> include) throws IOException {
    List<String> excluded = new ArrayList<>();
    for (String folder :
        List.of(
            "data",
            "DIM-1/data",
            "DIM1/data",
            "dimensions/example/islands/data",
            "dimensions/example/layers/sky/data")) {
      for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
        excluded.add(folder + "/DistantHorizons.sqlite" + suffix);
      }
    }
    excluded.add("session.lock");
    excluded.add("data/saved-data.dat.neoforge-tmp");
    List<String> retained =
        List.of(
            "level.dat",
            "region/r.0.0.mca",
            "playerdata/00000000-0000-0000-0000-000000000001.dat",
            "sublevels/r.0.0.slvlr",
            "sublevels/r.0.0.0.slvls",
            "data/sable_sub_level_occupancy.dat",
            "DIM-1/sublevels/r.0.0.slvlr",
            "DIM1/data/sable_sub_level_force_load_tickets.dat",
            "dimensions/example/islands/region/r.0.0.mca",
            "data/other.sqlite",
            "data/DistantHorizons.sqlite.backup",
            "data/DistantHorizons.sqlite-wal.player",
            "DistantHorizons.sqlite",
            "playerdata/DistantHorizons.sqlite",
            "sublevels/data/DistantHorizons.sqlite",
            "other/DIM1/data/DistantHorizons.sqlite",
            "dimensions/example/DistantHorizons.sqlite",
            "dimensions/example/islands/sublevels/DistantHorizons.sqlite");
    Path temporary = Files.createTempDirectory("aeroscapes-dh-backup-policy-");
    try {
      Path world = Files.createDirectories(temporary.resolve("world"));
      for (String relative : excluded) writeFixtureFile(world, relative);
      for (String relative : retained) writeFixtureFile(world, relative);
      Path archive;
      try (var snapshot =
          ManagedBackupArchive.capture(world, temporary.resolve("backups"), include)) {
        archive = snapshot.publish(1, 5, 2L * 1024 * 1024 * 1024).archive();
      }
      try (var zip = new ZipFile(archive.toFile())) {
        require(zip.size() == retained.size(), "Unexpected snapshot file count");
        for (String relative : excluded) {
          require(
              zip.getEntry("world/" + relative) == null, "Excluded cache captured: " + relative);
          require(Files.exists(world.resolve(relative)), "Source file deleted: " + relative);
        }
        for (String relative : retained) {
          var entry = zip.getEntry("world/" + relative);
          require(entry != null, "World/player/ship file missing: " + relative);
          try (var input = zip.getInputStream(entry)) {
            String actual = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            require(actual.equals("saved state: " + relative), "Saved state changed: " + relative);
          }
        }
      }
    } finally {
      try (var files = Files.walk(temporary)) {
        for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
      }
    }
  }

  private static void writeFixtureFile(Path world, String relative) throws IOException {
    Path file = world.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "saved state: " + relative, StandardCharsets.UTF_8);
  }

  private static void ownerExclusionsRemainEffective() {
    Predicate<Path> include =
        WorldBackupFilePolicy.create(
            List.of(Path.of("owner-excluded")),
            List.of(Path.of("data/private.dat")),
            ".*[.]owner-cache");
    require(!include.test(Path.of("owner-excluded/nested/file.dat")), "Directory exclusion lost");
    require(!include.test(Path.of("data/private.dat")), "Exact file exclusion lost");
    require(include.test(Path.of("data/private.dat.copy")), "Exact exclusion became a prefix");
    require(!include.test(Path.of("data/custom.owner-cache")), "Owner regex exclusion lost");
    require(
        WorldBackupFilePolicy.create(List.of(), List.of(), "")
            .test(Path.of("data/DistantHorizons.sqlite")),
        "Owner replacement of the default regex was overridden");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
