package dev.aeroscapes.navigation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import xaero.map.file.MapSaveLoad;

public final class MapLayerSelectionTest {
  private MapLayerSelectionTest() {}

  public static void main(String[] args) {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var selection = new MapLayerSelection();
    require(
        selection.mode() == MapLayerSelection.Mode.NATIVE, "New worlds use native map settings");
    selection.toggleFollowing();
    require(selection.mode() == MapLayerSelection.Mode.FOLLOW, "Height following is opt-in");
    int upper = selection.ceiling(180, -128, 383);
    int lower = selection.ceiling(24, -128, 383);
    require(upper == 191 && lower == 47, "Overlapping islands select different native slices");
    require(
        lower < 180 && lower + 1 - MapLayerSelection.DEPTH <= 24,
        "Lower slice excludes the upper island and includes the lower one");
    require(selection.ceiling(-20, -128, 383) == -1, "Negative heights use floor division");
    require(selection.ceiling(380, -128, 383) == 383, "Automatic ceiling stays below build height");
    selection.move(-MapLayerSelection.STEP, 180, -128, 383);
    require(selection.mode() == MapLayerSelection.Mode.MANUAL, "Changing a layer fixes its height");
    require(
        selection.ceiling(-100, -128, 383) == 175, "Fixed layer stays selected while player moves");
    for (int i = 0; i < 100; i++) selection.move(-MapLayerSelection.STEP, 180, -128, 383);
    require(selection.ceiling(180, -128, 383) == -128, "Manual layer is clamped at world bottom");
    for (int i = 0; i < 100; i++) selection.move(MapLayerSelection.STEP, 180, -128, 383);
    require(selection.ceiling(180, -128, 383) == 383, "Manual layer is clamped at world top");
    selection.toggleFollowing();
    require(
        selection.mode() == MapLayerSelection.Mode.FOLLOW, "A fixed layer returns to following");
    selection.toggleFollowing();
    require(
        selection.mode() == MapLayerSelection.Mode.NATIVE,
        "Following can restore all native map preferences");
    selection.reset();
    require(
        selection.mode() == MapLayerSelection.Mode.NATIVE,
        "Manual height does not leak to another world");

    var storage = new MapSaveLoad(null, null, null, null);
    Path map = Path.of("world", "overworld");
    require(
        storage.getCaveLayerFolder(Integer.MAX_VALUE, map).equals(map),
        "Surface map keeps its original storage");
    require(
        storage.getCaveLayerFolder(upper >> 4, map).equals(map.resolve("caves").resolve("11")),
        "Upper slice uses its native layer folder");
    require(
        storage.getCaveLayerFolder(lower >> 4, map).equals(map.resolve("caves").resolve("2")),
        "Lower slice is stored independently of the upper slice");
    require(
        storage.getCaveLayerFolder(-1, map).equals(map.resolve("caves").resolve("-1")),
        "Negative slices retain native storage identity");

    Map<String, String> supported =
        Map.of(
            "xaeroworldmap",
            "1.47.0",
            "xaerominimap",
            "26.6.0",
            "xaerolib",
            "1.7.3",
            "neoforge",
            "21.1.244");
    require(
        NavigationMixinPlugin.supportsVersions(supported::get),
        "Exact native versions are supported");
    require(!NavigationMixinPlugin.supportsVersions(id -> null), "Optional maps may be absent");
    try {
      NavigationMixinPlugin.supportsVersions(
          id -> id.equals("xaeroworldmap") ? "1.48.0" : supported.get(id));
      throw new IllegalStateException("Changed native versions must require a fresh audit");
    } catch (IllegalStateException expected) {
      require(
          expected.getMessage().contains("requires xaeroworldmap"),
          "Version mismatch identifies the affected provider");
    }
    System.out.println(
        "Map layers: automatic/manual range, negative heights, clamps, native storage separation and version guard passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
