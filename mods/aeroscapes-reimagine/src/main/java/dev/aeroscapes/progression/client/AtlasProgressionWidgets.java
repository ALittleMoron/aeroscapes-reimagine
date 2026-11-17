package dev.aeroscapes.progression.client;

import net.minecraft.client.gui.GuiGraphics;

final class AtlasProgressionWidgets {
  private AtlasProgressionWidgets() {}

  static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
    graphics.fill(x, y, x + width, y + height, 0xff111b29);
    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff3d536d);
    graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xff1b2b3d);
    graphics.hLine(x + 7, x + Math.min(width - 7, 28), y + 4, 0xffb99a6c);
    graphics.hLine(x + Math.max(7, width - 28), x + width - 7, y + height - 5, 0xffb99a6c);
  }
}
