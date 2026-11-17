package dev.aeroscapes.progression.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class InventoryButtonLayout {
  private InventoryButtonLayout() {}

  public static ScreenRectangle button(int index) {
    return new ScreenRectangle(80, 10 + 17 * index, 14, 14);
  }
}
