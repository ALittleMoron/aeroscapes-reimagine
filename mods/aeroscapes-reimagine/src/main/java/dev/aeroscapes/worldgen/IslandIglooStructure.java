package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.IglooStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Checks every native igloo template at its exact late, rotated height-query position. */
public final class IslandIglooStructure extends IglooStructure {
  public static final MapCodec<IslandIglooStructure> CODEC = simpleCodec(IslandIglooStructure::new);
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandIglooStructure>> TYPE =
      TYPES.register("island_igloo", () -> () -> CODEC);

  public IslandIglooStructure(StructureSettings settings) {
    super(settings);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
    Optional<GenerationStub> nativeStub = super.findGenerationPoint(context);
    if (nativeStub.isEmpty()) return Optional.empty();
    GenerationStub stub = nativeStub.orElseThrow();
    BlockPos anchor = stub.position();
    if (!context
        .validBiome()
        .test(
            context
                .biomeSource()
                .getNoiseBiome(
                    QuartPos.fromBlock(anchor.getX()), QuartPos.fromBlock(anchor.getY()),
                    QuartPos.fromBlock(anchor.getZ()), context.randomState().sampler())))
      return Optional.empty();
    var builder = stub.getPiecesBuilder();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    for (StructurePiece piece : builder.build().pieces()) {
      if (!(piece instanceof TemplateStructurePiece template)) {
        throw new IllegalStateException("Pinned igloo contains a non-template piece");
      }
      BlockPos origin = template.templatePosition();
      // Native makePosition adds only the template's X/Z offset to this chunk origin.
      int offsetX = origin.getX() - context.chunkPos().getMinBlockX();
      int offsetZ = origin.getZ() - context.chunkPos().getMinBlockZ();
      BlockPos query =
          origin.offset(
              StructureTemplate.calculateRelativePosition(
                  template.placeSettings(), new BlockPos(3 - offsetX, 0, -offsetZ)));
      int height =
          context
              .chunkGenerator()
              .getBaseHeight(
                  query.getX(),
                  query.getZ(),
                  Heightmap.Types.WORLD_SURFACE_WG,
                  context.heightAccessor(),
                  context.randomState());
      int shift = height - 90 - 1;
      var bounds = template.getBoundingBox();
      int floor = bounds.minY() + shift;
      if (context.heightAccessor().isOutsideBuildHeight(floor - 1)
          || context.heightAccessor().isOutsideBuildHeight(bounds.maxY() + shift)) {
        return Optional.empty();
      }
      for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
          final int columnX = x;
          final int columnZ = z;
          NoiseColumn column =
              columns.computeIfAbsent(
                  ChunkPos.asLong(x, z),
                  ignored ->
                      context
                          .chunkGenerator()
                          .getBaseColumn(
                              columnX, columnZ, context.heightAccessor(), context.randomState()));
          if (!NativeTerrainVolume.solid(column.getBlock(floor - 1))) return Optional.empty();
        }
      }
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
