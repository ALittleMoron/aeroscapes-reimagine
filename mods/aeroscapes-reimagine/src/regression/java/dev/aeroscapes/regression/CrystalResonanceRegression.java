package dev.aeroscapes.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;

final class CrystalResonanceRegression {
  private CrystalResonanceRegression() {}

  static void verify(ServerLevel level) {
    BlockPos origin = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 6);
    BlockPos reply = origin.east(3);
    BlockPos sealedCrystal = origin.west(3);
    BlockPos sealedOpening = sealedCrystal.east();
    var cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState();
    for (Direction direction : Direction.values()) {
      if (direction != Direction.WEST) {
        put(level, sealedOpening.relative(direction), Blocks.STONE.defaultBlockState());
      }
    }
    put(level, reply.east(), Blocks.STONE.defaultBlockState());
    put(level, sealedCrystal.west(), Blocks.STONE.defaultBlockState());
    put(level, origin, cluster.setValue(BlockStateProperties.FACING, Direction.EAST));
    put(level, reply, cluster.setValue(BlockStateProperties.FACING, Direction.WEST));
    put(level, sealedCrystal, cluster.setValue(BlockStateProperties.FACING, Direction.EAST));
    put(level, sealedOpening, Blocks.AIR.defaultBlockState());
    put(level, origin.east(), Blocks.AIR.defaultBlockState());
    put(level, origin.east(2), Blocks.AIR.defaultBlockState());

    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "CrystalProbe",
            Vec3.atBottomCenterOf(origin.east().below()),
            null)) {
      verifyStrikes(session.player, origin, reply);
    }
  }

  private static void verifyStrikes(ServerPlayer player, BlockPos origin, BlockPos reply) {
    var replies = new ArrayList<BlockPos>();
    Consumer<PlayLevelSoundEvent.AtPosition> record =
        event -> {
          if (event.getSound() != null
              && event.getSound().value() == SoundEvents.AMETHYST_BLOCK_CHIME) {
            replies.add(BlockPos.containing(event.getPosition()));
          }
        };
    NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, record);
    try {
      var denied = new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.START);
      denied.setCanceled(true);
      NeoForge.EVENT_BUS.post(denied);
      var denyBlock =
          new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.START);
      denyBlock.setUseBlock(TriState.FALSE);
      NeoForge.EVENT_BUS.post(denyBlock);
      NeoForge.EVENT_BUS.post(
          new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.STOP));
      player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
      NeoForge.EVENT_BUS.post(
          new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.START));
      player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
      require(replies.isEmpty(), "Denied or finishing strikes triggered resonance");
      var strike = new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.START);
      NeoForge.EVENT_BUS.post(strike);
      require(
          !strike.isCanceled() && replies.equals(List.of(reply)),
          "Open pocket replies: " + replies);
      NeoForge.EVENT_BUS.post(
          new LeftClickBlock(player, origin, Direction.EAST, LeftClickBlock.Action.START));
      require(replies.equals(List.of(reply)), "A repeat strike ignored the cooldown: " + replies);
    } finally {
      NeoForge.EVENT_BUS.unregister(record);
    }
  }

  private static void put(ServerLevel level, BlockPos position, BlockState state) {
    level.setBlock(position, state, Block.UPDATE_CLIENTS);
  }

  private static void require(boolean condition, String failure) {
    if (!condition) throw new IllegalStateException(failure);
  }
}
