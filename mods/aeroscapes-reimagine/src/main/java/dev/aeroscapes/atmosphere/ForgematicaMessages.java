package dev.aeroscapes.atmosphere;

import java.util.List;
import java.util.Map;

/** Recognizes the finite hardcoded messages in Forgematica 0.4.3. */
public final class ForgematicaMessages {
  private static final Map<String, String> STATIC =
      Map.ofEntries(
          Map.entry(
              "Custom preview image set", "aeroscapes_reimagine.forgematica.message.preview_set"),
          Map.entry(
              "Image 'thumb.png' not found",
              "aeroscapes_reimagine.forgematica.message.preview_missing"),
          Map.entry(
              "Failed to set custom preview image",
              "aeroscapes_reimagine.forgematica.message.preview_failed"),
          Map.entry(
              "Failed to read block position for vanilla structure",
              "aeroscapes_reimagine.forgematica.message.structure_position"),
          Map.entry(
              "SchematicaSchematic: Failed to read the block palette",
              "aeroscapes_reimagine.forgematica.message.schematica_palette"),
          Map.entry(
              "SchematicaSchematic: Old Schematica format detected, not currently implemented...",
              "aeroscapes_reimagine.forgematica.message.schematica_legacy"),
          Map.entry(
              "Select a placement either by looking at one in the world, holding the tool item and using the §etoolSelectElements§r hotkey (middle click by default), or alternatively by clicking on a placement in the Schematic Placements menu, so that it has the white outline indicating it's selected. The selected placement's name will also appear on the Tool HUD.",
              "aeroscapes_reimagine.forgematica.message.select_placement"),
          Map.entry(
              "No previous pasted version known, skipping delete",
              "aeroscapes_reimagine.forgematica.message.previous_version_missing"),
          Map.entry(
              "Deletion task failed", "aeroscapes_reimagine.forgematica.message.deletion_failed"),
          Map.entry(
              "Error: Calculated single box height was less than 1 block",
              "aeroscapes_reimagine.forgematica.message.box_height"));
  private static final List<Pattern> DYNAMIC =
      List.of(
          new Pattern(
              "Unknown block in the Sponge schematic palette: '",
              "'",
              "aeroscapes_reimagine.forgematica.message.sponge_unknown_block"),
          new Pattern(
              "Failed to read the Sponge schematic from '",
              "\"",
              "aeroscapes_reimagine.forgematica.message.sponge_read_failed"),
          new Pattern(
              "Failed to read the vanilla structure template from '",
              "\"",
              "aeroscapes_reimagine.forgematica.message.structure_read_failed"),
          new Pattern(
              "Exception while trying to load the vanilla structure: ",
              "",
              "aeroscapes_reimagine.forgematica.message.structure_exception"),
          new Pattern(
              "Failed to convert block with old name '",
              "'",
              "aeroscapes_reimagine.forgematica.message.old_block_conversion"),
          new Pattern(
              "Deleted ", " blocks", "aeroscapes_reimagine.forgematica.message.deleted_blocks"));

  private ForgematicaMessages() {}

  public static Translation resolve(String message, Object[] arguments) {
    if (arguments.length != 0) return null;
    String key = STATIC.get(message);
    if (key != null) return new Translation(key, arguments);
    for (Pattern pattern : DYNAMIC) {
      if (message.startsWith(pattern.prefix())
          && message.endsWith(pattern.suffix())
          && message.length() >= pattern.prefix().length() + pattern.suffix().length()) {
        String value =
            message.substring(
                pattern.prefix().length(), message.length() - pattern.suffix().length());
        if (pattern.key().endsWith("deleted_blocks")
            && (value.isEmpty() || !value.chars().allMatch(Character::isDigit))) continue;
        return new Translation(pattern.key(), new Object[] {value});
      }
    }
    return null;
  }

  public record Translation(String key, Object[] arguments) {}

  private record Pattern(String prefix, String suffix, String key) {}
}
