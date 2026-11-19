package dev.aeroscapes.races;

import java.util.Locale;
import javax.annotation.Nullable;

public enum Race {
  HUMAN,
  DWARF,
  GOBLIN,
  ELF;

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public int styleCount() {
    return 3;
  }

  public boolean validStyle(int style) {
    return style >= 0 && style < styleCount();
  }

  @Nullable
  public static Race byId(String id) {
    return switch (id) {
      case "human" -> HUMAN;
      case "dwarf" -> DWARF;
      case "goblin" -> GOBLIN;
      case "elf" -> ELF;
      default -> null;
    };
  }
}
