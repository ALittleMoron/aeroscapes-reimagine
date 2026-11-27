package dev.aeroscapes.races.client;

import dev.aeroscapes.magic.client.AbilityClient;
import dev.aeroscapes.progression.client.AtlasProgressionWidgets;
import dev.aeroscapes.races.ChangeNetwork;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public final class ChangeClient extends Screen implements ChangeView {
  private static int requestSequence;
  private final ChangeNetwork.Open session;
  @Nullable private final Screen previous;
  private final int sequence;
  @Nullable private final ChangeNetwork.Reply quote;
  private final Component choice;
  private boolean submitted;

  private ChangeClient(
      ChangeNetwork.Open session,
      @Nullable Screen previous,
      int sequence,
      @Nullable ChangeNetwork.Reply quote,
      Component choice) {
    super(text(session.identity() ? "identity" : "refund"));
    this.session = session;
    this.previous = previous;
    this.sequence = sequence;
    this.quote = quote;
    this.choice = choice;
  }

  public static MutableComponent text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.change." + key, arguments);
  }

  public static void open(ChangeNetwork.Open session) {
    var game = Minecraft.getInstance();
    if (game.player == null || game.level == null || !game.player.isAlive()) return;
    var profile = RaceClient.profile(game.player.getUUID());
    if (profile == null || !profile.complete()) return;
    if (session.identity()) {
      game.setScreen(new ChangeClient(session, null, 0, null, Component.empty()));
    } else {
      request(session, null, ChangeNetwork.Request.reset(session.id()), Component.empty());
    }
  }

  public static void preview(Screen editor, ChangeNetwork.Request request, Component choice) {
    if (!(editor instanceof ChangeView view) || view.changeSession() != request.id()) return;
    request(new ChangeNetwork.Open(request.id(), true), editor, request, choice);
  }

  private static void request(
      ChangeNetwork.Open session,
      @Nullable Screen editor,
      ChangeNetwork.Request request,
      Component choice) {
    if (++requestSequence == 0) requestSequence++;
    Minecraft.getInstance()
        .setScreen(new ChangeClient(session, editor, requestSequence, null, choice));
    PacketDistributor.sendToServer(request.numbered(requestSequence));
  }

  public static void reply(ChangeNetwork.Reply reply) {
    var game = Minecraft.getInstance();
    if (!(game.screen instanceof ChangeClient screen) || screen.changeSession() != reply.id())
      return;
    boolean waiting =
        screen.quote == null && screen.sequence != 0 && screen.sequence == reply.sequence();
    boolean confirmed =
        screen.submitted && screen.quote != null && screen.quote.ticket() == reply.ticket();
    if (confirmed || waiting && reply.status().equals("expired")) {
      game.setScreen(null);
      if (game.player != null) game.player.displayClientMessage(text(reply.status()), false);
      return;
    }
    if (waiting) {
      game.setScreen(
          new ChangeClient(screen.session, screen.previous, screen.sequence, reply, screen.choice));
    }
  }

  @Override
  public long changeSession() {
    return session.id();
  }

  private Component description() {
    if (sequence == 0) return text("choose");
    if (quote == null) return text("calculating");
    if (quote.ticket() == 0) return text("error." + quote.status());
    var body = Component.empty();
    if (session.identity()) body.append(choice).append("\n\n");
    body.append(text(session.identity() ? "reset_seal" : "reset_thread"))
        .append("\n\n")
        .append(text("reset_preserved"))
        .append("\n\n")
        .append(text("reset_banks"))
        .append("\n")
        .append(text("reset_penalty"));
    for (var bank : quote.pools()) {
      body.append("\n")
          .append(
              text(
                  "reset_bank",
                  poolName(bank.pool()),
                  signed(bank.before()),
                  signed(bank.after()),
                  bank.paid(),
                  bank.loss(),
                  bank.remainder()));
    }
    append(body, "reset_granted", quote.addedGrants(), ChangeClient::grantName);
    append(body, "reset_retired", quote.retiredGrants(), ChangeClient::grantName);
    append(body, "reset_spells_granted", quote.addedSpells(), AbilityClient::name);
    append(body, "reset_spells_retired", quote.retiredSpells(), AbilityClient::name);
    append(body, "reset_disabled", quote.disabled(), AbilityClient::name);
    append(body, "reset_armor", quote.wornGear(), ChangeClient::gearName);
    return body;
  }

  private static <T> void append(
      MutableComponent body, String heading, List<T> values, Function<T, Component> label) {
    if (values.isEmpty()) return;
    body.append("\n\n").append(text(heading));
    for (var value : values) body.append("\n").append(label.apply(value));
  }

  private static String signed(int points) {
    return points > 0 ? "+" + points : Integer.toString(points);
  }

  private static Component poolName(String pool) {
    return Component.translatable("puffish_skills.aeroscapes_reimagine." + pool + ".title");
  }

  private static Component grantName(ChangeNetwork.Grant grant) {
    var talent =
        Component.translatable(
            "puffish_skills.aeroscapes_reimagine." + grant.pool() + "." + grant.id() + ".title");
    return text("reset_grant", poolName(grant.pool()), talent);
  }

  private static Component gearName(String id) {
    var item = ResourceLocation.tryParse(id);
    return item != null && BuiltInRegistries.ITEM.containsKey(item)
        ? BuiltInRegistries.ITEM.get(item).getDescription()
        : Component.literal(id);
  }

  @Override
  protected void init() {
    int w = Math.min(420, width - 20),
        h = Math.min(280, height - 16),
        x = (width - w) / 2,
        y = (height - h) / 2;
    int bottom = y + h - 28;
    int contentBottom = bottom - 5;
    if (sequence == 0) {
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
        var name = page.name().toLowerCase(Locale.ROOT);
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
                x + 12, y + 32, w - 30, Math.max(16, contentBottom - y - 32), description(), font)
            .setColor(0xffd6e0eb));
    int buttonsWidth = w - 20;
    boolean canConfirm = quote != null && quote.ticket() != 0;
    var back =
        addRenderableWidget(
            Button.builder(
                    previous == null ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_BACK,
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
    PacketDistributor.sendToServer(new ChangeNetwork.Confirm(session.id(), 0, previous != null));
    minecraft.setScreen(previous);
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
