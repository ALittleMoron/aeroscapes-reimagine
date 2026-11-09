package dev.aeroscapes.equipment.magic;

import dev.aeroscapes.equipment.ReforgeProfiles;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

public final class MagicReforges {
  private static final String STAFF = "staff_legendary_fourth";
  private static final String BOOK = "spellbook_legendary_refund";
  private static final String RING = "ring_legendary_ward";
  private static final int STAFF_COOLDOWN = 240;
  private static final int REFUND_COOLDOWN = 240;
  private static final int REFUND_WINDOW = 240;
  private static final int WARD_WINDOW = 100;
  private static final int WARD_COOLDOWN = 400;
  private static final EquipmentSlot[] ARMOR_SLOTS = {
    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
  };

  // A spell's school does not distinguish attacks from travel, healing, or utility.
  private static final Set<String> OFFENSIVE_SPELLS =
      Set.of(
          "acupuncture",
          "acid_orb",
          "arrow_volley",
          "ball_lightning",
          "black_hole",
          "blaze_storm",
          "blizzard",
          "blight",
          "blood_needles",
          "blood_slash",
          "chain_creeper",
          "chain_lightning",
          "cone_of_cold",
          "devour",
          "divine_smite",
          "dragon_breath",
          "earthquake",
          "electrocute",
          "eldritch_blast",
          "fang_strike",
          "fang_swirl",
          "fang_ward",
          "fire_arrow",
          "fire_breath",
          "fireball",
          "firebolt",
          "firecracker",
          "firefly_swarm",
          "flaming_barrage",
          "flaming_strike",
          "frostbite",
          "frostwave",
          "gravity_fissure",
          "guiding_bolt",
          "heat_surge",
          "ice_spikes",
          "ice_tomb",
          "icicle",
          "lightning_bolt",
          "lightning_lance",
          "lob_creeper",
          "magic_arrow",
          "magic_missile",
          "magma_bomb",
          "poison_arrow",
          "poison_breath",
          "poison_splash",
          "raise_hell",
          "ray_of_frost",
          "ray_of_siphoning",
          "scorch",
          "sculk_tentacles",
          "shadow_slash",
          "shockwave",
          "sonic_boom",
          "spectral_hammer",
          "starfall",
          "stomp",
          "sunbeam",
          "thunderstorm",
          "volt_strike",
          "wall_of_fire",
          "wither_skull");
  private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

  private MagicReforges() {}

