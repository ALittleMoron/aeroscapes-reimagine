package dev.aeroscapes.magic;

import dev.aeroscapes.magic.travel.AmuletStep;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.RaceIdentity;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

public final class PreparedAbilities {
  private PreparedAbilities() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::tick);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::login);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::logout);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::clonePlayer);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::dimension);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PreparedAbilities::cooldown);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::tooltip);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::amuletChanged);
  }

  public static Preparation preparation(ServerPlayer player) {
    CompoundTag stored = SpellKnowledge.data(player);
    ListTag values = stored.getList("slots", Tag.TAG_STRING);
    var slots = new ArrayList<String>();
    for (int index = 0; index < Preparation.SLOT_COUNT; index++) {
      String id = index < values.size() ? values.getString(index) : "";
      if (!id.isEmpty()) {
        var innate = InnateAbility.byId(id);
        boolean valid =
            innate != null
                || AmuletStep.ID.equals(id)
                || SpellKnowledge.spell(id) != SpellRegistry.none();
        if (!valid || slots.contains(id)) id = "";
      }
      slots.add(id);
    }
    int selected = Math.clamp(stored.getInt("selected"), 0, Preparation.SLOT_COUNT - 1);
    return new Preparation(slots, selected);
  }

  private static void save(ServerPlayer player, Preparation preparation) {
    CompoundTag stored = SpellKnowledge.data(player);
    ListTag slots = new ListTag();
    for (String id : preparation.slots()) slots.add(StringTag.valueOf(id));
    stored.put("slots", slots);
    stored.putInt("selected", preparation.selected());
  }

  public static void edit(ServerPlayer player, int slot, String id) {
    if (slot < 0 || slot >= Preparation.SLOT_COUNT || id.length() > 128) return;
    var innate = InnateAbility.byId(id);
    if (AmuletStep.ID.equals(id)) {
      if (!AmuletStep.available(player)) return;
    } else if (innate != null) {
      if (RaceIdentity.race(player) != innate.race) return;
    } else if (!id.isEmpty()) {
      AbstractSpell spell = SpellKnowledge.spell(id);
      if (spell == SpellRegistry.none()) return;
      String reason =
          SpellKnowledge.knowledgeReason(player, spell, SpellKnowledge.unlocked(player));
      if (!reason.isEmpty()) {
        SpellKnowledge.message(player, reason);
        sync(player, true);
        return;
      }
    }
    var prepared = preparation(player);
    if (AmuletStep.ID.equals(prepared.slots().get(slot)) && !AmuletStep.available(player))
      prepared = prepared.assign(slot, "");
    save(player, prepared.assign(slot, id));
    reconcileCasting(player);
    sync(player, true);
  }

  public static void select(ServerPlayer player, int slot) {
    if (slot < 0 || slot >= Preparation.SLOT_COUNT) return;
    save(player, preparation(player).select(slot));
    sync(player, true);
  }

  public static void cast(ServerPlayer player) {
    Preparation prepared = preparation(player);
    String id = prepared.slots().get(prepared.selected());
    if (id.isEmpty()) {
      SpellKnowledge.message(player, "empty");
      return;
    }
    var innate = InnateAbility.byId(id);
    if (AmuletStep.ID.equals(id)) {
      AmuletStep.cast(player);
      sync(player, false);
      return;
    }
    if (innate != null) {
      RacialAbilities.use(player, innate);
      sync(player, false);
      return;
    }
    AbstractSpell spell = SpellKnowledge.spell(id);
    if (spell == SpellRegistry.none()) return;
    projectFocus(player, prepared, SpellKnowledge.unlocked(player));
    ItemStack focus = Utils.getPlayerSpellbookStack(player);
    if (focus == null || focus.isEmpty()) {
      SpellKnowledge.message(player, "focus");
      return;
    }
    int rank = SpellKnowledge.rank(player, spell, SpellKnowledge.unlocked(player));
    spell.attemptInitiateCast(
        focus,
        spell.getLevelFor(rank, player),
        player.level(),
        player,
        CastSource.SPELLBOOK,
        true,
        Curios.SPELLBOOK_SLOT);
    sync(player, true);
  }

  public static boolean study(ServerPlayer player, InscriptionTableMenu menu) {
    ItemStack book = menu.getSpellBookSlot().getItem();
    ItemStack stack = menu.getScrollSlot().getItem();
    if (!(book.getItem() instanceof SpellBook) || !(stack.getItem() instanceof Scroll))
      return false;
    SpellData scroll = ISpellContainer.getOrCreate(stack).getSpellAtIndex(0);
    AbstractSpell spell = scroll.getSpell();
    if (spell == SpellRegistry.none() || !spell.isEnabled()) return false;
    Set<String> skills = SpellKnowledge.unlocked(player);
    String reason =
        SpellProgression.studyReason(
            SpellKnowledge.rank(player, spell, skills),
            scroll.getLevel(),
            SpellKnowledge.cap(spell, skills));
    if (reason.isEmpty() && spell.requiresLearning() && !spell.isLearned(player))
      reason = "discovery";
    if (!reason.isEmpty()) {
      SpellKnowledge.message(player, reason);
      return false;
    }
    CompoundTag data = SpellKnowledge.data(player);
    CompoundTag ranks = data.getCompound("ranks");
    ranks.putInt(spell.getSpellId(), scroll.getLevel());
    data.put("ranks", ranks);
    menu.getScrollSlot().remove(1);
    menu.broadcastChanges();
    player.displayClientMessage(
        net.minecraft.network.chat.Component.translatable(
            "aeroscapes_reimagine.abilities.studied",
            spell.getDisplayName(player),
            scroll.getLevel()),
        true);
    sync(player, true);
    return true;
  }

  private static void projectFocus(ServerPlayer player, Preparation prepared, Set<String> skills) {
    ItemStack focus = Utils.getPlayerSpellbookStack(player);
    if (focus == null || !SpellKnowledge.hasFocus(player)) return;
    var container = ISpellContainer.create(Preparation.SLOT_COUNT, true, true).mutableCopy();
    for (int index = 0; index < prepared.slots().size(); index++) {
      String id = prepared.slots().get(index);
      if (id.isEmpty() || InnateAbility.byId(id) != null || AmuletStep.ID.equals(id)) continue;
      AbstractSpell spell = SpellKnowledge.spell(id);
      if (spell != SpellRegistry.none()
          && SpellKnowledge.knowledgeReason(player, spell, skills).isEmpty())
        container.addSpellAtIndex(spell, SpellKnowledge.rank(player, spell, skills), index, true);
    }
    var projected = container.toImmutable();
    if (!projected.equals(ISpellContainer.get(focus))) ISpellContainer.set(focus, projected);
  }

  public static int cooldownTicks(ServerPlayer player, AbstractSpell spell) {
    var cooldown =
        MagicData.getPlayerMagicData(player)
            .getPlayerCooldowns()
            .getSpellCooldowns()
            .get(spell.getSpellId());
    return Math.max(
        cooldown == null ? 0 : cooldown.getCooldownRemaining(),
        dev.aeroscapes.magic.travel.TravelMagic.cooldownTicks(player, spell));
  }

  public static void sync(ServerPlayer player, boolean project) {
    long practice = MagicPractice.credit(player);
    Set<String> skills = SpellKnowledge.unlocked(player);
    Preparation prepared = preparation(player);
    RacialAbilities.reconcile(player, prepared);
    if (project) projectFocus(player, prepared, skills);
    var entries = new ArrayList<AbilityNetwork.Entry>();
    boolean amulet = AmuletStep.available(player);
    if (amulet) {
      int cooldown = AmuletStep.cooldown(player);
      entries.add(
          new AbilityNetwork.Entry(
              AmuletStep.ID,
              AmuletStep.GROUP,
              0,
              0,
              0,
              cooldown == 0,
              cooldown == 0 ? "" : "cooldown",
              cooldown,
              0));
    }
    var race = RaceIdentity.race(player);
    for (var innate : InnateAbility.values()) {
      boolean available = innate.race == race;
      if (available || prepared.slots().contains(innate.id))
        entries.add(
            new AbilityNetwork.Entry(
                innate.id, InnateAbility.GROUP, 0, 0, 0, available, available ? "" : "race", 0, 0));
    }
    for (AbstractSpell spell : SpellKnowledge.spells()) {
      int rank = SpellKnowledge.rank(player, spell, skills);
      String reason = SpellKnowledge.knowledgeReason(player, spell, skills);
      int cooldown = cooldownTicks(player, spell);
      if (reason.isEmpty() && !SpellKnowledge.hasFocus(player)) reason = "focus";
      if (reason.isEmpty()
          && cooldown > 0
          && !MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spell))
        reason = "cooldown";
      int effectiveRank = spell.getLevelFor(Math.max(1, rank), player);
      int mana =
          MagicBonuses.manaCost(
              spell, effectiveRank, player, CastSource.SPELLBOOK, spell.getManaCost(effectiveRank));
      if (reason.isEmpty()
          && MagicData.getPlayerMagicData(player).getMana() < mana
          && !MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(spell))
        reason = "mana";
      entries.add(
          new AbilityNetwork.Entry(
              spell.getSpellId(),
              spell.getSchoolType().getId().toString(),
              rank,
              SpellKnowledge.cap(spell, skills),
              spell.getMaxLevel(),
              reason.isEmpty(),
              reason,
              cooldown,
              mana));
    }
    PacketDistributor.sendToPlayer(
        player,
        new AbilityNetwork.Snapshot(
            entries,
            amulet
                ? prepared.slots()
                : prepared.slots().stream().map(id -> AmuletStep.ID.equals(id) ? "" : id).toList(),
            prepared.selected(),
            RacialAbilities.nightVision(player),
            java.util.List.copyOf(skills),
            MagicPracticeProgress.level(practice),
            practice,
            ProgressionBridge.nextCombatPointXp(player, practice)));
  }

  private static void reconcileCasting(ServerPlayer player) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    Preparation prepared = preparation(player);
    boolean focus = SpellKnowledge.hasFocus(player);
    Set<String> skills = SpellKnowledge.unlocked(player);
    if (magic.isCasting()
        && magic.getCastSource() != CastSource.SCROLL
        && (!focus
            || !prepared.slots().contains(magic.getCastingSpellId())
            || !SpellKnowledge.knowledgeReason(
                    player, SpellKnowledge.spell(magic.getCastingSpellId()), skills)
                .isEmpty())) Utils.serverSideCancelCast(player);
    for (var recast : magic.getPlayerRecasts().getAllRecasts()) {
      if (recast.getCastSource() != CastSource.SCROLL
          && (!focus
              || !prepared.slots().contains(recast.getSpellId())
              || !SpellKnowledge.knowledgeReason(
                      player, SpellKnowledge.spell(recast.getSpellId()), skills)
                  .isEmpty()))
        magic.getPlayerRecasts().removeRecast(recast, RecastResult.USER_CANCEL);
    }
  }

  private static long now(ServerPlayer player) {
    return player.server.overworld().getGameTime();
  }

  private static void captureCooldowns(ServerPlayer player) {
    CompoundTag cooldowns = new CompoundTag();
    MagicData.getPlayerMagicData(player)
        .getPlayerCooldowns()
        .getSpellCooldowns()
        .forEach(
            (id, value) -> {
              if (value.getCooldownRemaining() > 0)
                cooldowns.putLong(id, now(player) + value.getCooldownRemaining());
            });
    SpellKnowledge.data(player).put("cooldowns", cooldowns);
  }

  private static void restoreCooldowns(ServerPlayer player) {
    CompoundTag cooldowns = SpellKnowledge.data(player).getCompound("cooldowns");
    for (String id : cooldowns.getAllKeys()) {
      AbstractSpell spell = SpellKnowledge.spell(id);
      int ticks = Math.clamp(cooldowns.getLong(id) - now(player), 0, Integer.MAX_VALUE);
      if (ticks > 0 && spell != SpellRegistry.none())
        MagicData.getPlayerMagicData(player).getPlayerCooldowns().addCooldown(spell, ticks);
    }
    MagicData.getPlayerMagicData(player).getPlayerCooldowns().syncToPlayer(player);
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
    reconcileCasting(player);
    if (player.tickCount % 20 == 0) {
      captureCooldowns(player);
      sync(player, true);
    }
  }

  private static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      restoreCooldowns(player);
      sync(player, true);
    }
  }

  private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) captureCooldowns(player);
  }

  private static void clonePlayer(PlayerEvent.Clone event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      player
          .getPersistentData()
          .put(
              SpellKnowledge.DATA,
              event.getOriginal().getPersistentData().getCompound(SpellKnowledge.DATA).copy());
      restoreCooldowns(player);
    }
  }

  private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      restoreCooldowns(player);
      sync(player, true);
    }
  }

  private static void cooldown(SpellCooldownAddedEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) captureCooldowns(player);
  }

  private static void amuletChanged(CurioChangeEvent event) {
    var amulet = ItemRegistry.TELEPORTATION_AMULET.get();
    if (event.getEntity() instanceof ServerPlayer player
        && (event.getFrom().is(amulet) || event.getTo().is(amulet))) sync(player, false);
  }

  private static void tooltip(ItemTooltipEvent event) {
    List<String> lines;
    if (event.getItemStack().getItem() instanceof Scroll) lines = List.of("scroll_hint");
    else if (event.getItemStack().getItem() instanceof SpellBook) lines = List.of("book_hint");
    else if (event.getItemStack().is(ItemRegistry.TELEPORTATION_AMULET.get()))
      lines = List.of("amulet_equip", "amulet_hint");
    else return;
    for (String key : lines)
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.abilities." + key)
                  .withStyle(ChatFormatting.GRAY));
  }
}
