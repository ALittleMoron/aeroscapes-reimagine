package dev.aeroscapes.construction;

import org.lwjgl.glfw.GLFW;

/** Defaults supplied before Minecraft restores the player's saved key mappings. */
public final class ConstructionKeys {
  private ConstructionKeys() {}

  public static int defaultKey(String description, int original) {
    return switch (description) {
      case "key.little.mark" -> GLFW.GLFW_KEY_SEMICOLON;
      case "key.little.mirror" -> GLFW.GLFW_KEY_APOSTROPHE;
      case "key.little.config.item", "key.little.config_secondary.item" -> GLFW.GLFW_KEY_BACKSLASH;
      case "key.little.building_mode" -> GLFW.GLFW_KEY_EQUAL;
      default -> original;
    };
  }
}
