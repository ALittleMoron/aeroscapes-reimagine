package dev.aeroscapes.races;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.progression.ProfessionDiscipline;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class FieldRepair {
  private static final String TALENT = "field_repair";
  static final int USE_TICKS = 40;
  private static final double MOVEMENT_LIMIT_SQUARED = 0.20 * 0.20;
  private static final WeakHashMap<ServerPlayer, Repair> ACTIVE = new WeakHashMap<>();

  private record Material(int slot, ItemStack stack) {}

  private static final class Repair {
    private final ServerLevel level;
    private final Vec3 origin;
    private final InteractionHand kitHand;
    private final ItemStack kit;
    private final ItemStack target;
    private final Material material;
    private int ticks;

    private Repair(
        ServerPlayer player,
        InteractionHand kitHand,
        ItemStack kit,
        ItemStack target,
        Material material) {
      level = player.serverLevel();
      origin = player.position();
      this.kitHand = kitHand;
      this.kit = kit;
      this.target = target;
      this.material = material;
    }
  }

  private FieldRepair() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FieldRepair::damaged);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FieldRepair::attacking);
    NeoForge.EVENT_BUS.addListener(FieldRepair::reserveOffhandKit);
    NeoForge.EVENT_BUS.addListener(FieldRepair::tick);
    NeoForge.EVENT_BUS.addListener(FieldRepair::logout);
  }

  static boolean begin(ServerPlayer player, InteractionHand hand) {
    if (!validPlayer(player) || player.isUsingItem()) return refuse(player, "state");
    if (!learned(player)) return refuse(player, "field_repair_unlearned");
    var kit = player.getItemInHand(hand);
    var target = player.getItemInHand(other(hand));
    if (!kit.is(FieldRepairKit.ITEM.get()) || !repairable(target))
      return refuse(player, "field_repair_target");
    var material = findMaterial(player.getInventory(), target, kit);
    if (material == null) return refuse(player, "field_repair_material");
    player.startUsingItem(hand);
    if (!player.isUsingItem() || player.getUseItem() != kit) return false;
    ACTIVE.put(player, new Repair(player, hand, kit, target, material));
    return true;
  }

  static void useTick(ServerPlayer player, ItemStack kit) {
    var attempt = ACTIVE.get(player);
    if (attempt == null) return;
    if (!valid(player, attempt, kit)) interrupt(player);
    else attempt.ticks++;
  }

  static void finish(ServerPlayer player, ItemStack kit) {
    var attempt = ACTIVE.remove(player);
    if (attempt == null) return;
    if (attempt.ticks < USE_TICKS || !valid(player, attempt, kit)) {
      refuse(player, "field_repair_interrupted");
      return;
    }
    var target = attempt.target;
    int repair = Math.min(target.getDamageValue(), Math.max(1, target.getMaxDamage() / 8));
    target.setDamageValue(target.getDamageValue() - repair);
    attempt.material.stack().shrink(1);
    kit.shrink(1);
    player.getInventory().setChanged();
    player.containerMenu.broadcastChanges();
    player.displayClientMessage(
        Component.translatable(
            "aeroscapes_reimagine.field_repair.completed", repair, target.getHoverName()),
        true);
  }

  static void released(ServerPlayer player) {
    if (ACTIVE.remove(player) != null) refuse(player, "field_repair_interrupted");
  }

  private static boolean valid(ServerPlayer player, Repair attempt, ItemStack kit) {
    var material = attempt.material;
    return validPlayer(player)
        && player.serverLevel() == attempt.level
        && player.position().distanceToSqr(attempt.origin) <= MOVEMENT_LIMIT_SQUARED
        && player.isUsingItem()
        && player.getUsedItemHand() == attempt.kitHand
        && player.getUseItem() == kit
        && kit == attempt.kit
        && player.getItemInHand(attempt.kitHand) == kit
        && kit.is(FieldRepairKit.ITEM.get())
        && player.getItemInHand(other(attempt.kitHand)) == attempt.target
        && repairable(attempt.target)
        && player.getInventory().getItem(material.slot()) == material.stack()
        && matching(attempt.target, material.stack())
        && learned(player);
  }

  private static boolean validPlayer(ServerPlayer player) {
    return PracticeActions.personal(player)
        && player.isAlive()
        && !player.isSleeping()
        && !player.isFallFlying();
  }

  private static boolean learned(ServerPlayer player) {
    return ProfessionProgression.hasTalent(player, ProfessionDiscipline.SMITHING, TALENT);
  }

  private static boolean repairable(ItemStack target) {
    return target.getCount() == 1 && target.isDamaged() && target.getItem().isRepairable(target);
  }

  private static boolean matching(ItemStack target, ItemStack input) {
    return !input.isEmpty()
        && !input.isDamageableItem()
        && !input.is(target.getItem())
        && target.getItem().isValidRepairItem(target, input);
  }

  @Nullable
  private static Material findMaterial(Inventory inventory, ItemStack target, ItemStack kit) {
    for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
      var stack = inventory.getItem(slot);
      if (stack != kit && stack != target && matching(target, stack))
        return new Material(slot, stack);
    }
    return null;
  }

  static InteractionHand other(InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
  }

  private static void damaged(LivingDamageEvent.Post event) {
    if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player)
      interrupt(player);
  }

  private static void attacking(AttackEntityEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) interrupt(player);
  }

  private static void reserveOffhandKit(PlayerInteractEvent.RightClickItem event) {
    var player = event.getEntity();
    if (event.getHand() == InteractionHand.MAIN_HAND
        && player.getOffhandItem().is(FieldRepairKit.ITEM.get())
        && repairable(player.getMainHandItem())
        && ProfessionProgression.hasTalent(player, ProfessionDiscipline.SMITHING, TALENT)) {
      // Air-use tries the main hand first; armor would otherwise equip before trying the kit.
      event.setCancellationResult(InteractionResult.PASS);
      event.setCanceled(true);
    }
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player
        && !player.isUsingItem()
        && ACTIVE.containsKey(player)) released(player);
  }

  private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) ACTIVE.remove(player);
  }

  private static void interrupt(ServerPlayer player) {
    if (ACTIVE.remove(player) != null) {
      player.stopUsingItem();
      refuse(player, "field_repair_interrupted");
    }
  }

  private static boolean refuse(ServerPlayer player, String reason) {
    SpellKnowledge.message(player, reason);
    return false;
  }
}
