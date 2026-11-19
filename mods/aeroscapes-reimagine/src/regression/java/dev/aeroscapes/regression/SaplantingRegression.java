package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.storage.DataAttachmentTypes;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

final class SaplantingRegression {
  private SaplantingRegression() {}

  static void verify(ServerLevel level) {
    var bed = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 6);
    var planted = bed.above();
    var originalBed = level.getBlockState(bed);
    var originalPlant = level.getBlockState(planted);
    var placed = level.getChunkAt(planted).getData(DataAttachmentTypes.PLACED_MAP.get());
    var previousOwner = placed.remove(planted);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "Planter",
            Vec3.atBottomCenterOf(bed.east(2).above()),
            null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        if (!RaceIdentity.choose(player, Race.HUMAN, 0))
          throw new IllegalStateException("Fixture player could not finish character creation");
        level.setBlockAndUpdate(bed, Blocks.DIRT.defaultBlockState());
        level.removeBlock(planted, false);
        long before = APIUtils.getXp("building", player);
        Consumer<BlockEvent.EntityPlaceEvent> deny =
            event -> {
              if (event.getPos().equals(planted)) event.setCanceled(true);
            };
        NeoForge.EVENT_BUS.addListener(
            EventPriority.LOWEST, BlockEvent.EntityPlaceEvent.class, deny);
        try {
          autoPlant(level, planted);
          if (!level.isEmptyBlock(planted))
            throw new IllegalStateException("Cancelled planting placed a sapling");
        } finally {
          NeoForge.EVENT_BUS.unregister(deny);
        }
        autoPlant(level, planted);
        if (!level.getBlockState(planted).is(Blocks.OAK_SAPLING)
            || placed.containsKey(planted)
            || APIUtils.getXp("building", player) != before)
          throw new IllegalStateException(
              "Saplanting did not plant independently of player progression");
        level.removeBlock(planted, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, Items.STONE_BRICKS.getDefaultInstance());
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(planted), Direction.UP, bed, false);
        var use =
            player.gameMode.useItemOn(
                player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        if (!level.getBlockState(planted).is(Blocks.STONE_BRICKS))
          throw new IllegalStateException(
              "Manual placement result: " + use + ", block: " + level.getBlockState(planted));
        if (!player.getUUID().equals(placed.get(planted)))
          throw new IllegalStateException(
              "Manual placement lost its PMMO owner: " + placed.get(planted));
        long after = APIUtils.getXp("building", player);
        if (after <= before)
          throw new IllegalStateException(
              "Building practice for placing stone bricks: " + before + " -> " + after);
        LogUtils.getLogger().info("Saplanting placement and manual building practice passed");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    } finally {
      level.setBlockAndUpdate(planted, originalPlant);
      level.setBlockAndUpdate(bed, originalBed);
      if (previousOwner == null) placed.remove(planted);
      else placed.put(planted, previousOwner);
    }
  }

  private static void autoPlant(ServerLevel level, BlockPos target) {
    // Run the same pinned task that Saplanting drains on a world tick.
    try {
      var type = Class.forName("cool.muyucloud.saplanting.util.PlantContext");
      var task = type.getConstructor().newInstance();
      type.getMethod("setWorld", ServerLevel.class).invoke(task, level);
      type.getMethod("setPos", BlockPos.class).invoke(task, target);
      type.getMethod("setStack", ItemStack.class)
          .invoke(task, Items.OAK_SAPLING.getDefaultInstance());
      type.getMethod("setLarge", Boolean.class).invoke(task, false);
      type.getMethod("plant").invoke(task);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Saplanting's queued planting task failed", exception);
    }
  }
}