  public static void register() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener(EventPriority.LOWEST, MagicReforges::prepareCast);
    bus.addListener(EventPriority.LOWEST, MagicReforges::completeCast);
    bus.addListener(EventPriority.LOWEST, MagicReforges::refundOnKill);
    bus.addListener(EventPriority.LOWEST, MagicReforges::absorbHit);
    bus.addListener(MagicReforges::tick);
    bus.addListener(CurioReforgeAttributes::append);
  }

  private static void prepareCast(SpellPreCastEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
    var state = STATES.computeIfAbsent(player, ignored -> new State());
    state.prepared =
        new PreparedCast(
            event.getSpellId(),
            event.getCastSource(),
            player.getMainHandItem(),
            player.getOffhandItem(),
            slottedBook(player));
  }

  private static void completeCast(SpellOnCastEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var state = STATES.get(player);
    if (state == null) return;
    String spell = event.getSpellId();
    var prepared = state.prepared;
    state.prepared = null;
    var magic = MagicData.getPlayerMagicData(player);
    if (prepared == null
        || !prepared.spell().equals(spell)
        || prepared.source() != event.getCastSource()
        || !event.getCastSource().consumesMana()
        || event.getManaCost() <= 0
        || magic.getMana() <= 0
        || player.isCreative()
        || magic.getPlayerRecasts().hasRecastForSpell(spell)) {
      if (!event.getCastSource().consumesMana()) state.refunds.remove(spell);
      return;
    }

    int now = player.tickCount;
    applyStaffDiscount(event, player, state, prepared, now);
    String bookSlot = magic.getCastingEquipmentSlot();
    var book = castBook(player, bookSlot);
    if (ReforgeProfiles.has(book, BOOK) && book == prepared.bookAt(bookSlot)) {
      state.refunds.put(spell, new PendingRefund(book, bookSlot, now + REFUND_WINDOW));
    } else {
      state.refunds.remove(spell);
    }

    if (now >= state.wardReadyAt) {
      state.wardArmor = wardArmor(player);
      state.wardRing = wardRing(player);
      if (!state.wardArmor.isEmpty() || !state.wardRing.isEmpty()) {
        state.wardUntil = now + WARD_WINDOW;
        state.wardReadyAt = now + WARD_COOLDOWN;
      }
    }
  }

  private static void applyStaffDiscount(
      SpellOnCastEvent event, ServerPlayer player, State state, PreparedCast prepared, int now) {
    var staff = player.getMainHandItem();
    if (staff != prepared.mainHand() || !ReforgeProfiles.has(staff, STAFF)) return;
    String namespace = "irons_spellbooks:";
    if (!event.getSpellId().startsWith(namespace)
        || !OFFENSIVE_SPELLS.contains(event.getSpellId().substring(namespace.length()))) return;
    if (state.staff != staff) {
      state.staff = staff;
      state.staffCharges = 0;
    }
    if (state.staffCharges < 3) {
      state.staffCharges++;
    } else if (now >= state.staffReadyAt) {
      int cost = event.getManaCost();
      event.setManaCost(Math.max(1, (int) Math.ceil(cost * 0.75)));
      state.staffCharges = 0;
      state.staffReadyAt = now + STAFF_COOLDOWN;
    }
  }

  private static void refundOnKill(LivingDeathEvent event) {
    if (!(event.getSource() instanceof SpellDamageSource source)
        || !(source.getEntity() instanceof ServerPlayer player)
        || event.getEntity().isAlliedTo(player)
        || (!(event.getEntity() instanceof Enemy)
            && event.getEntity().getType().getCategory() != MobCategory.MONSTER)) return;
    var state = STATES.get(player);
    if (state == null || player.tickCount < state.refundReadyAt) return;
    var refund = state.refunds.remove(source.spell().getSpellId());
    if (refund == null || !refund.isActive(player)) return;

    var magic = MagicData.getPlayerMagicData(player);
    float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
    if (magic.getMana() >= maxMana) return;
    state.refundReadyAt = player.tickCount + REFUND_COOLDOWN;
    magic.setMana(Math.min(magic.getMana() + 8, maxMana));
    PacketDistributor.sendToPlayer(player, new SyncManaPacket(magic));
  }

  private static void absorbHit(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.getNewDamage() <= 0
        || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
    var state = STATES.get(player);
    if (state == null || player.tickCount >= state.wardUntil) return;
    boolean ringWorks =
        (event.getSource() instanceof SpellDamageSource
                || event.getSource().is(Tags.DamageTypes.IS_MAGIC))
            && !state.wardRing.isEmpty()
            && wardRing(player) == state.wardRing;
    if (ringWorks || (!state.wardArmor.isEmpty() && wardArmor(player) == state.wardArmor)) {
      event.setNewDamage(event.getNewDamage() * 0.75F);
      state.wardUntil = 0;
    }
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var state = STATES.get(player);
    if (state == null) return;
    if (state.staff != player.getMainHandItem() || !ReforgeProfiles.has(state.staff, STAFF)) {
      state.staff = ItemStack.EMPTY;
      state.staffCharges = 0;
    }
    state.refunds.values().removeIf(refund -> !refund.isActive(player));
    if (player.tickCount >= state.wardUntil) {
      state.wardArmor = ItemStack.EMPTY;
      state.wardRing = ItemStack.EMPTY;
    } else {
      if (!state.wardArmor.isEmpty() && wardArmor(player) != state.wardArmor) {
        state.wardArmor = ItemStack.EMPTY;
      }
      if (!state.wardRing.isEmpty() && wardRing(player) != state.wardRing) {
        state.wardRing = ItemStack.EMPTY;
      }
    }
  }

  private static ItemStack wardArmor(ServerPlayer player) {
    for (var slot : ARMOR_SLOTS) {
      var stack = player.getItemBySlot(slot);
      if (ReforgeProfiles.has(stack, "magic_armor_" + slot.getName() + "_legendary_ward"))
        return stack;
    }
    return ItemStack.EMPTY;
  }

  private static ItemStack wardRing(ServerPlayer player) {
    return CuriosApi.getCuriosInventory(player)
        .flatMap(
            handler ->
                handler.findFirstCurio(stack -> ReforgeProfiles.has(stack, RING), Curios.RING_SLOT))
        .map(SlotResult::stack)
        .orElse(ItemStack.EMPTY);
  }

  private static ItemStack slottedBook(ServerPlayer player) {
    var stack = Utils.getPlayerSpellbookStack(player);
    return stack == null ? ItemStack.EMPTY : stack;
  }

  private static ItemStack castBook(ServerPlayer player, String slot) {
    if (Curios.SPELLBOOK_SLOT.equals(slot)) return slottedBook(player);
    if (EquipmentSlot.MAINHAND.getName().equals(slot)) return player.getMainHandItem();
    if (EquipmentSlot.OFFHAND.getName().equals(slot)) return player.getOffhandItem();
    return ItemStack.EMPTY;
  }

  private static final class State {
    private final Map<String, PendingRefund> refunds = new HashMap<>();
    private PreparedCast prepared;
    private ItemStack staff = ItemStack.EMPTY;
    private ItemStack wardArmor = ItemStack.EMPTY;
    private ItemStack wardRing = ItemStack.EMPTY;
    private int staffCharges;
    private int staffReadyAt;
    private int refundReadyAt;
    private int wardUntil;
    private int wardReadyAt;
  }

  private record PreparedCast(
      String spell,
      CastSource source,
      ItemStack mainHand,
      ItemStack offHand,
      ItemStack slottedBook) {
    private ItemStack bookAt(String slot) {
      if (Curios.SPELLBOOK_SLOT.equals(slot)) return slottedBook;
      if (EquipmentSlot.MAINHAND.getName().equals(slot)) return mainHand;
      if (EquipmentSlot.OFFHAND.getName().equals(slot)) return offHand;
      return ItemStack.EMPTY;
    }
  }

  private record PendingRefund(ItemStack book, String slot, int until) {
    private boolean isActive(ServerPlayer player) {
      return player.tickCount < until
          && castBook(player, slot) == book
          && ReforgeProfiles.has(book, BOOK);
    }
  }
}
