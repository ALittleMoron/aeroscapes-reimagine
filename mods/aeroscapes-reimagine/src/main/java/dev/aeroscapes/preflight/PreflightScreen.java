package dev.aeroscapes.preflight;

import dev.ryanhcode.sable.Sable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PreflightScreen extends Screen {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private final NumberFormat numbers;
  private PreflightNetwork.Snapshot snapshot;
  private long receivedAt;
  private long requestedAt;
  private int sequence;
  private int scroll;
  private int contentHeight;
  private Button refresh;

  public PreflightScreen() {
    super(text("title"));
    String language = Minecraft.getInstance().getLanguageManager().getSelected();
    numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.replace('_', '-')));
    numbers.setMaximumFractionDigits(1);
  }

  @Override
  protected void init() {
    int left = Math.max(12, width / 2 - 150);
    refresh =
        addRenderableWidget(
            Button.builder(text("refresh"), button -> request())
                .bounds(left, height - 30, 140, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(Math.min(width - 152, width / 2 + 10), height - 30, 140, 20)
            .build());
    if (requestedAt == 0) request();
  }

  private void request() {
    sequence = SEQUENCE.incrementAndGet();
    requestedAt = System.nanoTime();
    refresh.active = false;
    PacketDistributor.sendToServer(new PreflightNetwork.Request(sequence));
  }

  public void receive(PreflightNetwork.Snapshot value) {
    if (value.sequence() != sequence) return;
    snapshot = value;
    receivedAt = System.nanoTime();
    scroll = 0;
  }

  @Override
  public void tick() {
    if (minecraft == null || minecraft.player == null || minecraft.level == null) {
      onClose();
      return;
    }
    refresh.active = System.nanoTime() - requestedAt >= 1_000_000_000L;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics, mouseX, mouseY, partialTick);
    int left = Math.max(12, width / 2 - 210), right = Math.min(width - 12, width / 2 + 210);
    graphics.drawCenteredString(font, title, width / 2, 14, 0xffffff);
    List<FormattedCharSequence> lines = new ArrayList<>();
    for (Component row : rows()) {
      lines.addAll(font.split(row, right - left));
      lines.add(FormattedCharSequence.EMPTY);
    }
    contentHeight = lines.size() * 11;
    int bottom = height - 40;
    scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - 36)));
    graphics.enableScissor(left, 36, right, bottom);
    int y = 36 - scroll;
    for (FormattedCharSequence line : lines) {
      graphics.drawString(font, line, left, y, 0xe3e8ed, false);
      y += 11;
    }
    graphics.disableScissor();
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    scroll =
        Math.clamp(scroll - (int) (vertical * 22), 0, Math.max(0, contentHeight - (height - 76)));
    return true;
  }

  private List<Component> rows() {
    List<Component> rows = new ArrayList<>();
    if (snapshot == null) {
      rows.add(text(System.nanoTime() - requestedAt > 5_000_000_000L ? "timeout" : "loading"));
      return rows;
    }
    if (snapshot.bodies() == 0) {
      rows.add(text("aboard"));
      return rows;
    }
    long age = (System.nanoTime() - receivedAt) / 1_000_000_000L;
    boolean stale = age >= 5 || !stillAboard();
    rows.add(text(stale ? "stale" : "sample", age));
    if (!snapshot.name().isBlank()) rows.add(text("vessel", snapshot.name()));
    rows.add(text("bodies", snapshot.bodies()));
    rows.add(text("mass", number(snapshot.mass())));
    rows.add(text("support", number(snapshot.support())));
    rows.add(text("margin", number(snapshot.support() - snapshot.mass())));
    var assessment =
        PreflightReadings.assessment(
            snapshot.mass(),
            snapshot.support(),
            snapshot.balloons(),
            snapshot.leaking(),
            snapshot.partial() || stale,
            snapshot.consistentGravity());
    rows.add(text("assessment." + assessment.name().toLowerCase(Locale.ROOT)));
    rows.add(
        text("gas", snapshot.balloons(), number(snapshot.filled()), number(snapshot.capacity())));
    rows.add(text("target", number(snapshot.target()), number(snapshot.change())));
    rows.add(text(snapshot.leaking() ? "leak" : "no_leak"));
    for (int index = 0; index < snapshot.fuels().size(); index++) {
      var fuel = snapshot.fuels().get(index);
      Component item =
          fuel.count() == 0
              ? text("empty")
              : BuiltInRegistries.ITEM.get(fuel.item()).getDescription();
      rows.add(
          text(
              "fuel",
              index + 1,
              fuel.count(),
              item,
              fuel.infinite()
                  ? text("infinite")
                  : text("burn", number(fuel.burningTicks() / 20.0))));
    }
    if (snapshot.fuels().isEmpty()) rows.add(text("no_portable"));
    rows.add(text("fuel_scope"));
    rows.add(text("limits"));
    return rows;
  }

  private boolean stillAboard() {
    if (minecraft == null
        || minecraft.level == null
        || minecraft.player == null
        || !minecraft.level.dimension().location().equals(snapshot.dimension())) return false;
    var body = Sable.HELPER.getTrackingOrVehicleSubLevel(minecraft.player);
    return body != null && snapshot.members().contains(body.getUniqueId());
  }

  private String number(double value) {
    return Double.isFinite(value) ? numbers.format(value) : "—";
  }

  private static Component text(String key, Object... arguments) {
    return Component.translatable("screen.aeroscapes_preflight." + key, arguments);
  }
}
