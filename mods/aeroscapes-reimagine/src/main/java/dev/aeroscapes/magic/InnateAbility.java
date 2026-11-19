package dev.aeroscapes.magic;

import dev.aeroscapes.races.Race;

/** Stable catalogue and saved-slot identities, independent of Iron's spell registry. */
public enum InnateAbility {
  NIGHT_VISION("dwarf_night_vision", Race.DWARF),
  LIGHT("elf_light", Race.ELF);

  public static final String GROUP = "aeroscapes_reimagine:innate";
  public final String path;
  public final String id;
  public final Race race;

  InnateAbility(String path, Race race) {
    this.path = path;
    this.id = "aeroscapes_reimagine:" + path;
    this.race = race;
  }

  public static InnateAbility byId(String id) {
    if (NIGHT_VISION.id.equals(id)) return NIGHT_VISION;
    if (LIGHT.id.equals(id)) return LIGHT;
    return null;
  }
}
