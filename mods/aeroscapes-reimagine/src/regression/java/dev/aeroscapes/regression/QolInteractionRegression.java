package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Exercises selected QoL mechanics on a disposable dedicated server. */
public final class QolInteractionRegression {
  private QolInteractionRegression() {}

  public static void verify(ServerLevel level, Path output) {
    TranslationRegistryRegression.verify(level, output.resolveSibling("translations.json"));
    Vec3 position = Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    level.getChunkAt(net.minecraft.core.BlockPos.containing(position));
    require(
        level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position, 8, 8, 8)).isEmpty(),
        "Fixture area contains unrelated XP orbs");
    JsonObject report = new JsonObject();
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("8ebaf75e-6649-456a-a816-ff85a1a96284"),
            "QolRegression",
            position,
            null)) {
      ServerPlayer player = session.player;
      verifyClumps(level, player, position);
      report.addProperty("clumps_mixed_merge_save_pickup_mending", true);
      verifyTradeCycling(level, player, position);
      report.addProperty("trade_cycling_first_trade_lock", true);
      verifyDismount(level, player, position);
      report.addProperty("dismount_native_event_and_player_guard", true);
    } catch (ReflectiveOperationException failure) {
      Throwable cause =
          failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
      throw new IllegalStateException("Pinned QoL interaction fixture failed", cause);
    }
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write QoL interaction report", failure);
    }
  }

  private static void verifyClumps(ServerLevel level, ServerPlayer player, Vec3 position)
      throws ReflectiveOperationException {
    resetXp(player);
    ExperienceOrb first = mergedOrb(level, position);
    CompoundTag saved = first.saveWithoutId(new CompoundTag());
    require(
        saved.getCompound("clumpedMap").getInt("3") == 1
            && saved.getCompound("clumpedMap").getInt("7") == 1,
        "Mixed Clumps histogram absent in actual entity save");
    first.discard();
    ExperienceOrb reloaded = new ExperienceOrb(level, position.x, position.y, position.z, 0);
    reloaded.load(saved);
    require(level.addFreshEntity(reloaded), "Cannot add reloaded XP orb");
    require(reloaded.getValue() == 10, "Reload changed total XP");
    reloaded.playerTouch(player);
    require(
        reloaded.isRemoved() && player.totalExperience == 10 && player.takeXpDelay == 0,
        "Clumped pickup did not award ten XP immediately exactly once");
    resetXp(player);
    CompoundTag legacy =
        new ExperienceOrb(level, position.x, position.y, position.z, 3)
            .saveWithoutId(new CompoundTag());
    legacy.remove("clumpedMap");
    legacy.putInt("Count", 2);
    ExperienceOrb migrated = new ExperienceOrb(level, position.x, position.y, position.z, 0);
    migrated.load(legacy);
    require(level.addFreshEntity(migrated), "Cannot load legacy XP fixture");
    migrated.playerTouch(player);
    require(
        migrated.isRemoved() && player.totalExperience == 6,
        "Legacy vanilla orb count must migrate without losing XP");
    resetXp(player);
    ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
    tool.enchant(
        level
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.MENDING),
        1);
    tool.setDamageValue(10);
    player.setItemSlot(EquipmentSlot.MAINHAND, tool);
    ExperienceOrb repair = mergedOrb(level, position);
    repair.playerTouch(player);
    require(
        repair.isRemoved() && tool.getDamageValue() == 0 && player.totalExperience == 5,
        "Ten XP must repair ten durability and leave five player XP");
    player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    resetXp(player);
  }

  private static ExperienceOrb mergedOrb(ServerLevel level, Vec3 position)
      throws ReflectiveOperationException {
    ExperienceOrb first = new ExperienceOrb(level, position.x, position.y, position.z, 3);
    require(level.addFreshEntity(first), "Cannot add XP fixture");
    require(
        level
            .getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position, 2, 2, 2))
            .contains(first),
        "XP fixture must be queryable in an accessible entity section before merging");
    ExperienceOrb.award(level, position, 7);
    var orbs = level.getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position, 2, 2, 2));
    require(
        orbs.size() == 1 && first.getValue() == 10,
        "Native award must merge distinct XP denominations into one ten-XP orb");
    Object histogram =
        Class.forName("com.blamejared.clumps.helper.IClumpedOrb")
            .getMethod("clumps$getClumpedMap")
            .invoke(first);
    require(
        histogram instanceof Map<?, ?> map
            && Integer.valueOf(1).equals(map.get(3))
            && Integer.valueOf(1).equals(map.get(7))
            && map.size() == 2,
        "Clumps lost original XP denomination counts");
    return first;
  }

  private static void verifyTradeCycling(ServerLevel level, ServerPlayer player, Vec3 position)
      throws ReflectiveOperationException {
    Villager villager = new Villager(EntityType.VILLAGER, level);
    villager.setPos(position);
    villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.FARMER, 1));
    villager
        .getBrain()
        .setMemory(
            MemoryModuleType.JOB_SITE,
            GlobalPos.of(level.dimension(), net.minecraft.core.BlockPos.containing(position)));
    require(level.addFreshEntity(villager), "Cannot add villager fixture");
    var originalMenu = player.containerMenu;
    try {
      MerchantMenu menu = new MerchantMenu(1, player.getInventory(), villager);
      player.containerMenu = menu;
      villager.setTradingPlayer(player);
      var cycle =
          Class.forName("de.maxhenkel.tradecycling.TradeCyclingMod")
              .getMethod("onCycleTrades", ServerPlayer.class);
      var before = villager.getOffers();
      cycle.invoke(null, player);
      require(
          villager.getOffers() != before && !villager.getOffers().isEmpty(),
          "Untraded villager offers were not regenerated");
      var generated = villager.getOffers();
      var offer = generated.getFirst();
      menu.setSelectionHint(0);
      menu.getSlot(0).set(offer.getCostA().copy());
      menu.getSlot(1).set(offer.getCostB().copy());
      require(menu.getSlot(2).hasItem(), "Actual merchant output did not become obtainable");
      ItemStack traded = menu.getSlot(2).remove(menu.getSlot(2).getItem().getCount());
      menu.getSlot(2).onTake(player, traded);
      require(
          villager.getVillagerXp() > 0 && offer.getUses() > 0,
          "Actual result-slot trade did not lock villager progress");
      cycle.invoke(null, player);
      require(
          villager.getOffers() == generated && offer.getUses() > 0,
          "Cycle packet changed offers after the first completed trade");
      villager.overrideXp(0);
      villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
      cycle.invoke(null, player);
      require(villager.getOffers() == generated, "Villager without job site was rerolled");
    } finally {
      player.containerMenu = originalMenu;
      villager.setTradingPlayer(null);
      villager.discard();
    }
  }

  private static void verifyDismount(ServerLevel level, ServerPlayer player, Vec3 position)
      throws ReflectiveOperationException {
    var guard =
        Class.forName("com.serilum.dismountentity_common_neoforge.config.ConfigHandler")
            .getField("beAbleToDismountOtherPlayers");
    require(Boolean.FALSE.equals(guard.get(null)), "Dismount other players must remain disabled");
    var boat = EntityType.BOAT.create(level);
    var pig = EntityType.PIG.create(level);
    require(boat != null && pig != null, "Cannot create vehicle fixtures");
    boat.setPos(position);
    pig.setPos(position);
    require(level.addFreshEntity(boat) && level.addFreshEntity(pig), "Cannot add vehicle fixtures");
    try {
      require(pig.startRiding(boat, true), "Pig cannot board fixture boat");
      player.setShiftKeyDown(false);
      player.setPose(Pose.STANDING);
      NeoForge.EVENT_BUS.post(
          new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, boat));
      require(pig.getVehicle() == boat, "Ordinary interaction ejected passenger");
      player.setShiftKeyDown(true);
      player.setPose(Pose.CROUCHING);
      var event =
          NeoForge.EVENT_BUS.post(
              new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, boat));
      require(
          event.isCanceled() && pig.getVehicle() == null && boat.isAlive(),
          "Crouch-use did not eject mob and preserve vehicle through native event bus");
      require(player.startRiding(boat, true), "Player cannot board fixture boat");
      NeoForge.EVENT_BUS.post(
          new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, boat));
      require(player.getVehicle() == boat, "Default Dismount setting ejected player passenger");
    } finally {
      player.stopRiding();
      player.setShiftKeyDown(false);
      player.setPose(Pose.STANDING);
      pig.discard();
      boat.discard();
    }
  }

  private static void resetXp(ServerPlayer player) {
    player.totalExperience = 0;
    player.experienceLevel = 0;
    player.experienceProgress = 0;
    player.takeXpDelay = 40;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
