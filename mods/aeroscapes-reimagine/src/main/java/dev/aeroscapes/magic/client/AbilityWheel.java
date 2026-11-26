package dev.aeroscapes.magic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Preparation selection never casts; only the server commits the selected slot. */
public final class AbilityWheel {
  private static boolean active;

  private AbilityWheel() {}

  public static boolean active() {
    return active;
  }

  public static void open() {
    var game = Minecraft.getInstance();
    if (active || game.screen != null || game.options.hideGui) return;
    active = true;
    game.mouseHandler.releaseMouse();
  }

  public static void release() {
    if (!active) return;
    int slot = hovered();
    cancel(true);
    if (slot >= 0) AbilityClient.select(slot);
  }

  public static void cancel(boolean restoreMouse) {
    boolean owned = active;
    active = false;
    var game = Minecraft.getInstance();
    if (owned
        && restoreMouse
        && game.isWindowActive()
        && game.player != null
        && game.level != null
        && game.screen == null
        && game.getOverlay() == null) game.mouseHandler.grabMouse();
  }

  private static int hovered() {
    var game = Minecraft.getInstance();
    double x =
        game.mouseHandler.xpos()
            * game.getWindow().getGuiScaledWidth()
            / game.getWindow().getScreenWidth();
    double y =
        game.mouseHandler.ypos()
            * game.getWindow().getGuiScaledHeight()
            / game.getWindow().getScreenHeight();
    return AbilityWheelGeometry.sector(
        x - game.getWindow().getGuiScaledWidth() / 2.0,
        y - game.getWindow().getGuiScaledHeight() / 2.0);
  }

  public static void render(GuiGraphics graphics) {
    if (!active) return;
    var game = Minecraft.getInstance();
    int centerX = game.getWindow().getGuiScaledWidth() / 2;
    int centerY = game.getWindow().getGuiScaledHeight() / 2;
    int selected = hovered();
    for (int y = -76; y < 76; y += 2) {
      int run = -76;
      int previous = 0;
      for (int x = -76; x <= 76; x += 2) {
        int radius = x * x + y * y;
        int sector = AbilityWheelGeometry.sector(x, y);
        int color =
            radius > 76 * 76 || radius < 30 * 30
                ? 0
                : radius > 73 * 73 || radius < 33 * 33
                    ? 0xffb99a6c
                    : sector == selected ? 0xee53647b : 0xee1b2b3d;
        if (color != previous || x == 76) {
          if (previous != 0)
            graphics.fill(centerX + run, centerY + y, centerX + x, centerY + y + 2, previous);
          run = x;
          previous = color;
        }
      }
    }
    for (int boundary = 0; boundary < AbilityClient.SLOT_COUNT; boundary++) {
      double angle = boundary * Math.PI / 4 + Math.PI / 8;
      for (int radius = 33; radius <= 75; radius++) {
        int x = centerX + (int) Math.round(Math.sin(angle) * radius);
        int y = centerY - (int) Math.round(Math.cos(angle) * radius);
        graphics.fill(x, y, x + 1, y + 1, 0xff627b96);
      }
    }
    graphics.drawCenteredString(
        game.font,
        Integer.toString((selected < 0 ? AbilityClient.snapshot().selected() : selected) + 1),
        centerX,
        centerY - 4,
        AbilityWidgets.GOLD);
    for (int slot = 0; slot < AbilityClient.SLOT_COUNT; slot++) {
      double angle = slot * Math.PI / 4;
      int x = centerX + (int) Math.round(Math.sin(angle) * 53);
      int y = centerY - (int) Math.round(Math.cos(angle) * 53);
      String id = AbilityClient.slot(slot);
      AbilityClient.icon(graphics, id, x - 9, y - 9, 18);
      graphics.drawString(
          game.font,
          Integer.toString(slot + 1),
          x + 8,
          y + 6,
          slot == selected ? AbilityWidgets.GOLD : AbilityWidgets.MUTED,
          false);
    }
    String id = selected < 0 ? "" : AbilityClient.slot(selected);
    Component name =
        id.isEmpty()
            ? AbilityClient.text(selected < 0 ? "wheel_hint" : "empty")
            : AbilityClient.name(id);
    graphics.drawCenteredString(game.font, name, centerX, centerY + 86, AbilityWidgets.GOLD);
    var entry = AbilityClient.entry(id);
    if (entry != null) {
      graphics.drawCenteredString(
          game.font, AbilityClient.status(entry), centerX, centerY + 99, AbilityWidgets.TEXT);
    }
  }
}
