package dev.aeroscapes.magic;

import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import dev.aeroscapes.equipment.expedition.StoredBreath;
import dev.aeroscapes.expedition.ExpeditionDiscoveries;
import dev.aeroscapes.magic.travel.AmuletStep;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.RaceIdentity;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
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
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
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
  public static final String DISCOVERY_GROUP = "aeroscapes_reimagine:expedition_discovery";
  public static final String DISTRACTION = "aeroscapes_reimagine:distraction_throw";
  private static final String BORROWED_INVISIBILITY = "borrowed_invisibility_active";

  private PreparedAbilities() {}

  public static void register() {
    MartialTechniques.register();
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::tick);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::login);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::logout);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::clonePlayer);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::dimension);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PreparedAbilities::cooldown);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::tooltip);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::curioChanged);
    NeoForge.EVENT_BUS.addListener(PreparedAbilities::recordInvisibilitySource);
  }

  public static Preparation preparation(ServerPlayer player) {
    return loadout(player).active();
  }

  public static AbilityLoadout loadout(ServerPlayer player) {
    CompoundTag stored = SpellKnowledge.data(player);
    var wheels = new ArrayList<Preparation>(AbilityLoadout.WHEEL_COUNT);
    for (int wheel = 0; wheel < AbilityLoadout.WHEEL_COUNT; wheel++)
      wheels.add(readWheel(wheel == 0 ? stored : stored.getCompound(wheelKey(wheel))));
    int active = stored.getInt("active_wheel");
    return new AbilityLoadout(wheels, AbilityLoadout.validWheel(active) ? active : 0);
  }

  private static String wheelKey(int wheel) {
    return "wheel_" + (wheel + 1);
  }

  private static Preparation readWheel(CompoundTag stored) {
    ListTag values = stored.getList("slots", Tag.TAG_STRING);
    var slots = new ArrayList<String>();
    for (int index = 0; index < Preparation.SLOT_COUNT; index++) {
      String id = index < values.size() ? values.getString(index) : "";
      if (!id.isEmpty()) {
        var innate = InnateAbility.byId(id);
        boolean valid =
            innate != null
                || MartialTechnique.byId(id) != null
                || AmuletStep.ID.equals(id)
                || ExceptionalAccessory.byId(id) != null
                || DISTRACTION.equals(id)
                || HexPrograms.itemType(id) != null
                || SpellKnowledge.spell(id) != SpellRegistry.none();
        if (!valid || slots.contains(id)) id = "";
      }
      slots.add(id);
    }
    int selected = Math.clamp(stored.getInt("selected"), 0, Preparation.SLOT_COUNT - 1);
    return new Preparation(slots, selected);
  }

  private static void save(ServerPlayer player, int wheel, Preparation preparation) {
    CompoundTag data = SpellKnowledge.data(player);
    CompoundTag stored = wheel == 0 ? data : data.getCompound(wheelKey(wheel));
    ListTag slots = new ListTag();
    for (String id : preparation.slots()) slots.add(StringTag.valueOf(id));
    stored.put("slots", slots);
    stored.putInt("selected", preparation.selected());
    if (wheel != 0) data.put(wheelKey(wheel), stored);
  }

  public static void edit(ServerPlayer player, int slot, String id) {
    edit(player, loadout(player).activeWheel(), slot, id);
  }

  public static void edit(ServerPlayer player, int wheel, int slot, String id) {
    if (!AbilityLoadout.validWheel(wheel)
        || slot < 0
        || slot >= Preparation.SLOT_COUNT
        || id.length() > 128) return;
    var innate = InnateAbility.byId(id);
    var technique = MartialTechnique.byId(id);
    if (HexPrograms.itemType(id) != null) {
      if (!HexPrograms.inventory(player).containsKey(id)) {
        SpellKnowledge.message(player, "hex_missing");
        sync(player, false);
        return;
      }
    } else if (AmuletStep.ID.equals(id)) {
      if (!AmuletStep.available(player)) return;
    } else if (ExceptionalAccessory.byId(id) != null) {
      if (ExceptionalAccessory.byId(id).equipped(player).isEmpty()) return;
    } else if (DISTRACTION.equals(id)) {
      if (!ExpeditionDiscoveries.knowsDistraction(player)) return;
    } else if (innate != null) {
      if (RaceIdentity.race(player) != innate.race) return;
    } else if (technique != null) {
      if (!MartialTechniques.learned(player, technique)) {
        SpellKnowledge.message(player, "technique_unlearned");
        sync(player, false);
        return;
      }
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
    var prepared = loadout(player).wheels().get(wheel);
    String previous = prepared.slots().get(slot);
    var previousAccessory = ExceptionalAccessory.byId(previous);
    if (AmuletStep.ID.equals(previous) && !AmuletStep.available(player)
        || previousAccessory != null && previousAccessory.equipped(player).isEmpty()
        || DISTRACTION.equals(previous) && !ExpeditionDiscoveries.knowsDistraction(player))
      prepared = prepared.assign(slot, "");
    save(player, wheel, prepared.assign(slot, id));
    MartialTechniques.reconcile(player, loadout(player).assignedIds());
    reconcileCasting(player);
    sync(player, true);
  }

  public static void select(ServerPlayer player, int slot) {
    if (slot >= 0) select(player, loadout(player).activeWheel(), slot);
  }

  public static void select(ServerPlayer player, int wheel, int slot) {
    select(player, wheel, slot, 0);
  }

  static void select(ServerPlayer player, int wheel, int slot, int sequence) {
    if (!AbilityLoadout.validWheel(wheel) || slot < -1 || slot >= Preparation.SLOT_COUNT) return;
    if (slot >= 0) save(player, wheel, loadout(player).wheels().get(wheel).select(slot));
    SpellKnowledge.data(player).putInt("active_wheel", wheel);
    sync(player, true, sequence);
  }

  public static void cast(ServerPlayer player) {
    Preparation prepared = preparation(player);
    String id = prepared.slots().get(prepared.selected());
    if (id.isEmpty()) {
      SpellKnowledge.message(player, "empty");
      return;
    }
    var innate = InnateAbility.byId(id);
    var technique = MartialTechnique.byId(id);
    if (technique != null) {
      MartialTechniques.activate(player, technique);
      sync(player, false);
      return;
    }
    if (HexPrograms.itemType(id) != null) {
      HexPrograms.cast(player, id);
      sync(player, false);
      return;
    }
    if (AmuletStep.ID.equals(id)) {
      AmuletStep.cast(player);
      sync(player, false);
      return;
    }
    var accessory = ExceptionalAccessory.byId(id);
    if (accessory != null) {
      if (accessory == ExceptionalAccessory.BREATH) {
        StoredBreath.use(player);
      } else {
        var ring = accessory.equipped(player);
        if (ring.isEmpty()) SpellKnowledge.message(player, "equipment_required");
        else
          accessory
              .spell()
              .attemptInitiateCast(
                  ring, 1, player.level(), player, CastSource.SPELLBOOK, true, "ring");
      }
      sync(player, false);
      return;
    }
    if (DISTRACTION.equals(id)) {
      if (ExpeditionDiscoveries.knowsDistraction(player))
        ExpeditionDiscoveries.throwDistraction(player);
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
      if (id.isEmpty()
          || InnateAbility.byId(id) != null
          || MartialTechnique.byId(id) != null
          || AmuletStep.ID.equals(id)
          || ExceptionalAccessory.byId(id) != null
          || DISTRACTION.equals(id)
          || HexPrograms.itemType(id) != null) continue;
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
    sync(player, project, 0);
  }

  private static void sync(ServerPlayer player, boolean project, int selectionAck) {
    long practice = MagicPractice.credit(player);
    Set<String> skills = SpellKnowledge.unlocked(player);
    AbilityLoadout prepared = loadout(player);
    RacialAbilities.reconcile(player, prepared);
    if (project) projectFocus(player, prepared.active(), skills);
    var entries = new ArrayList<AbilityNetwork.Entry>();
    HexPrograms.catalogue(player, prepared, entries);
    addExpeditionAbilities(player, entries);
    MartialTechniques.catalogue(player, entries);
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
      if (available || prepared.contains(innate.id))
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
            visibleLoadout(prepared, amulet, entries),
            selectionAck,
            RacialAbilities.nightVision(player),
            java.util.List.copyOf(skills),
            MagicPracticeProgress.level(practice),
            practice,
            ProgressionBridge.nextMagicPointXp(player, practice)));
  }

  private static AbilityLoadout visibleLoadout(
      AbilityLoadout loadout, boolean amulet, List<AbilityNetwork.Entry> entries) {
    var wheels = new ArrayList<Preparation>(AbilityLoadout.WHEEL_COUNT);
    for (Preparation wheel : loadout.wheels()) {
      var slots = new ArrayList<>(wheel.slots());
      slots.replaceAll(
          id ->
              (!amulet && AmuletStep.ID.equals(id))
                      || (ExceptionalAccessory.byId(id) != null || DISTRACTION.equals(id))
                          && entries.stream().noneMatch(entry -> entry.id().equals(id))
                  ? ""
                  : id);
      wheels.add(new Preparation(slots, wheel.selected()));
    }
    return new AbilityLoadout(wheels, loadout.activeWheel());
  }

  private static void addExpeditionAbilities(
      ServerPlayer player, List<AbilityNetwork.Entry> entries) {
    for (var accessory : ExceptionalAccessory.values()) {
      if (accessory.equipped(player).isEmpty()) continue;
      if (accessory == ExceptionalAccessory.BREATH) {
        boolean charged = StoredBreath.charged(player);
        entries.add(
            new AbilityNetwork.Entry(
                accessory.id,
                AmuletStep.GROUP,
                0,
                0,
                0,
                charged,
                charged ? "" : "breath_refill",
                0,
                0));
      } else {
        var spell = accessory.spell();
        int cooldown = cooldownTicks(player, spell);
        int mana =
            MagicBonuses.manaCost(spell, 1, player, CastSource.SPELLBOOK, spell.getManaCost(1));
        String reason = "";
        if (!spell.isEnabled()) reason = "disabled";
        else if (cooldown > 0) reason = "cooldown";
        else if (MagicData.getPlayerMagicData(player).getMana() < mana) reason = "mana";
        entries.add(
            new AbilityNetwork.Entry(
                accessory.id, AmuletStep.GROUP, 1, 1, 1, reason.isEmpty(), reason, cooldown, mana));
      }
    }
    if (ExpeditionDiscoveries.knowsDistraction(player)) {
      int cooldown = ExpeditionDiscoveries.distractionCooldownTicks(player);
      entries.add(
          new AbilityNetwork.Entry(
              DISTRACTION,
              DISCOVERY_GROUP,
              0,
              0,
              0,
              cooldown == 0,
              cooldown == 0 ? "" : "cooldown",
              cooldown,
              0));
    }
  }

  private static void reconcileCasting(ServerPlayer player) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    AbilityLoadout prepared = loadout(player);
    boolean focus = SpellKnowledge.hasFocus(player);
    Set<String> skills = SpellKnowledge.unlocked(player);
    if (magic.isCasting() && magic.getCastSource() != CastSource.SCROLL) {
      var spell = SpellKnowledge.spell(magic.getCastingSpellId());
      var accessory = ExceptionalAccessory.bySpell(spell);
      ItemStack castingItem = magic.getPlayerCastingItem();
      boolean allowed =
          accessory != null && accessory.matches(castingItem)
              ? prepared.contains(accessory.id) && accessory.equipped(player) == castingItem
              : focus
                  && prepared.contains(spell.getSpellId())
                  && SpellKnowledge.knowledgeReason(player, spell, skills).isEmpty();
      if (!allowed) Utils.serverSideCancelCast(player);
    }
    for (var recast : magic.getPlayerRecasts().getAllRecasts()) {
      if (recast.getCastSource() != CastSource.SCROLL
          && (!focus
              || !prepared.contains(recast.getSpellId())
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
    MartialTechniques.tick(player);
    StoredBreath.tick(player);
    reconcileInvisibility(player);
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
    if (event.getEntity() instanceof ServerPlayer player) {
      MartialTechniques.reset(player);
      captureCooldowns(player);
    }
  }

  private static void clonePlayer(PlayerEvent.Clone event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      if (event.getOriginal() instanceof ServerPlayer previous) MartialTechniques.reset(previous);
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
      MartialTechniques.reset(player);
      restoreCooldowns(player);
      sync(player, true);
    }
  }

  private static void cooldown(SpellCooldownAddedEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) captureCooldowns(player);
  }

  private static void curioChanged(CurioChangeEvent event) {
    var amulet = ItemRegistry.TELEPORTATION_AMULET.get();
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    if (event.getFrom().is(amulet) || event.getTo().is(amulet)) sync(player, false);
    for (var accessory : ExceptionalAccessory.values()) {
      if (!accessory.matches(event.getFrom()) && !accessory.matches(event.getTo())) continue;
      reconcileCasting(player);
      reconcileInvisibility(player);
      sync(player, false);
      return;
    }
  }

  private static void recordInvisibilitySource(SpellOnCastEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !event.getSpellId().equals(ExceptionalAccessory.INVISIBILITY.spell().getSpellId()))
      return;
    var item = MagicData.getPlayerMagicData(player).getPlayerCastingItem();
    SpellKnowledge.data(player)
        .putBoolean(BORROWED_INVISIBILITY, ExceptionalAccessory.INVISIBILITY.matches(item));
  }

  private static void reconcileInvisibility(ServerPlayer player) {
    var data = SpellKnowledge.data(player);
    if (!data.getBoolean(BORROWED_INVISIBILITY)) return;
    if (ExceptionalAccessory.INVISIBILITY.equipped(player).isEmpty())
      player.removeEffect(MobEffectRegistry.TRUE_INVISIBILITY);
    if (!player.hasEffect(MobEffectRegistry.TRUE_INVISIBILITY)) data.remove(BORROWED_INVISIBILITY);
  }

  private static void tooltip(ItemTooltipEvent event) {
    List<String> lines;
    if (event.getItemStack().getItem() instanceof Scroll) lines = List.of("scroll_hint");
    else if (event.getItemStack().getItem() instanceof SpellBook) lines = List.of("book_hint");
    else if (event.getItemStack().is(ItemRegistry.TELEPORTATION_AMULET.get()))
      lines = List.of("amulet_equip", "amulet_hint");
    else if (HexPrograms.programData(event.getItemStack()) != null)
      lines = List.of("hex_item_hint");
    else return;
    for (String key : lines)
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.abilities." + key)
                  .withStyle(ChatFormatting.GRAY));
  }
}
