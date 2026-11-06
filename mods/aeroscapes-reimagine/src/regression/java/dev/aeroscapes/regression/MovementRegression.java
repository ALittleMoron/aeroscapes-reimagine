package dev.aeroscapes.regression;

import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import tictim.paraglider.api.bargain.Bargain;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.api.stamina.Stamina;
import tictim.paraglider.api.vessel.VesselContainer;
import tictim.paraglider.config.Cfg;
import tictim.paraglider.config.FeatureCfg;
import tictim.paraglider.impl.movement.ServerPlayerMovement;

/** Loaded native rewards, progression, movement policy and offhand item ownership. */
public final class MovementRegression {
  private MovementRegression() {}

  public static void verify(ServerLevel level) {
    verifySettings();
    verifyOrbLoot(level);
    UUID identity = UUID.fromString("b7a29954-311f-4940-b367-c8b388cc0093");
    Vec3 position = Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 40, 0);
    CompoundTag saved;
    try (var session =
        TestShipPlayerSession.create(level, identity, "MovementFixture", position, null)) {
      ServerPlayer player = session.player;
      verifyBargains(level, player);
      verifySprintingAndGliding(player);
      verifyOffhandHook(level, player);
      HookMiningRegression.verify(level, player);
      saved = player.saveWithoutId(new CompoundTag());
    }
    try (var session =
        TestShipPlayerSession.create(level, identity, "MovementFixture", position, saved)) {
      VesselContainer vessels = VesselContainer.get(session.player);
      require(
          vessels.heartContainer() == 1 && vessels.staminaVessel() == 2 && vessels.essence() == 3,
          "Native vessel attachments changed after saving and reloading the player");
    }
  }

  private static void verifyOrbLoot(ServerLevel level) {
    Item orb = item("paraglider:spirit_orb");
    Vec3 origin = Vec3.atCenterOf(level.getSharedSpawnPos());
    var blockParams =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, origin)
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_PICKAXE))
            .withParameter(LootContextParams.BLOCK_STATE, Blocks.SPAWNER.defaultBlockState())
            .create(LootContextParamSets.BLOCK);
    var spawnerLoot =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(Blocks.SPAWNER.getLootTable())
            .getRandomItems(blockParams, 1532L);
    require(count(spawnerLoot, orb) == 1, "Actual spawner block loot lost its Spirit Orb");
    var chestParams =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, origin)
            .create(LootContextParamSets.CHEST);
    for (var reward :
        java.util.Map.of(
                "chests/trial_chambers/reward", 2,
                "chests/trial_chambers/reward_ominous", 4,
                "chests/ancient_city", 1)
            .entrySet()) {
      var loot =
          level
              .getServer()
              .reloadableRegistries()
              .getLootTable(
                  ResourceKey.create(
                      Registries.LOOT_TABLE,
                      ResourceLocation.fromNamespaceAndPath("minecraft", reward.getKey())))
              .getRandomItems(chestParams, 1532L);
      require(
          count(loot, orb) == reward.getValue(),
          "Actual loot reward lost its native Spirit Orbs: " + reward.getKey());
    }
  }

  private static int count(List<ItemStack> stacks, Item item) {
    return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private static void verifySettings() {
    Cfg config = Cfg.get();
    require(
        config.updraft() && config.paraglidingConsumesStamina() && !config.runningConsumesStamina(),
        "Native gliding/updrafts must remain active without sprint or swim costs");
    require(
        config.startingHearts() == 10
            && config.maxHeartContainers() == 20
            && config.startingStamina() == 1000
            && config.maxStaminaVessels() == 10
            && config.staminaIncreasePerVessel() == 200,
        "Native health and stamina progression changed");
    FeatureCfg features = FeatureCfg.get();
    require(
        features.enableSpiritOrbGens()
            && features.enableHeartContainers()
            && features.enableStaminaVessels()
            && features.enableVillageStructures(),
        "Selected orb, vessel or village-statue content is disabled");
    require(
        config.spiritOrbLoots()
            && config.spawnerSpiritOrbDrops() == 1.0
            && config.trialSpiritOrbDrops() == 2.0
            && config.ominousTrialSpiritOrbDrops() == 4.0
            && config.enderDragonDropsVessel()
            && config.witherDropsVessel()
            && config.raidGivesVessel()
            && config.elderGuardianDropsSpiritOrb(),
        "Native survival reward sources changed");
  }

  private static void verifyBargains(ServerLevel level, ServerPlayer player) {
    Item orb = item("paraglider:spirit_orb");
    player.getInventory().clearContent();
    player.getInventory().setItem(0, new ItemStack(orb, 8));
    VesselContainer vessels = VesselContainer.get(player);
    Bargain heart = bargain(level, "goddess_statue/heart_container");
    Bargain stamina = bargain(level, "goddess_statue/stamina_vessel");
    require(heart.bargain(player, true).isSuccess(), "Four-orb heart bargain cannot be simulated");
    require(
        player.getInventory().countItem(orb) == 8 && vessels.heartContainer() == 0,
        "Simulated statue bargain changed player state");
    require(heart.bargain(player, false).isSuccess(), "Heart bargain failed");
    require(
        player.getInventory().countItem(orb) == 4 && vessels.heartContainer() == 1,
        "Heart bargain did not consume exactly four orbs for one upgrade");
    require(stamina.bargain(player, false).isSuccess(), "Stamina bargain failed");
    require(
        player.getInventory().countItem(orb) == 0 && vessels.staminaVessel() == 1,
        "Stamina bargain did not consume exactly four orbs for one upgrade");
    require(
        bargain(level, "horned_statue/sell_heart_container").bargain(player, false).isSuccess(),
        "Native heart reallocation sale failed");
    require(
        vessels.heartContainer() == 0
            && vessels.essence() == 1
            && player.getInventory().countItem(Items.EMERALD) == 5,
        "Heart sale did not return one essence and five emeralds");
    player.getInventory().add(new ItemStack(Items.EMERALD));
    require(
        bargain(level, "horned_statue/buy_stamina_vessel").bargain(player, false).isSuccess(),
        "Native reallocation to stamina failed");
    require(
        vessels.staminaVessel() == 2
            && vessels.essence() == 0
            && player.getInventory().countItem(Items.EMERALD) == 0,
        "Stamina buyback did not consume one essence and six emeralds");
    require(!heart.bargain(player, false).isSuccess(), "Statue granted a heart without payment");
    vessels.setHeartContainer(20, false, false);
    vessels.setStaminaVessel(10, false, false);
    player.getInventory().add(new ItemStack(orb, 4));
    require(
        !heart.bargain(player, false).isSuccess()
            && !stamina.bargain(player, false).isSuccess()
            && player.getInventory().countItem(orb) == 4,
        "Native caps consumed payment or allowed an extra upgrade");
    require(
        vessels.setHeartContainer(21, false, false) == VesselContainer.SetResult.TOO_HIGH
            && vessels.setStaminaVessel(11, false, false) == VesselContainer.SetResult.TOO_HIGH,
        "Native vessel limits changed");
    vessels.setHeartContainer(1, false, false);
    vessels.setStaminaVessel(2, false, false);
    vessels.setEssence(3, false, false);
  }

  private static void verifySprintingAndGliding(ServerPlayer player) {
    ServerPlayerMovement movement = (ServerPlayerMovement) Movement.get(player);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.setOnGround(true);
    player.setSprinting(true);
    movement.update();
    Stamina stamina = Stamina.get(player);
    stamina.setStamina(500.0);
    for (int tick = 0; tick < 40; tick++) movement.update();
    require(
        movement.state().id().equals(ResourceLocation.parse("paraglider:running"))
            && movement.staminaDelta() == 0.0
            && stamina.stamina() == 500.0,
        "Ordinary sprinting consumed stamina or selected the wrong state");
    player.setSprinting(false);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("paraglider:paraglider")));
    player.setOnGround(false);
    player.setPos(player.getX(), player.getY() - 2.0, player.getZ());
    movement.update();
    require(
        movement.state().paragliding()
            && movement.staminaDelta() < 0.0
            && stamina.stamina() < 500.0,
        "Native gliding did not consume stamina");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
  }

  private static void verifyOffhandHook(ServerLevel level, ServerPlayer player) {
    Item hookItem = item("yo_hooks:iron_grappling_hook");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(hookItem));
    hookItem.use(level, player, InteractionHand.OFF_HAND);
    HookEntity hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    require(
        hook != null && hook.getHoldingHand(player) == InteractionHand.OFF_HAND,
        "Native hook failed to recognize the active offhand stack");
    try {
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
      require(hook.isControlledBy(player), "Changing the main-hand mining tool released the hook");
    } finally {
      hook.discard();
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }
  }

  private static Bargain bargain(ServerLevel level, String path) {
    var recipe =
        level
            .getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath("paraglider", path))
            .orElseThrow();
    if (recipe.value() instanceof Bargain bargain) return bargain;
    throw new IllegalStateException("Missing native bargain: " + path);
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
