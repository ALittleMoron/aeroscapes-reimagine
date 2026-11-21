package dev.aeroscapes.races;

import java.util.Locale;
import javax.annotation.Nullable;

public enum Race {
  HUMAN,
  DWARF,
  GOBLIN,
  ELF;

  public static final int DEFAULT_TINT = -1;

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public int styleCount() {
    return 3;
  }

  public boolean validStyle(int style) {
    return style >= 0 && style < styleCount();
  }

  public boolean hasEars() {
    return this == GOBLIN || this == ELF;
  }

  public boolean hasBeard(int style) {
    return validStyle(style) && (this == DWARF && style != 0 || this == HUMAN && style == 2);
  }

  public boolean canTint(int style, int tint) {
    return validStyle(style)
        && (tint == DEFAULT_TINT
            || tint >= 0 && tint <= 0xffffff && (hasEars() || hasBeard(style)));
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
