package dev.aeroscapes.magic.client;

import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** A view of the server's personal spell knowledge and preparation. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class AbilityClient {
  public static final int SLOT_COUNT = 8;
  private static final AbilityNetwork.Snapshot EMPTY =
      new AbilityNetwork.Snapshot(
          List.of(), java.util.Collections.nCopies(SLOT_COUNT, ""), 0, List.of(), 0, 0, 0);
  private static AbilityNetwork.Snapshot snapshot = EMPTY;
  private static long receivedAt;
  private static final AbilityHudSelection HUD_SELECTION = new AbilityHudSelection();

  private AbilityClient() {}

  public static void accept(AbilityNetwork.Snapshot update) {
    var game = Minecraft.getInstance();
    if (game.player == null || game.level == null) return;
    snapshot =
        new AbilityNetwork.Snapshot(
            List.copyOf(update.entries()),
            List.copyOf(update.slots()),
            update.selected(),
            List.copyOf(update.talents()),
            update.practiceLevel(),
            update.practiceXp(),
            update.nextTalentXp());
    MagicBonuses.setClientSkills(Set.copyOf(snapshot.talents()));
    HUD_SELECTION.accept(snapshot.selected(), slot(snapshot.selected()), Util.getMillis());
    receivedAt = game.level.getGameTime();
  }

  public static AbilityNetwork.Snapshot snapshot() {
    return snapshot;
  }

  public static boolean hasSnapshot() {
    return snapshot != EMPTY;
  }

  public static String slot(int index) {
    return index >= 0 && index < SLOT_COUNT && index < snapshot.slots().size()
        ? snapshot.slots().get(index)
        : "";
  }

  public static AbilityNetwork.Entry entry(String id) {
    return snapshot.entries().stream()
        .filter(entry -> entry.id().equals(id))
        .findFirst()
        .orElse(null);
  }

  public static AbilityNetwork.Entry selected() {
    return entry(slot(snapshot.selected()));
  }

  public static AbstractSpell spell(String id) {
    var location = ResourceLocation.tryParse(id);
    return location == null ? SpellRegistry.none() : SpellRegistry.getSpell(location);
  }

  public static Component name(String id) {
    return spell(id).getDisplayName(Minecraft.getInstance().player);
  }

  public static Component school(AbilityNetwork.Entry entry) {
    return spell(entry.id()).getSchoolType().getDisplayName();
  }

  public static Component text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.abilities." + key, arguments);
  }

  public static Component reason(AbilityNetwork.Entry entry) {
    return reason(entry.reason());
  }

  public static Component reason(String reason) {
    if (reason.isEmpty()) return text("ready");
    if (reason.equals("waiting")) return text("waiting");
    return Component.translatable("aeroscapes_reimagine.abilities.reason." + reason);
  }

  public static int cooldown(AbilityNetwork.Entry entry) {
    var level = Minecraft.getInstance().level;
    long elapsed = level == null ? 0 : Math.max(0, level.getGameTime() - receivedAt);
    return (int) Math.max(0, entry.cooldownTicks() - elapsed);
  }

  public static void request() {
    if (Minecraft.getInstance().getConnection() != null)
      PacketDistributor.sendToServer(new AbilityNetwork.Request());
  }

  public static void select(int slot) {
    if (slot >= 0 && slot < SLOT_COUNT)
      PacketDistributor.sendToServer(new AbilityNetwork.Select(slot));
  }

  public static void cast() {
    PacketDistributor.sendToServer(new AbilityNetwork.Cast());
  }

  public static void edit(int slot, String id) {
    PacketDistributor.sendToServer(new AbilityNetwork.Edit(slot, id));
  }

  @SubscribeEvent
  public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
    snapshot = EMPTY;
    MagicBonuses.clearClientSkills();
    HUD_SELECTION.reset();
    request();
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    snapshot = EMPTY;
    MagicBonuses.clearClientSkills();
    HUD_SELECTION.reset();
    AbilityWheel.cancel(false);
  }

  @SubscribeEvent
  public static void nativeLayers(RenderGuiLayerEvent.Pre event) {
    if (event.getName().getNamespace().equals("irons_spellbooks")
        && (event.getName().getPath().equals("spell_bar")
            || event.getName().getPath().equals("spell_wheel"))) event.setCanceled(true);
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post event) {
    var game = Minecraft.getInstance();
    if (!ModList.get().isLoaded("irons_spellbooks")
        || game.player == null
        || game.level == null
        || game.options.hideGui
        || game.player.isSpectator()
        || !game.player.isAlive()
        || game.screen != null
        || game.getOverlay() != null) return;
    var graphics = event.getGuiGraphics();
    if (AbilityWheel.active()) {
      AbilityWheel.render(graphics);
      return;
    }
    var entry = selected();
    int left = 8;
    int top = Math.max(8, game.getWindow().getGuiScaledHeight() - 38);
    int width = Math.max(16, Math.min(180, game.getWindow().getGuiScaledWidth() / 2 - 107));
    if (entry == null) {
      if (HUD_SELECTION.showEmptyHint(Util.getMillis()))
        graphics.drawString(
            game.font,
            game.font.plainSubstrByWidth(text("empty_hud").getString(), width),
            left,
            top + 4,
            AbilityWidgets.MUTED,
            true);
      return;
    }
    icon(graphics, entry.id(), left, top, 16);
    if (width <= 22) return;
    String name = name(entry.id()).getString();
    String visible = game.font.plainSubstrByWidth(name, width - 22);
    if (!visible.equals(name))
      visible = game.font.plainSubstrByWidth(name, width - 22 - game.font.width("…")) + "…";
    graphics.drawString(game.font, visible, left + 22, top + 4, AbilityWidgets.GOLD, true);
  }

  public static void icon(GuiGraphics graphics, String id, int x, int y, int size) {
    if (id.isEmpty()) return;
    graphics.blit(spell(id).getSpellIconResource(), x, y, 0, 0, size, size, size, size);
  }
}
