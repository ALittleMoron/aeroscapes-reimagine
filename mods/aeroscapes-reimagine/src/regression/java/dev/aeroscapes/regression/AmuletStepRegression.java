package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.travel.AmuletStep;
import io.redspace.ironsspellbooks.api.events.SpellTeleportEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.curios.TeleportationAmuletItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

final class AmuletStepRegression {
  private AmuletStepRegression() {}

  static void verify(ServerLevel level) {
    var floor = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 18);
    var start = Vec3.atBottomCenterOf(floor.above());
    for (int z = 0; z <= 18; z++)
      level.setBlockAndUpdate(floor.south(z), Blocks.STONE.defaultBlockState());
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "AmuletFixture", start, null)) {
      var player = session.player;
      AbilitiesRegression.initialize(player);
      io.redspace.ironsspellbooks.api.util.Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
      var item = (TeleportationAmuletItem) ItemRegistry.TELEPORTATION_AMULET.get();
      var amulet = item.getDefaultInstance();
      var power = player.getAttribute(AttributeRegistry.ENDER_SPELL_POWER);
      power.setBaseValue(1.26);
      player.getInventory().add(amulet.copy());
      PreparedAbilities.edit(player, 4, AmuletStep.ID);
      require(
          !PreparedAbilities.preparation(player).slots().contains(AmuletStep.ID),
          "Carrying grants no ability");
      var cosmetic = curios.getStacksHandler("necklace").orElseThrow().getCosmeticStacks();
      cosmetic.setStackInSlot(0, amulet.copy());
      require(!AmuletStep.available(player), "A cosmetic necklace grants no step");
      cosmetic.setStackInSlot(0, ItemStack.EMPTY);
      curios.setEquippedCurio("head", 0, Items.LEATHER_HELMET.getDefaultInstance());
      curios.setEquippedCurio("necklace", 0, amulet);
      curios.setSlotActive("necklace", 0, false);
      require(!AmuletStep.available(player), "An inactive necklace slot grants no step");
      curios.setSlotActive("necklace", 0, true);
      power.setBaseValue(1.25);
      require(!AmuletStep.available(player), "Exactly 125% cannot activate the amulet");
      var context = new SlotContext("necklace", player, 0, false, true);
      player.tickCount = 5;
      item.curioTick(context, amulet);
      var drops =
          level.getEntitiesOfClass(
              ItemEntity.class,
              player.getBoundingBox().inflate(12),
              entity -> entity.getItem().is(item));
      require(
          curios.findCurio("necklace", 0).isEmpty()
              && drops.size() == 1
              && curios.findCurio("head", 0).orElseThrow().stack().is(Items.LEATHER_HELMET),
          "Native rejection drops the necklace once and preserves the unrelated first slot");
      drops.getFirst().discard();
      power.setBaseValue(1.26);
      curios.setEquippedCurio("necklace", 0, amulet.copy());
      PreparedAbilities.edit(player, 4, AmuletStep.ID);
      PreparedAbilities.select(player, 4);
      require(
          session.abilities().slots().get(4).equals(AmuletStep.ID),
          "Equipped necklace grants a prepared step");
      player.setOnGround(true);
      var obstacle = floor.south().above();
      level.setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(obstacle.above(), Blocks.STONE.defaultBlockState());
      PreparedAbilities.cast(player);
      require(
          player.position().equals(start) && AmuletStep.cooldown(player) == 0,
          "No passage through wall");
      level.removeBlock(obstacle, false);
      level.removeBlock(obstacle.above(), false);
      level.removeBlock(floor.south(), false);
      PreparedAbilities.cast(player);
      require(
          player.position().equals(start)
              && !player.hasEffect(MobEffectRegistry.EVASION)
              && AmuletStep.cooldown(player) == 0,
          "No crossing an unsupported gap for distant solid ground");
      level.setBlockAndUpdate(floor.south(), Blocks.STONE.defaultBlockState());
      var data = MagicData.getPlayerMagicData(player);
      data.setMana(0);
      long practice = MagicPractice.credit(player);
      var teleports = new AtomicInteger();
      Consumer<SpellTeleportEvent> observe =
          event -> {
            if (event.getEntity() != player) return;
            if (teleports.incrementAndGet() == 1) event.setCanceled(true);
            else event.setTargetZ(start.z + 1000);
          };
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, observe);
      try {
        PreparedAbilities.cast(player);
        require(
            player.position().equals(start)
                && AmuletStep.cooldown(player) == 0
                && !player.hasEffect(MobEffectRegistry.EVASION),
            "A rejected teleport spends no cooldown and grants no Evasion");
        PreparedAbilities.cast(player);
        require(teleports.get() == 2, "One native event per attempted teleport");
      } finally {
        NeoForge.EVENT_BUS.unregister(observe);
      }
      require(
          player.position().distanceTo(start.add(0, 0, 16)) < 0.001
              && player.getEffect(MobEffectRegistry.EVASION) != null
              && player.getEffect(MobEffectRegistry.EVASION).getDuration() == 60
              && AmuletStep.cooldown(player) == 400
              && MagicPractice.credit(player) == practice
              && data.getMana() == 0,
          "Successful bookless step, native Evasion, range and cooldown");
      var landed = player.position();
      curios.setEquippedCurio("necklace", 0, ItemStack.EMPTY);
      PreparedAbilities.sync(player, false);
      require(
          session.abilities().slots().get(4).isEmpty()
              && session.abilities().entries().stream()
                  .noneMatch(entry -> entry.id().equals(AmuletStep.ID)),
          "Unequipping immediately removes the displayed action");
      PreparedAbilities.cast(player);
      curios.setEquippedCurio("necklace", 0, amulet.copy());
      PreparedAbilities.edit(player, 2, AmuletStep.ID);
      PreparedAbilities.select(player, 2);
      PreparedAbilities.cast(player);
      require(
          player.position().equals(landed) && AmuletStep.cooldown(player) == 400,
          "Swapping the item or prepared slot does not reset the cooldown");
      AbilitiesRegression.verifyDeathClone(
          player,
          replacement ->
              require(
                  AmuletStep.cooldown(replacement) == 400,
                  "Travel deadline survives a death clone"));
    } finally {
      for (int z = 0; z <= 18; z++) level.removeBlock(floor.south(z), false);
      for (int y = 1; y <= 2; y++) level.removeBlock(floor.south().above(y), false);
    }
    LogUtils.getLogger().info("Teleportation amulet equipped step regression passed");
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}
