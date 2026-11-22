package dev.aeroscapes.atmosphere;

import dev.ryanhcode.sable.Sable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CrystalResonance {
  private static final int SEARCH_DISTANCE = 4;
  private static final int MAX_REPLIES = 2;
  private static final int COOLDOWN_TICKS = 30;
  private static final TagKey<Block> CLUSTERS =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "resonant_crystals"));
  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "aeroscapes_reimagine");
  private static final Supplier<AttachmentType<Long>> NEXT_RESONANCE =
      ATTACHMENTS.register(
          "next_crystal_resonance", () -> AttachmentType.builder(() -> 0L).build());

  private CrystalResonance() {}

  public static void register(IEventBus modBus) {
    ATTACHMENTS.register(modBus);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CrystalResonance::onStrike);
  }

  private static void onStrike(PlayerInteractEvent.LeftClickBlock event) {
    if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
        || event.getUseBlock() == TriState.FALSE
        || !(event.getEntity() instanceof ServerPlayer player)
        || player instanceof FakePlayer
        || player.isSpectator()
        || !(event.getLevel() instanceof ServerLevel level)
        || !level.dimension().equals(Level.OVERWORLD)) return;

    BlockPos origin = event.getPos();
    long now = level.getGameTime();
    if (!player.canInteractWithBlock(origin, 0)
        || !level.mayInteract(player, origin)
        || now < player.getData(NEXT_RESONANCE)
        || !loadedState(level, origin).is(CLUSTERS)
        || player.blockActionRestricted(level, origin, player.gameMode.getGameModeForPlayer())
        || (ModList.get().isLoaded("sable") && ShipPlots.contains(level, origin))) return;

    var replies = findReplies(origin, pos -> loadedState(level, pos));
    if (replies.isEmpty()) return;
    player.setData(NEXT_RESONANCE, now + COOLDOWN_TICKS);
    for (BlockPos reply : replies) {
      float pitch = 0.8F + 0.15F * origin.distManhattan(reply);
      level.playSound(
          null, reply, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, pitch);
    }
  }

  private static List<BlockPos> findReplies(
      BlockPos origin, Function<BlockPos, BlockState> stateAt) {
    var face = stateAt.apply(origin).getOptionalValue(BlockStateProperties.FACING);
    if (face.isEmpty()) return List.of();
    BlockPos opening = origin.relative(face.orElseThrow());
    if (!stateAt.apply(opening).isAir()) return List.of();

    var replies = new ArrayList<BlockPos>(MAX_REPLIES);
    var visited = new HashSet<BlockPos>();
    var openAir = new ArrayDeque<BlockPos>();
    visited.add(opening);
    openAir.add(opening);
    // Traverse the shared air pocket so an adjacent sealed geode stays silent.
    while (!openAir.isEmpty()) {
      BlockPos air = openAir.remove();
      for (Direction side : Direction.values()) {
        BlockPos neighbor = air.relative(side);
        if (neighbor.equals(origin) || origin.distManhattan(neighbor) > SEARCH_DISTANCE) continue;
        BlockState state = stateAt.apply(neighbor);
        if (state.isAir() && visited.add(neighbor)) {
          openAir.add(neighbor);
        } else if (state.is(CLUSTERS)
            && state.getOptionalValue(BlockStateProperties.FACING).orElse(null)
                == side.getOpposite()
            && !replies.contains(neighbor)) {
          replies.add(neighbor);
          if (replies.size() == MAX_REPLIES) return replies;
        }
      }
    }
    return replies;
  }

  private static BlockState loadedState(ServerLevel level, BlockPos position) {
    if (level.isOutsideBuildHeight(position)) return Blocks.BARRIER.defaultBlockState();
    var chunk =
        level
            .getChunkSource()
            .getChunkNow(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()));
    return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(position);
  }

  private static final class ShipPlots {
    private static boolean contains(ServerLevel level, BlockPos position) {
      return Sable.HELPER.getContaining(level, position) != null;
    }
  }
}
