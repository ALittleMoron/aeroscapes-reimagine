package dev.aeroscapes.engineering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** Physical well targets identify finite reserves; extracted oil stays in native saved data. */
public final class OilFields {
  public static final int MINIMUM_AMOUNT = 48_000;
  public static final int MAXIMUM_AMOUNT = 96_000;
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks("aeroscapes_reimagine");
  public static final DeferredBlock<OilRockBlock> OIL_BEARING_ROCK =
      BLOCKS.register(
          "oil_bearing_rock",
          () ->
              new OilRockBlock(
                  BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                      .pushReaction(PushReaction.BLOCK)
                      .noLootTable()));

  private OilFields() {}

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    DeferredRegister<Feature<?>> features =
        DeferredRegister.create(Registries.FEATURE, "aeroscapes_reimagine");
    features.register("oil_field", () -> new OilFieldFeature(NoneFeatureConfiguration.CODEC));
    features.register(bus);
  }

  public static RandomSource random(long seed, ChunkPos chunk) {
    return RandomSource.create(
        seed ^ (chunk.x * 341873128712L + chunk.z * 132897987541L) ^ 0x05D1E5E1L);
  }

  public static int initialAmount(ServerLevel level, ChunkPos position) {
    if (level.dimension() == Level.OVERWORLD) {
      return findTarget(level, position) == null ? 0 : amount(level.getSeed(), position);
    }
    // Native Nether wells may still use bedrock, but have the same finite budget.
    return level.dimension() == Level.NETHER && random(level.getSeed(), position).nextInt(8) == 0
        ? amount(level.getSeed(), position)
        : 0;
  }

  public static @Nullable BlockPos findTarget(ServerLevel level, ChunkPos position) {
    LevelChunk chunk = level.getChunk(position.x, position.z);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
      var section = chunk.getSection(sectionIndex);
      if (!section.maybeHas(state -> state.is(OIL_BEARING_ROCK.get()))) continue;
      int minimumY = chunk.getMinBuildHeight() + sectionIndex * 16;
      for (int y = minimumY; y < minimumY + 16; y++) {
        for (int x = 0; x < 16; x++) {
          for (int z = 0; z < 16; z++) {
            if (section.getBlockState(x, y & 15, z).is(OIL_BEARING_ROCK.get())) {
              cursor.set(position.getMinBlockX() + x, y, position.getMinBlockZ() + z);
              return cursor.immutable();
            }
          }
        }
      }
    }
    return null;
  }

  public static int amount(long seed, ChunkPos chunk) {
    return MINIMUM_AMOUNT
        + random(seed ^ 0xF1E1D5L, chunk).nextInt(MAXIMUM_AMOUNT - MINIMUM_AMOUNT + 1);
  }
}
