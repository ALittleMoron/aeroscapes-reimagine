package dev.aeroscapes.equipment.tools;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.Sable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class NaturalBlockProvenance {
  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "aeroscapes_reimagine");
  private static final Codec<BitSet> POSITIONS =
      Codec.LONG_STREAM.xmap(
          stream -> BitSet.valueOf(stream.toArray()), bits -> Arrays.stream(bits.toLongArray()));
  private static final Supplier<AttachmentType<BitSet>> CHANGES =
      ATTACHMENTS.register(
          "natural_block_changes",
          () -> AttachmentType.builder(() -> new BitSet()).serialize(POSITIONS).build());
  private static final String SABLE_VERSION =
      ModList.get()
          .getModContainerById("sable")
          .map(container -> container.getModInfo().getVersion().toString())
          .orElse("");

  private NaturalBlockProvenance() {}

  static void register(IEventBus modBus) {
    ATTACHMENTS.register(modBus);
    NeoForge.EVENT_BUS.addListener(NaturalBlockProvenance::beginTracking);
  }

  private static void beginTracking(ChunkEvent.Load event) {
    if (event.isNewChunk()
        && event.getChunk() instanceof LevelChunk chunk
        && !chunk.hasData(CHANGES)) {
      // An empty persisted set means known generation. A missing attachment means unknown history.
      chunk.setData(CHANGES, new BitSet());
    }
  }

  public static void recordChange(
      LevelChunk chunk, BlockPos pos, BlockState incoming, BlockState old) {
    // Leave existing markers intact through removal and its ensuing drop event.
    if (incoming == old
        || incoming.isAir()
        || incoming.getBlock() instanceof LiquidBlock
        || chunk.getLevel().isClientSide()) {
      return;
    }
    BitSet changes = chunk.getExistingDataOrNull(CHANGES);
    if (changes == null) return;
    int index = localIndex(chunk, pos);
    if (!changes.get(index)) {
      changes.set(index);
      chunk.setUnsaved(true);
    }
  }

  static boolean isNatural(ServerLevel level, BlockPos pos) {
    if (isOnShip(level, pos)) return false;
    LevelChunk chunk =
        level
            .getChunkSource()
            .getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    if (chunk == null) return false;
    BitSet changes = chunk.getExistingDataOrNull(CHANGES);
    return changes != null && !changes.get(localIndex(chunk, pos));
  }

  static boolean isOnShip(ServerLevel level, BlockPos pos) {
    return !SABLE_VERSION.isEmpty()
        && (!"2.0.6".equals(SABLE_VERSION) || SablePlots.contains(level, pos));
  }

  private static int localIndex(LevelChunk chunk, BlockPos pos) {
    int localY = pos.getY() - chunk.getMinBuildHeight();
    return localY * 256 + (pos.getZ() & 15) * 16 + (pos.getX() & 15);
  }

  private static final class SablePlots {
    private static boolean contains(ServerLevel level, BlockPos pos) {
      return Sable.HELPER.getContaining(level, pos) != null;
    }
  }
}
