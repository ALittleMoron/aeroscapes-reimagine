package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class HexProgramsRegression {
  private HexProgramsRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    UUID identity = UUID.randomUUID();
    CompoundTag saved;
    String reference;
    try (var session =
        TestShipPlayerSession.create(level, identity, "HexPrograms", position, null)) {
      var player = session.player;
      AbilitiesRegression.initialize(player);
      player.setGameMode(GameType.SURVIVAL);
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      MagicData.getPlayerMagicData(player).setMana(0);
      long practice = MagicPractice.credit(player);
      var inventory = player.getInventory();
      var trinket = program("trinket", 3000);
      trinket.set(DataComponents.CUSTOM_NAME, Component.literal("Dismiss"));
      var other = program("trinket", 3000);
      var cypher = program("cypher", 1000);
      var artifact = program("artifact", 0);
      var blank = item("artifact");
      inventory.setItem(0, new ItemStack(Items.STICK));
      inventory.setItem(2, other);
      inventory.setItem(6, trinket);
      inventory.setItem(Inventory.SLOT_OFFHAND, cypher);
      inventory.setItem(22, artifact);
      inventory.setItem(23, blank);
      PreparedAbilities.sync(player, true);
      var programs =
          session.abilities().entries().stream().filter(entry -> entry.program() != null).toList();
      require(
          programs.size() == 4
              && programs.stream().map(AbilityNetwork.Entry::id).distinct().count() == 4,
          "Only actual programmed items are listed, each with its own identity");
      var entry = at(session, 6);
      require(entry.program().customName().equals("Dismiss"), "Named program in the catalogue");
      reference = entry.id();
      String cypherId = at(session, Inventory.SLOT_OFFHAND).id();
      String artifactId = at(session, 22).id();
      PreparedAbilities.edit(player, 1, reference);
      PreparedAbilities.select(player, 1);
      PreparedAbilities.edit(player, 1, "hexcasting:trinket/" + UUID.randomUUID());
      require(
          PreparedAbilities.preparation(player).slots().get(1).equals(reference),
          "Forged assignment cannot replace a bound item");
      player.setGameMode(GameType.SPECTATOR);
      PreparedAbilities.cast(player);
      require(
          media(trinket) == 3000 && inventory.selected == 0,
          "Spectating never selects or casts a program");
      player.setGameMode(GameType.SURVIVAL);
      PreparedAbilities.cast(player);
      require(
          player.getMainHandItem() == trinket
              && inventory.getItem(0).is(Items.STICK)
              && media(trinket) == 2000
              && media(other) == 3000,
          "Selecting the real hotbar stack spends media once and preserves the previous held item");
      PreparedAbilities.cast(player);
      require(
          media(trinket) == 2000 && player.getCooldowns().isOnCooldown(trinket.getItem()),
          "Native item cooldown blocks repeated activation");

      PreparedAbilities.edit(player, 4, cypherId);
      PreparedAbilities.select(player, 4);
      PreparedAbilities.cast(player);
      require(
          player.getOffhandItem().isEmpty()
              && player.getMainHandItem() == trinket
              && byId(session, cypherId).reason().equals("hex_missing"),
          "Native offhand cypher breaks at zero charge and only its assignment goes missing");
      PreparedAbilities.edit(player, 5, artifactId);
      PreparedAbilities.select(player, 5);
      PreparedAbilities.cast(player);
      require(
          byId(session, artifactId).reason().equals("hex_stowed")
              && inventory.getItem(22) == artifact
              && player.getMainHandItem() == trinket,
          "Backpack program remains bindable and cannot replace a held item");
      inventory.setItem(Inventory.SLOT_OFFHAND, inventory.removeItemNoUpdate(22));
      inventory.setItem(6, new ItemStack(Items.STICK));
      inventory.setItem(2, ItemStack.EMPTY);
      var dust = item("amethyst_dust");
      inventory.setItem(15, dust);
      PreparedAbilities.cast(player);
      require(
          player.getOffhandItem() == artifact
              && dust.isEmpty()
              && player.getMainHandItem().is(Items.STICK),
          "Native artifact casts from its new hand and draws physical media from inventory");
      require(
          MagicData.getPlayerMagicData(player).getMana() == 0
              && MagicPractice.credit(player) == practice,
          "Hex execution is independent of Iron's mana and practice");
      PreparedAbilities.select(player, 1);
      player.getCooldowns().removeCooldown(trinket.getItem());
      inventory.setItem(2, other);
      PreparedAbilities.cast(player);
      require(
          byId(session, reference).reason().equals("hex_missing") && media(other) == 3000,
          "Missing item cannot dispatch another program of its type");
      inventory.setItem(7, trinket);
      CustomData.update(
          DataComponents.CUSTOM_DATA, trinket, tag -> tag.putLong("hexcasting:media", 3000));
      PreparedAbilities.cast(player);
      require(
          player.getMainHandItem() == trinket
              && media(trinket) == 2000
              && byId(session, reference).program().inventorySlot() == 7,
          "Recharging and moving a program preserve its binding");
      AbilitiesRegression.verifyDeathClone(
          player,
          replacement ->
              require(
                  PreparedAbilities.preparation(replacement).slots().get(1).equals(reference),
                  "Death preserves the missing or recoverable item reference"));
      saved = player.saveWithoutId(new CompoundTag());
    }
    try (var session =
        TestShipPlayerSession.create(level, identity, "HexPrograms", position, saved)) {
      var player = session.player;
      PreparedAbilities.sync(player, true);
      require(
          PreparedAbilities.preparation(player).selected() == 1
              && byId(session, reference).program().inventorySlot() == 7,
          "Native save and rejoin preserve both item and assignment");
      var original = player.getMainHandItem();
      var copy = original.copy();
      player.getInventory().setItem(2, copy);
      PreparedAbilities.cast(player);
      require(
          byId(session, reference).reason().equals("hex_missing")
              && media(original) == 2000
              && media(copy) == 2000
              && !at(session, 2).id().equals(at(session, 7).id()),
          "Colliding identities require an explicit new binding for either item");
    }
    LogUtils.getLogger().info("Prepared Hex programs regression passed");
  }

  private static AbilityNetwork.Entry byId(TestShipPlayerSession session, String id) {
    return session.abilities().entries().stream()
        .filter(entry -> entry.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private static AbilityNetwork.Entry at(TestShipPlayerSession session, int slot) {
    return session.abilities().entries().stream()
        .filter(entry -> entry.program() != null && entry.program().inventorySlot() == slot)
        .findFirst()
        .orElseThrow();
  }

  private static ItemStack item(String type) {
    return BuiltInRegistries.ITEM
        .getOptional(ResourceLocation.fromNamespaceAndPath("hexcasting", type))
        .orElseThrow()
        .getDefaultInstance();
  }

  private static ItemStack program(String type, long media) {
    // Native Sentinel's Dismissal (north-east, qdwdqdw) needs no arguments and costs 1,000 media.
    var shape = new CompoundTag();
    shape.putByte("start_dir", (byte) 0);
    shape.putByteArray("angles", new byte[] {5, 2, 0, 2, 5, 2, 0});
    var iota = new CompoundTag();
    iota.putString("hexcasting:type", "hexcasting:pattern");
    iota.put("hexcasting:data", shape);
    var program = new ListTag();
    program.add(iota);
    var stack = item(type);
    CustomData.update(
        DataComponents.CUSTOM_DATA,
        stack,
        tag -> {
          tag.put("patterns", program);
          tag.putLong("hexcasting:media", media);
          tag.putLong("hexcasting:start_media", Math.max(1000, media));
        });
    return stack;
  }

  private static long media(ItemStack stack) {
    return stack
        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        .copyTag()
        .getLong("hexcasting:media");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
