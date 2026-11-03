package dev.aeroscapes.aquatic;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.aeroscapes.mixin.aquatic.AquaticStructureAccess;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Fits the native small skeleton assembly on real water-covered basin terrain. */
public final class AquaticWhaleFall extends Structure {
  public static final MapCodec<AquaticWhaleFall> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      Structure.DIRECT_CODEC.fieldOf("delegate").forGetter(value -> value.delegate))
                  .apply(instance, AquaticWhaleFall::new));
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final net.neoforged.neoforge.registries.DeferredHolder<
          StructureType<?>, StructureType<AquaticWhaleFall>>
      TYPE = TYPES.register("basin_whale_fall", () -> () -> CODEC);
  private final Structure delegate;

  public AquaticWhaleFall(Structure delegate) {
    super(
        new StructureSettings(
            delegate.biomes(), delegate.spawnOverrides(), delegate.step(), TerrainAdjustment.NONE));
    this.delegate = delegate;
  }

  public static void register(IEventBus bus) {
    TYPES.register(bus);
  }

  @Override
  protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    IslandTerrain terrain = IslandTerrain.from(context.randomState());
    if (terrain == null) return delegate.findValidGenerationPoint(context);
    var nativeStub = ((AquaticStructureAccess) delegate).aeroscapes$assemble(context);
    if (nativeStub.isEmpty()) return Optional.empty();
    var builder = nativeStub.orElseThrow().getPiecesBuilder();
    var pieces = builder.build();
    if (pieces.isEmpty()) return Optional.empty();
    var bounds = builder.getBoundingBox();
    if (bounds.getXSpan() > 65 || bounds.getZSpan() > 65) return Optional.empty();
    var center = bounds.getCenter();
    var basin = terrain.basin(center.getX(), center.getZ());
    if (basin == null) return Optional.empty();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    int lowest = Integer.MAX_VALUE;
    int highest = Integer.MIN_VALUE;
    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
      for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
        if (!basin.water(x, basin.waterline() - 12, z)) return Optional.empty();
        int columnX = x;
        int columnZ = z;
        var column =
            columns.computeIfAbsent(
                ChunkPos.asLong(x, z),
                ignored ->
                    context
                        .chunkGenerator()
                        .getBaseColumn(
                            columnX, columnZ, context.heightAccessor(), context.randomState()));
        int floor = basin.waterline();
        if (!column.getBlock(floor).getFluidState().is(FluidTags.WATER)) return Optional.empty();
        while (floor > basin.floor(x, z) - 2
            && column.getBlock(floor).getFluidState().is(FluidTags.WATER)) floor--;
        if (!column
            .getBlock(floor)
            .isFaceSturdy(EmptyBlockGetter.INSTANCE, new BlockPos(x, floor, z), Direction.UP))
          return Optional.empty();
        lowest = Math.min(lowest, floor);
        highest = Math.max(highest, floor);
        if (highest - lowest > 3) return Optional.empty();
      }
    }
    if (highest - lowest > 3 || highest + bounds.getYSpan() + 1 >= basin.waterline())
      return Optional.empty();
    int deltaY = highest + 1 - bounds.minY();
    BlockPos anchor = new BlockPos(center.getX(), highest + 1, center.getZ());
    if (!context
        .validBiome()
        .test(
            context
                .chunkGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(anchor.getX()), QuartPos.fromBlock(anchor.getY()),
                    QuartPos.fromBlock(anchor.getZ()), context.randomState().sampler())))
      return Optional.empty();
    for (var piece : pieces.pieces()) {
      piece.move(0, deltaY, 0);
      if (piece instanceof PoolElementStructurePiece pool)
        pool.getJunctions()
            .replaceAll(
                junction ->
                    new JigsawJunction(
                        junction.getSourceX(),
                        junction.getSourceGroundY() + deltaY,
                        junction.getSourceZ(),
                        junction.getDeltaY(),
                        junction.getDestProjection()));
    }
    return Optional.of(new GenerationStub(anchor, Either.right(builder)));
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
