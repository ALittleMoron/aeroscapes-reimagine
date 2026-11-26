package dev.aeroscapes.races.client;

import dev.aeroscapes.progression.client.AtlasProgressionWidgets;
import dev.aeroscapes.races.ChangeNetwork;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.puffish.skillsmod.client.SkillsClientMod;

public final class ChangeClient extends Screen implements ChangeView {
  private final ChangeNetwork.Open session;
  private final Screen previous;
  private final ChangeNetwork.Reply quote;
  private final Component description;
  private boolean submitted;

  private ChangeClient(
      ChangeNetwork.Open session,
      Screen previous,
      ChangeNetwork.Reply quote,
      Component description) {
    super(text(session.identity() ? "identity" : "refund"));
    this.session = session;
    this.previous = previous;
    this.quote = quote;
    this.description = description;
  }

  public static MutableComponent text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.change." + key, arguments);
  }

  public static void open(ChangeNetwork.Open session) {
    var game = Minecraft.getInstance();
    if (game.player == null || game.level == null || !game.player.isAlive()) return;
    var profile = RaceClient.profile(game.player.getUUID());
    if (profile == null || !profile.complete()) return;
    game.setScreen(
        new ChangeClient(session, null, null, text(session.identity() ? "choose" : "loading")));
    if (!session.identity()) SkillsClientMod.getInstance().openScreen(Optional.empty());
  }

  public static long pendingTree() {
    var screen = Minecraft.getInstance().screen;
    return screen instanceof ChangeClient menu
            && !menu.session.identity()
            && menu.quote == null
            && menu.previous == null
        ? menu.changeSession()
        : 0;
  }

  public static void preview(
      Screen editor, ChangeNetwork.Request request, boolean identity, Component choice) {
    Minecraft.getInstance()
        .setScreen(
            new ChangeClient(new ChangeNetwork.Open(request.id(), identity), editor, null, choice));
    PacketDistributor.sendToServer(request);
  }

  public static void reply(ChangeNetwork.Reply reply) {
    var game = Minecraft.getInstance();
    if (!(game.screen instanceof ChangeView view) || view.changeSession() != reply.id()) return;
    if (reply.status().equals("done") || reply.status().equals("expired")) {
      game.setScreen(null);
      if (game.player != null) game.player.displayClientMessage(text(reply.status()), false);
      return;
    }
    if (!(game.screen instanceof ChangeClient waiting)
        || waiting.previous == null
        || waiting.quote != null) return;
    var identity = waiting.session.identity();
    MutableComponent body;
    if (reply.ticket() == 0) {
      body = text("error." + reply.status());
    } else {
      body = Component.empty();
      if (identity) body.append(waiting.description).append("\n\n");
      body.append(
          identity
              ? text("seal_cost", reply.freed(), reply.cost())
              : text(
                  "refund_cost",
                  reply.freed(),
                  reply.cost(),
                  reply.freed() - reply.cost(),
                  reply.remainder()));
      if (identity && reply.remainder() != 0) body.append("\n").append(text("advance"));
      if (!reply.nodes().isEmpty()) {
        body.append("\n\n").append(text("affected"));
        for (String node : reply.nodes())
          body.append("\n")
              .append(
                  Component.translatable(
                      "puffish_skills.aeroscapes_reimagine."
                          + reply.pool()
                          + "."
                          + node
                          + ".title"));
      }
    }
    game.setScreen(new ChangeClient(waiting.session, waiting.previous, reply, body));
  }

  @Override
  public long changeSession() {
    return session.id();
  }

  @Override
  protected void init() {
    int w = Math.min(420, width - 20),
        h = Math.min(280, height - 16),
        x = (width - w) / 2,
        y = (height - h) / 2;
    int bottom = y + h - 28;
    int contentBottom = bottom - 5;
    if (quote == null && session.identity() && previous == null) {
      var profile = RaceClient.profile(minecraft.player.getUUID());
      var pages =
          profile != null && profile.subclass() != null
              ? List.of(
                  RaceScreen.Page.RACE,
                  RaceScreen.Page.CLASS,
                  RaceScreen.Page.SUBCLASS,
                  RaceScreen.Page.PROFESSION)
              : List.of(RaceScreen.Page.RACE, RaceScreen.Page.CLASS, RaceScreen.Page.PROFESSION);
      int row = bottom - pages.size() * 21 - 5;
      for (RaceScreen.Page page : pages) {
        var name = page.name().toLowerCase(java.util.Locale.ROOT);
        addRenderableWidget(
            Button.builder(
                    RaceVisuals.text(name + "_page"),
                    button -> {
                      var current = RaceClient.profile(minecraft.player.getUUID());
                      if (current != null)
                        minecraft.setScreen(new RaceScreen(current, session.id(), page, this));
                    })
                .bounds(x + 10, row, w - 20, 19)
                .build());
        row += 21;
      }
      contentBottom -= pages.size() * 21 + 6;
    }
    addRenderableWidget(
        new FittingMultiLineTextWidget(
                x + 12, y + 32, w - 30, Math.max(16, contentBottom - y - 32), description, font)
            .setColor(0xffd6e0eb));
    int buttonsWidth = w - 20;
    boolean canConfirm = quote != null && quote.ticket() != 0;
    var back =
        addRenderableWidget(
            Button.builder(
                    quote == null ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_BACK,
                    b -> onClose())
                .bounds(x + 10, bottom, canConfirm ? (buttonsWidth - 4) / 2 : buttonsWidth, 20)
                .build());
    back.active = !submitted;
    if (canConfirm) {
      var confirm =
          addRenderableWidget(
              Button.builder(
                      text("spend_item"),
                      b -> {
                        submitted = true;
                        rebuildWidgets();
                        PacketDistributor.sendToServer(
                            new ChangeNetwork.Confirm(session.id(), quote.ticket()));
                      })
                  .bounds(x + 14 + (buttonsWidth - 4) / 2, bottom, (buttonsWidth - 4) / 2, 20)
                  .build());
      confirm.active = !submitted;
    }
  }

  @Override
  public void onClose() {
    if (submitted) return;
    if (quote == null) {
      cancel(session.id());
      minecraft.setScreen(null);
    } else minecraft.setScreen(previous);
  }

  public static void cancel(long session) {
    PacketDistributor.sendToServer(new ChangeNetwork.Confirm(session, 0));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mx, int my, float partial) {
    super.renderBackground(graphics, mx, my, partial);
    int w = Math.min(420, width - 20),
        h = Math.min(280, height - 16),
        x = (width - w) / 2,
        y = (height - h) / 2;
    AtlasProgressionWidgets.panel(graphics, x, y, w, h);
    graphics.drawString(font, title, x + 12, y + 12, 0xffe9c084, false);
  }
}
