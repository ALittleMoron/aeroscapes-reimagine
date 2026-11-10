package dev.aeroscapes.equipment.magic;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.combat.ArmorSpecialties;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.entity.spells.HealingAoe;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

public final class MagicSpecialties {
  private static final String STAFF = "staff_legendary_fourth_with_school_sequence";
  private static final String BOOK = "spellbook_legendary_refund_with_ally_recovery";
  private static final String HEALING_CIRCLE = "irons_spellbooks:healing_circle";
  private static final String HOOD = "magic_armor_head_legendary_ward_with_still_focus";
  private static final String LEGS = "magic_armor_legs_legendary_ward_with_support_step";
  private static final String FEET = "magic_armor_feet_legendary_ward_with_grounded_cast";
  private static final String RING = "ring_legendary_ward_with_after_battle";
  private static final ResourceLocation CASTING_STEP =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "casting_step");
  private static final ResourceLocation MANA_RECOVERY =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "mana_recovery");
  private static final Set<String> SUPPORT =
      Set.of(
          "irons_spellbooks:blessing_of_life",
          "irons_spellbooks:cleanse",
          "irons_spellbooks:cloud_of_regeneration",
          "irons_spellbooks:fortify",
          "irons_spellbooks:greater_heal",
          "irons_spellbooks:heal",
          HEALING_CIRCLE,
          "irons_spellbooks:shield");
  private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
  private static final Map<HealingAoe, CompletedCast> HEALING_AREAS = new WeakHashMap<>();

  private MagicSpecialties() {}

  static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(EventPriority.LOWEST, MagicSpecialties::adjustBookCooldown);
    bus.addListener(EventPriority.LOWEST, MagicSpecialties::castingKnockback);
    bus.addListener(MagicSpecialties::combatDamage);
    bus.addListener(MagicSpecialties::tick);
  }

  public static int castDuration(
      AbstractSpell spell, int spellLevel, ServerPlayer player, CastSource source, int duration) {
    if (duration <= 0
        || spell.getCastType() != CastType.LONG
        || spell.getManaCost(spellLevel) <= 0
        || !eligible(player, source, spell.getSpellId())) {
      return duration;
    }
    var state = state(player);
    var hood = player.getItemBySlot(EquipmentSlot.HEAD);
    state.focusHood = hood;
    state.anchoredFocus =
        ReforgeProfiles.has(hood, HOOD)
            && state.stillHood == hood
            && state.stillTicks >= 20
            && player.onGround();
    double hoodFactor =
        switch (ReforgeProfiles.path(hood)) {
          case "armor_head_poor_with_slow_cast" -> 1.10;
          case "armor_head_fine_with_quick_cast" -> 0.95;
          case "magic_armor_head_epic_mana_with_cast_focus" -> 0.92;
          default -> 1.0;
        };
    double staffFactor =
        source == CastSource.SPELLBOOK ? staffSpeed(spell.getSpellId(), player, state) : 1.0;
    return Math.max(
        1, (int) Math.round(duration * Math.clamp(hoodFactor * staffFactor, 0.75, 1.2)));
  }

  private static double staffSpeed(String spell, ServerPlayer player, State state) {
    var staff = player.getMainHandItem();
    String profile = ReforgeProfiles.path(staff);
    if (SUPPORT.contains(spell) && profile.equals("staff_exceptional_cast_with_support_focus")) {
      return 0.88;
    }
    if (!MagicReforges.offensive(spell)) return 1.0;
    if (profile.equals("staff_poor_with_combat_drag")) return 1.10;
    if (profile.equals("staff_fine_with_combat_tempo")) return 0.94;
    if (profile.equals(STAFF)
        && state.sequenceStaff == staff
        && player.tickCount < state.sequenceUntil
        && player.tickCount >= state.sequenceReadyAt) {
      state.sequenceUntil = 0;
      state.sequenceSchool = null;
      state.sequenceReadyAt = player.tickCount + 240;
      return 0.80;
    }
    return 1.0;
  }

  public static boolean resistInterruption(ServerPlayer player, LivingIncomingDamageEvent event) {
    if (event.getAmount() <= 0.0F
        || event.getAmount() > 3.0F
        || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
    var state = STATES.get(player);
    var hood = player.getItemBySlot(EquipmentSlot.HEAD);
    if (state == null || state.focusHood != hood || player.tickCount < state.interruptReadyAt) {
      return false;
    }
    var magic = MagicData.getPlayerMagicData(player);
    if (!activePaidCast(player, magic) || magic.getCastType() != CastType.LONG) return false;
    int cooldown =
        switch (ReforgeProfiles.path(hood)) {
          case "magic_armor_head_exceptional_resist_with_long_focus" ->
              magic.getCastDuration() >= 20 ? 280 : 0;
          case "magic_armor_head_epic_mana_with_cast_focus" ->
              magic.getCastDuration() >= 20 ? 240 : 0;
          case HOOD -> state.anchoredFocus ? 200 : 0;
          default -> 0;
        };
    if (cooldown == 0 || !ArmorSpecialties.spendDefense(player)) return false;
    state.interruptReadyAt = player.tickCount + cooldown;
    state.focusHood = ItemStack.EMPTY;
    return true;
  }

  static void paidCast(
      SpellOnCastEvent event, ServerPlayer player, ItemStack staff, ItemStack book, String slot) {
    var state = state(player);
    String spell = event.getSpellId();
    int now = player.tickCount;
    if (MagicReforges.offensive(spell)) {
      state.lastCombatAt = now;
      if (event.getCastSource() == CastSource.SPELLBOOK) {
        updateSequence(event, player, staff, state);
        if (ReforgeProfiles.has(staff, "staff_epic_combat_with_combat_economy")) {
          event.setManaCost(Math.max(1, (int) Math.ceil(event.getManaCost() * 0.92)));
        }
      }
    }
    if (SUPPORT.contains(spell)) {
      var leggings = player.getItemBySlot(EquipmentSlot.LEGS);
      if (ReforgeProfiles.has(leggings, LEGS) && now >= state.stepReadyAt) {
        state.stepLegs = leggings;
        state.stepUntil = now + 40;
        state.stepReadyAt = now + 160;
      }
    }
    state.completed = new CompletedCast(spell, event.getCastSource(), book, slot, now);
  }

  private static void updateSequence(
      SpellOnCastEvent event, ServerPlayer player, ItemStack staff, State state) {
    int now = player.tickCount;
    if (!ReforgeProfiles.has(staff, STAFF) || now < state.sequenceReadyAt) return;
    if (state.sequenceStaff != staff) {
      state.sequenceStaff = staff;
      state.sequenceSchool = null;
      state.sequenceUntil = 0;
    }
    if (now < state.sequenceUntil) return;
    var school = event.getSchoolType().getId();
    if (state.sequenceSchool != null
        && !state.sequenceSchool.equals(school)
        && now - state.sequenceLastCast <= 200) {
      state.sequenceUntil = now + 200;
    }
    state.sequenceSchool = school;
    state.sequenceLastCast = now;
  }

  private static void adjustBookCooldown(SpellCooldownAddedEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.getEffectiveCooldown() <= 0
        || event.getCastSource() != CastSource.SPELLBOOK) return;
    String spell = event.getSpell().getSpellId();
    var state = STATES.get(player);
    if (state == null || !state.completed.matches(player, event.getCastSource(), spell)) return;
    double multiplier =
        switch (ReforgeProfiles.path(state.completed.book())) {
          case "spellbook_poor_with_combat_recovery" -> MagicReforges.offensive(spell) ? 1.08 : 1.0;
          case "spellbook_fine_with_support_recovery" -> SUPPORT.contains(spell) ? 0.95 : 1.0;
          case "spellbook_exceptional_cooldown_with_support_recovery" ->
              SUPPORT.contains(spell) ? 0.90 : 1.0;
          case "spellbook_epic_mana_with_support_recovery" -> SUPPORT.contains(spell) ? 0.88 : 1.0;
          default -> 1.0;
        };
    state.completed = CompletedCast.NONE;
    if (multiplier != 1.0) {
      event.setEffectiveCooldown(
          Math.max(1, (int) Math.round(event.getEffectiveCooldown() * multiplier)));
    }
  }

  public static void rememberHealingCircle(
      HealingAoe area, ServerPlayer caster, CastSource source) {
    var state = STATES.get(caster);
    if (state != null
        && source == CastSource.SPELLBOOK
        && state.completed.matches(caster, source, HEALING_CIRCLE)
        && ReforgeProfiles.has(state.completed.book(), BOOK)) {
      HEALING_AREAS.put(area, state.completed);
    }
  }

  public static void healedAlly(HealingAoe area, LivingEntity target, float previousHealth) {
    if (!(area.getOwner() instanceof ServerPlayer player)
        || target == player
        || target.getHealth() <= previousHealth) return;
    var cast = HEALING_AREAS.get(area);
    var state = STATES.get(player);
    if (cast == null
        || state == null
        || !cast.sameBook(player)
        || !ReforgeProfiles.has(cast.book(), BOOK)
        || player.tickCount < state.allyReadyAt) return;
    var cooldowns = MagicData.getPlayerMagicData(player).getPlayerCooldowns();
    var entry =
        cooldowns.getSpellCooldowns().entrySet().stream()
            .filter(value -> !value.getKey().equals(HEALING_CIRCLE))
            .filter(
                value ->
                    SUPPORT.contains(value.getKey()) && value.getValue().getCooldownRemaining() > 1)
            .sorted(
                Comparator.comparingInt(
                        (Map.Entry<
                                    String,
                                    io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance>
                                value) -> value.getValue().getCooldownRemaining())
                    .reversed()
                    .thenComparing(Map.Entry::getKey))
            .findFirst()
            .orElse(null);
    if (entry == null) return;
    var cooldown = entry.getValue();
    cooldown.decrementBy(Math.min(40, Math.max(1, cooldown.getCooldownRemaining() / 5)));
    cooldowns.syncToPlayer(player);
    state.allyReadyAt = player.tickCount + 240;
    HEALING_AREAS.remove(area);
  }

  private static boolean eligible(ServerPlayer player, CastSource source, String spell) {
    var magic = MagicData.getPlayerMagicData(player);
    return !player.isCreative()
        && source != null
        && source.consumesMana()
        && magic.getMana() > 0.0F
        && !magic.getPlayerRecasts().hasRecastForSpell(spell);
  }

  private static boolean activePaidCast(ServerPlayer player, MagicData magic) {
    return magic.isCasting()
        && magic.getCastingSpell() != null
        && magic.getCastingSpell().getSpell().getManaCost(magic.getCastingSpellLevel()) > 0
        && eligible(player, magic.getCastSource(), magic.getCastingSpellId());
  }

  private static void castingKnockback(LivingKnockBackEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || event.getStrength() <= 0) return;
    var magic = MagicData.getPlayerMagicData(player);
    if (!activePaidCast(player, magic)) return;
    String profile = ArmorSpecialties.profile(player, EquipmentSlot.FEET);
    float factor =
        switch (profile) {
          case "armor_feet_poor_with_casting_sway" -> 1.12F;
          case "armor_feet_fine_with_casting_brace" -> 0.90F;
          case "magic_armor_feet_exceptional_resist_with_ground_brace" ->
              player.onGround() ? 0.82F : 1.0F;
          case "magic_armor_feet_epic_mana_with_casting_brace" -> 0.84F;
          default -> 1.0F;
        };
    if (profile.equals(FEET) && player.onGround() && event.getStrength() >= 0.35F) {
      var state = state(player);
      if (player.tickCount >= state.feetReadyAt && ArmorSpecialties.spendKnockback(player)) {
        factor = 0.35F;
        state.feetReadyAt = player.tickCount + 200;
      }
    }
    event.setStrength(event.getStrength() * factor);
  }

  private static void combatDamage(LivingDamageEvent.Post event) {
    if (event.getNewDamage() <= 0
        || !(event.getSource().getEntity() instanceof LivingEntity attacker)
        || attacker == event.getEntity()
        || attacker.isAlliedTo(event.getEntity())) return;
    if (event.getEntity() instanceof ServerPlayer player)
      state(player).lastCombatAt = player.tickCount;
    if (attacker instanceof ServerPlayer player) state(player).lastCombatAt = player.tickCount;
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var state = state(player);
    var magic = MagicData.getPlayerMagicData(player);
    trackHood(player, state);
    if (state.sequenceStaff != player.getMainHandItem()
        || !ReforgeProfiles.has(state.sequenceStaff, STAFF)) {
      state.sequenceStaff = ItemStack.EMPTY;
      state.sequenceSchool = null;
      state.sequenceUntil = 0;
    }
    double step = movementBonus(player, state, magic);
    setModifier(
        player,
        Attributes.MOVEMENT_SPEED,
        CASTING_STEP,
        step,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    double mana = ringBonus(player, state, magic);
    setModifier(
        player,
        AttributeRegistry.MANA_REGEN,
        MANA_RECOVERY,
        mana,
        AttributeModifier.Operation.ADD_VALUE);
  }

  private static void trackHood(ServerPlayer player, State state) {
    var hood = player.getItemBySlot(EquipmentSlot.HEAD);
    if (state.stillHood == hood
        && ReforgeProfiles.has(hood, HOOD)
        && player.onGround()
        && !player.isPassenger()
        && player.position().distanceToSqr(state.position) < 0.001) {
      state.stillTicks = Math.min(state.stillTicks + 1, 20);
    } else {
      state.stillTicks = 0;
    }
    state.stillHood = hood;
    state.position = player.position();
  }

  private static double movementBonus(ServerPlayer player, State state, MagicData magic) {
    var legs = player.getItemBySlot(EquipmentSlot.LEGS);
    if (player.tickCount < state.stepUntil
        && state.stepLegs == legs
        && ReforgeProfiles.has(legs, LEGS)) {
      return 0.12;
    }
    if (!activePaidCast(player, magic)) return 0.0;
    return switch (ReforgeProfiles.path(legs)) {
      case "armor_legs_poor_with_casting_drag" -> -0.10;
      case "armor_legs_fine_with_casting_step" -> 0.05;
      case "magic_armor_legs_exceptional_mana_with_long_step" ->
          magic.getCastType() == CastType.LONG && magic.getCastDuration() >= 20 ? 0.10 : 0.0;
      case "magic_armor_legs_epic_mana_with_casting_step" -> 0.08;
      default -> 0.0;
    };
  }

  private static double ringBonus(ServerPlayer player, State state, MagicData magic) {
    var ring =
        CuriosApi.getCuriosInventory(player)
            .flatMap(
                handler ->
                    handler.findFirstCurio(
                        stack ->
                            switch (ReforgeProfiles.path(stack)) {
                              case RING,
                                  "ring_exceptional_regen_with_quiet_recovery",
                                  "ring_epic_mana_with_battle_reserve" ->
                                  true;
                              default -> false;
                            },
                        Curios.RING_SLOT))
            .map(SlotResult::stack)
            .orElse(ItemStack.EMPTY);
    int now = player.tickCount;
    boolean fighting = now - state.lastCombatAt < 160;
    float mana = magic.getMana();
    float maximum = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
    if (ReforgeProfiles.has(ring, RING)
        && ring == state.ring
        && state.wasFighting
        && !fighting
        && now >= state.ringReadyAt
        && mana < maximum / 3.0F) {
      state.ringUntil = now + 80;
      state.ringReadyAt = now + 400;
    }
    if (ring != state.ring || fighting) state.ringUntil = 0;
    state.ring = ring;
    state.wasFighting = fighting;
    return switch (ReforgeProfiles.path(ring)) {
      case RING -> now < state.ringUntil ? 0.14 : 0.0;
      case "ring_exceptional_regen_with_quiet_recovery" -> fighting ? 0.0 : 0.05;
      case "ring_epic_mana_with_battle_reserve" -> fighting && mana < maximum / 2.0F ? 0.07 : 0.0;
      default -> 0.0;
    };
  }

  private static void setModifier(
      ServerPlayer player,
      Holder<Attribute> attribute,
      ResourceLocation id,
      double amount,
      AttributeModifier.Operation operation) {
    var instance = player.getAttribute(attribute);
    if (instance == null) return;
    var previous = instance.getModifier(id);
    if (previous != null && previous.amount() != amount) instance.removeModifier(id);
    if (amount != 0.0 && !instance.hasModifier(id)) {
      instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }
  }

  private static State state(ServerPlayer player) {
    return STATES.computeIfAbsent(player, ignored -> new State());
  }

  private record CompletedCast(
      String spell, CastSource source, ItemStack book, String slot, int tick) {
    private static final CompletedCast NONE =
        new CompletedCast("", CastSource.NONE, ItemStack.EMPTY, "", -1);

    private boolean sameBook(ServerPlayer player) {
      return !book.isEmpty() && MagicReforges.castBook(player, slot) == book;
    }

    private boolean matches(ServerPlayer player, CastSource castSource, String castSpell) {
      return tick == player.tickCount
          && source == castSource
          && spell.equals(castSpell)
          && sameBook(player);
    }
  }

  private static final class State {
    private CompletedCast completed = CompletedCast.NONE;
    private Vec3 position = Vec3.ZERO;
    private ItemStack focusHood = ItemStack.EMPTY;
    private ItemStack stillHood = ItemStack.EMPTY;
    private ItemStack sequenceStaff = ItemStack.EMPTY;
    private ItemStack stepLegs = ItemStack.EMPTY;
    private ItemStack ring = ItemStack.EMPTY;
    private ResourceLocation sequenceSchool;
    private int sequenceLastCast;
    private int sequenceReadyAt;
    private int sequenceUntil;
    private int stepReadyAt;
    private int stepUntil;
    private int ringReadyAt;
    private int ringUntil;
    private int feetReadyAt;
    private int allyReadyAt;
    private int interruptReadyAt;
    private int lastCombatAt = -1000;
    private int stillTicks;
    private boolean anchoredFocus;
    private boolean wasFighting;
  }
}
