package dev.aeroscapes.atmosphere;

import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.TextWidget.Alignment;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Keeps localized frequency labels below native ore and geode slots. */
public final class OreRecipeLayout {
  private static final int PADDING = 4;

  private OreRecipeLayout() {}

  public static int footerHeight(Font font, Component text, int width) {
    if (text == null) return 0;
    return Math.max(1, font.split(text, width - PADDING).size()) * font.lineHeight + 2 * PADDING;
  }

  public static TextWidget addFrequency(
      WidgetHolder widgets, Font font, Component text, int color, boolean shadow) {
    List<FormattedCharSequence> lines = font.split(text, widgets.getWidth() - PADDING);
    int firstCenter =
        widgets.getHeight()
            - footerHeight(font, text, widgets.getWidth())
            + PADDING
            + font.lineHeight / 2;
    int right = widgets.getWidth() - PADDING / 2;
    TextWidget first =
        widgets
            .addText(
                lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst(),
                right,
                firstCenter,
                color,
                shadow)
            .horizontalAlign(Alignment.END)
            .verticalAlign(Alignment.CENTER);
    for (int index = 1; index < lines.size(); index++) {
      widgets
          .addText(lines.get(index), right, firstCenter + index * font.lineHeight, color, shadow)
          .horizontalAlign(Alignment.END)
          .verticalAlign(Alignment.CENTER);
    }
    return first;
  }
}
