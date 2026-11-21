package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import com.mojang.math.Axis;
import dev.aeroscapes.races.Race;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

final class RaceTintPicker {
  private float hue;
  private float saturation;
  private float brightness;
  private int color;

  RaceTintPicker(int color, int defaultSwatch) {
    int swatch = color == Race.DEFAULT_TINT ? defaultSwatch : color;
    float[] hsb = Color.RGBtoHSB(swatch >> 16 & 255, swatch >> 8 & 255, swatch & 255, null);
    hue = hsb[0];
    saturation = hsb[1];
    brightness = hsb[2];
    this.color = color;
  }

  int color() {
    return color;
  }

  void reset() {
    color = Race.DEFAULT_TINT;
  }

  AbstractWidget palette(int x, int y, int width, int height) {
    return new Control(x, y, width, height, false);
  }

  AbstractWidget hueStrip(int x, int y, int width, int height) {
    return new Control(x, y, width, height, true);
  }

  private void select() {
    color = Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
  }

  private static void horizontalGradient(
      GuiGraphics graphics, int x, int y, int width, int height, int first, int last) {
    var pose = graphics.pose();
    pose.pushPose();
    pose.translate(x, y + height, 0);
    pose.mulPose(Axis.ZN.rotationDegrees(90));
    graphics.fillGradient(0, 0, height, width, first, last);
    pose.popPose();
  }

  private final class Control extends AbstractWidget {
    private final boolean hueStrip;

    Control(int x, int y, int width, int height, boolean hueStrip) {
      super(x, y, width, height, text(hueStrip ? "tint_hue" : "tint_palette"));
      this.hueStrip = hueStrip;
      setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      int x = getX();
      int y = getY();
      if (hueStrip) {
        for (int section = 0; section < 6; section++) {
          int start = section * width / 6;
          int end = (section + 1) * width / 6;
          horizontalGradient(
              graphics,
              x + start,
              y,
              end - start,
              height,
              Color.HSBtoRGB(section / 6f, 1, 1),
              Color.HSBtoRGB((section + 1) / 6f, 1, 1));
        }
        int marker = x + Math.round(hue * (width - 1));
        graphics.renderOutline(marker - 2, y - 1, 5, height + 2, 0xff181818);
        graphics.renderOutline(marker - 1, y, 3, height, 0xffffffff);
      } else {
        horizontalGradient(graphics, x, y, width, height, 0xffffffff, Color.HSBtoRGB(hue, 1, 1));
        graphics.fillGradient(x, y, x + width, y + height, 0x00000000, 0xff000000);
        if (color != Race.DEFAULT_TINT) {
          int markerX = x + Math.round(saturation * (width - 1));
          int markerY = y + Math.round((1 - brightness) * (height - 1));
          graphics.renderOutline(markerX - 4, markerY - 4, 9, 9, 0xff181818);
          graphics.renderOutline(markerX - 3, markerY - 3, 7, 7, 0xffffffff);
        }
      }
      if (isFocused()) graphics.renderOutline(x - 2, y - 2, width + 4, height + 4, 0xffe9c084);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
      moveTo(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
      moveTo(mouseX, mouseY);
    }

    private void moveTo(double mouseX, double mouseY) {
      float horizontal = Mth.clamp((float) ((mouseX - getX()) / (width - 1)), 0, 1);
      if (hueStrip) {
        hue = horizontal;
      } else {
        saturation = horizontal;
        brightness = 1 - Mth.clamp((float) ((mouseY - getY()) / (height - 1)), 0, 1);
      }
      select();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
      int horizontal = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
      int vertical = key == GLFW.GLFW_KEY_UP ? 1 : key == GLFW.GLFW_KEY_DOWN ? -1 : 0;
      if (hueStrip) {
        if (horizontal == 0) return false;
        hue = Mth.clamp(hue + horizontal / 360f, 0, 1);
      } else {
        if (horizontal == 0 && vertical == 0) return false;
        saturation = Mth.clamp(saturation + horizontal / 255f, 0, 1);
        brightness = Mth.clamp(brightness + vertical / 255f, 0, 1);
      }
      select();
      return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
      defaultButtonNarrationText(narration);
    }
  }
}
