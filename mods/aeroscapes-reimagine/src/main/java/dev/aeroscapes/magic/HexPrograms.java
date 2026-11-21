package dev.aeroscapes.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * A preparation slot identifies one programmed item, independently of its current charge or hand.
 */
public final class HexPrograms {
  static final String GROUP = "hexcasting:programs";
  private static final String IDENTITY = "aeroscapes_reimagine:hex_program";
  private static final Set<ResourceLocation> HOLDERS =
      Set.of(
          ResourceLocation.parse("hexcasting:cypher"),
          ResourceLocation.parse("hexcasting:trinket"),
          ResourceLocation.parse("hexcasting:artifact"));

  private HexPrograms() {}

  @Nullable
  public static ResourceLocation itemType(String reference) {
    int separator = reference.indexOf('/');
    if (!reference.startsWith("hexcasting:")
        || separator < 0
        || reference.length() != separator + 37) return null;
    var type = ResourceLocation.tryParse(reference.substring(0, separator));
    if (type == null || !HOLDERS.contains(type)) return null;
    String uuid = reference.substring(separator + 1);
    try {
      return UUID.fromString(uuid).toString().equals(uuid) ? type : null;
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static UUID identify(ItemStack stack) {
    UUID id = UUID.randomUUID();
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(IDENTITY, id));
    return id;
  }

  @Nullable
  static CompoundTag programData(ItemStack stack) {
    if (stack.isEmpty() || !HOLDERS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
      return null;
    var data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) return null;
    var tag = data.copyTag();
    return tag.get("patterns") instanceof ListTag patterns
            && (patterns.isEmpty() || patterns.getElementType() == Tag.TAG_COMPOUND)
        ? tag
        : null;
  }

  static Map<String, Integer> inventory(ServerPlayer player) {
    var inventory = player.getInventory();
    var locations = new HashMap<UUID, List<Integer>>();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      if (slot >= inventory.items.size() && slot != Inventory.SLOT_OFFHAND) continue;
      var stack = inventory.getItem(slot);
      var tag = programData(stack);
      if (tag == null) continue;
      UUID identity = tag.hasUUID(IDENTITY) ? tag.getUUID(IDENTITY) : identify(stack);
      locations.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(slot);
    }
    var programs = new LinkedHashMap<String, Integer>();
    locations.forEach(
        (id, slots) -> {
          for (int slot : slots) {
            var stack = inventory.getItem(slot);
            // Creative copies can share every component. Require a fresh assignment for both.
            UUID identity = slots.size() == 1 ? id : identify(stack);
            programs.put(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "/" + identity, slot);
          }
        });
    return programs;
  }

  private static String reason(ServerPlayer player, int slot) {
    if (slot < 0) return "hex_missing";
    if (!Inventory.isHotbarSlot(slot) && slot != Inventory.SLOT_OFFHAND) return "hex_stowed";
    if (!player.isAlive() || player.isSpectator()) return "state";
    if (player.isUsingItem()) return "hex_busy";
    var stack = player.getInventory().getItem(slot);
    if (!stack.isItemEnabled(player.level().enabledFeatures())) return "disabled";
    return player.getCooldowns().isOnCooldown(stack.getItem()) ? "cooldown" : "";
  }

  static void catalogue(
      ServerPlayer player, Preparation prepared, List<AbilityNetwork.Entry> entries) {
    var programs = inventory(player);
    for (String id : prepared.slots()) {
      if (itemType(id) != null) programs.putIfAbsent(id, -1);
    }
    programs.forEach(
        (id, slot) -> {
          var customName =
              slot < 0 ? null : player.getInventory().getItem(slot).get(DataComponents.CUSTOM_NAME);
          String reason = reason(player, slot);
          entries.add(
              new AbilityNetwork.Entry(
                  id,
                  GROUP,
                  0,
                  0,
                  0,
                  reason.isEmpty(),
                  reason,
                  0,
                  0,
                  new AbilityNetwork.Program(
                      slot, customName == null ? "" : customName.getString(128))));
        });
  }

  static void cast(ServerPlayer player, String id) {
    int slot = inventory(player).getOrDefault(id, -1);
    String reason = reason(player, slot);
    if (!reason.isEmpty()) {
      SpellKnowledge.message(player, reason);
      return;
    }
    var hand =
        slot == Inventory.SLOT_OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    if (hand == InteractionHand.MAIN_HAND && slot != player.getInventory().selected) {
      player.getInventory().selected = slot;
      player.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }
    player.resetLastActionTime();
    var result =
        player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
    if (result.shouldSwing()) player.swing(hand, true);
  }
}
