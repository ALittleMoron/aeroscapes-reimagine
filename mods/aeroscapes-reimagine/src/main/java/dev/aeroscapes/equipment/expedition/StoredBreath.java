package dev.aeroscapes.equipment.expedition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class StoredBreath {
  private static final String CHARGED = "aeroscapes_reimagine:stored_breath_charged";
  private static final String SPENT = "aeroscapes_reimagine:stored_breath_spent";
  private static final String REFILL = "aeroscapes_reimagine:stored_breath_refill";
  private static final int CHARGE_TIME = 40;
  private static final int AIR_TICKS = 80;
  private static final double RANGE = 4.0;

  private StoredBreath() {}

  public static boolean charged(ServerPlayer wearer) {
    return !spent(wearer) && hasCharge(ExceptionalAccessory.BREATH.equipped(wearer));
  }

  private static boolean hasCharge(ItemStack necklace) {
    var itemData = necklace.get(DataComponents.CUSTOM_DATA);
    return itemData != null && itemData.contains(CHARGED) && itemData.copyTag().getBoolean(CHARGED);
  }

  public static void tick(ServerPlayer wearer) {
    var data = wearer.getPersistentData();
    var necklace = ExceptionalAccessory.BREATH.equipped(wearer);
    if (wearer.getEyeInFluidType() != NeoForgeMod.EMPTY_TYPE.value()
        || necklace.isEmpty()
        || wearer.isSpectator()
        || hasCharge(necklace) && !spent(wearer)) {
      data.remove(REFILL);
      return;
    }
    int ticks = data.getInt(REFILL) + 1;
    if (ticks >= CHARGE_TIME) {
      data.remove(REFILL);
      data.getCompound(Player.PERSISTED_NBT_TAG).remove(SPENT);
      CustomData.update(DataComponents.CUSTOM_DATA, necklace, tag -> tag.putBoolean(CHARGED, true));
    } else {
      data.putInt(REFILL, ticks);
    }
  }

  public static void use(ServerPlayer wearer) {
    if (!wearer.isAlive() || wearer.isSpectator()) return;
    var necklace = ExceptionalAccessory.BREATH.equipped(wearer);
    if (necklace.isEmpty()) {
      message(wearer, "equipment_required");
      return;
    }
    if (!hasCharge(necklace) || spent(wearer)) {
      message(wearer, "breath_refill");
      return;
    }
    var hit =
        ProjectileUtil.getHitResultOnViewVector(
            wearer, entity -> entity instanceof ServerPlayer ally && canReceive(ally), RANGE);
    var target =
        hit instanceof EntityHitResult entityHit ? (ServerPlayer) entityHit.getEntity() : wearer;
    if (!canReceive(target)) {
      message(wearer, "breath_unneeded");
      return;
    }
    target.setAirSupply(Math.min(target.getMaxAirSupply(), target.getAirSupply() + AIR_TICKS));
    CustomData.update(DataComponents.CUSTOM_DATA, necklace, tag -> tag.remove(CHARGED));
    var data = wearer.getPersistentData();
    var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
    persisted.putBoolean(SPENT, true);
    data.put(Player.PERSISTED_NBT_TAG, persisted);
    data.remove(REFILL);
    if (target == wearer) {
      wearer.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.abilities.breath_used"), true);
      return;
    }
    wearer.displayClientMessage(
        Component.translatable(
            "aeroscapes_reimagine.abilities.breath_given", target.getDisplayName()),
        true);
    target.displayClientMessage(
        Component.translatable(
            "aeroscapes_reimagine.abilities.breath_received", wearer.getDisplayName()),
        true);
  }

  private static boolean spent(ServerPlayer wearer) {
    return wearer.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(SPENT);
  }

  private static boolean canReceive(ServerPlayer player) {
    return player.isAlive()
        && !player.isSpectator()
        && player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value())
        && player.getAirSupply() < player.getMaxAirSupply();
  }

  private static void message(ServerPlayer player, String reason) {
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.abilities.reason." + reason), true);
  }
}
