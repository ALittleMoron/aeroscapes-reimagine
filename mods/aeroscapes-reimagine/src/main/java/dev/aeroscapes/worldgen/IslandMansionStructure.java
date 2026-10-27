package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Gives native mansion foundations a natural bed beneath their actual lowest-floor mask. */
public final class IslandMansionStructure extends WoodlandMansionStructure {
  public static final MapCodec<IslandMansionStructure> CODEC =
      simpleCodec(IslandMansionStructure::new);
  private static final DeferredRegister<StructureType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<StructureType<?>, StructureType<IslandMansionStructure>>
      TYPE = TYPES.register("island_mansion", () -> () -> CODEC);

  public IslandMansionStructure(StructureSettings settings) {
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
    PiecesContainer pieces = builder.build();
    BoundingBox bounds = pieces.calculateBoundingBox();
    if (context.heightAccessor().isOutsideBuildHeight(bounds.minY())
        || context.heightAccessor().isOutsideBuildHeight(bounds.maxY())) return Optional.empty();
    Map<Long, NoiseColumn> columns = new HashMap<>();
    Set<Long> occupied = occupiedFloor(context, pieces, bounds.minY(), columns);
    if (occupied.isEmpty()) throw new IllegalStateException("Pinned mansion has no lowest floor");
    List<Long> ordered = new ArrayList<>(occupied);
    ordered.sort(
        Comparator.comparingInt((Long key) -> ChunkPos.getX(key))
            .thenComparingInt(key -> ChunkPos.getZ(key)));
    // Necessary, cheap checks before any dense noise volume is allocated.
    for (int i = 0; i < 8; i++) {
      long key = ordered.get(i * (ordered.size() - 1) / 7);
      NoiseColumn column = column(context, columns, ChunkPos.getX(key), ChunkPos.getZ(key));
      if (!hasBed(context, column, bounds.minY())) return Optional.empty();
    }
    if (!(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)) {
      throw new IllegalStateException("Island mansions require the pinned noise generator");
    }
    Map<Long, Set<Long>> tiles = new HashMap<>();
    for (long key : occupied) {
      long tile =
          ChunkPos.asLong(
              Math.floorDiv(ChunkPos.getX(key), 64), Math.floorDiv(ChunkPos.getZ(key), 64));
      tiles.computeIfAbsent(tile, ignored -> new HashSet<>()).add(key);
    }
    for (Set<Long> remaining : tiles.values()) {
      int left = remaining.stream().mapToInt(ChunkPos::getX).min().orElseThrow();
      int right = remaining.stream().mapToInt(ChunkPos::getX).max().orElseThrow();
      int front = remaining.stream().mapToInt(ChunkPos::getZ).min().orElseThrow();
      int back = remaining.stream().mapToInt(ChunkPos::getZ).max().orElseThrow();
      int minimum = context.heightAccessor().getMinBuildHeight() + 1;
      for (int top = bounds.minY() - 1; top >= minimum && !remaining.isEmpty(); top -= 30) {
        int bottom = Math.max(minimum, top - 29);
        int slabTop = top;
        var volume =
            new NativeTerrainVolume(
                noise,
                context.randomState(),
                new BoundingBox(left, bottom, front, right, top, back));
        remaining.removeIf(
            key -> {
              for (int y = slabTop; y >= bottom; y--) {
                if (volume.solid(ChunkPos.getX(key), y, ChunkPos.getZ(key))) return true;
              }
              return false;
            });
      }
      if (!remaining.isEmpty()) return Optional.empty();
    }
    return Optional.of(new GenerationStub(stub.position(), Either.right(builder)));
  }

  private static Set<Long> occupiedFloor(
      GenerationContext context,
      PiecesContainer pieces,
      int floor,
      Map<Long, NoiseColumn> columns) {
    Set<Long> footprint = new LinkedHashSet<>();
    Map<Long, Boolean> authored = new HashMap<>();
    // The pinned 73 native templates each have one palette. Compose their exact
    // floor records in placement order: later AIR must erase an earlier floor.
    for (var piece : pieces.pieces()) {
      if (piece.getBoundingBox().minY() != floor) continue;
      if (!(piece instanceof TemplateStructurePiece template)) {
        throw new IllegalStateException("Pinned mansion contains a non-template piece");
      }
      var bounds = piece.getBoundingBox();
      for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) footprint.add(ChunkPos.asLong(x, z));
      }
      CompoundTag saved = template.template().save(new CompoundTag());
      if (saved.contains("palettes")) {
        throw new IllegalStateException("Pinned mansion floor unexpectedly has multiple palettes");
      }
      ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
      for (Tag entry : saved.getList("blocks", Tag.TAG_COMPOUND)) {
        CompoundTag block = (CompoundTag) entry;
        ListTag position = block.getList("pos", Tag.TAG_INT);
        if (position.getInt(1) != 0) continue;
        String name = palette.getCompound(block.getInt("state")).getString("Name");
        if (name.equals("minecraft:structure_void")) continue;
        if (name.equals("minecraft:structure_block")) {
          throw new IllegalStateException("Pinned mansion has a data marker in its lowest floor");
        }
        BlockPos transformed =
            template
                .templatePosition()
                .offset(
                    StructureTemplate.calculateRelativePosition(
                        template.placeSettings(),
                        new BlockPos(position.getInt(0), 0, position.getInt(2))));
        boolean air =
            name.equals("minecraft:air")
                || name.equals("minecraft:cave_air")
                || name.equals("minecraft:void_air");
        authored.put(ChunkPos.asLong(transformed.getX(), transformed.getZ()), !air);
      }
    }
    Set<Long> occupied = new HashSet<>();
    for (long key : footprint) {
      Boolean explicit = authored.get(key);
      if (Boolean.TRUE.equals(explicit)
          || explicit == null
              && !column(context, columns, ChunkPos.getX(key), ChunkPos.getZ(key))
                  .getBlock(floor)
                  .isAir()) occupied.add(key);
    }
    return occupied;
  }

  private static NoiseColumn column(
      GenerationContext context, Map<Long, NoiseColumn> columns, int x, int z) {
    return columns.computeIfAbsent(
        ChunkPos.asLong(x, z),
        ignored ->
            context
                .chunkGenerator()
                .getBaseColumn(x, z, context.heightAccessor(), context.randomState()));
  }

  @SuppressWarnings(
      "deprecation") // Match the pinned native afterPlace liquid-block stop condition.
  private static boolean hasBed(GenerationContext context, NoiseColumn column, int floor) {
    for (int y = floor - 1; y > context.heightAccessor().getMinBuildHeight(); y--) {
      var state = column.getBlock(y);
      if (!state.isAir() && !state.liquid()) return NativeTerrainVolume.solid(state);
    }
    return false;
  }

  @Override
  @SuppressWarnings("deprecation") // Preserve the pinned native afterPlace liquid-block predicate.
  public void afterPlace(
      WorldGenLevel level,
      StructureManager manager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox chunkBounds,
      ChunkPos chunk,
      PiecesContainer pieces) {
    BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    int minimum = level.getMinBuildHeight();
    BoundingBox bounds = pieces.calculateBoundingBox();
    int floor = bounds.minY();
    for (int x = chunkBounds.minX(); x <= chunkBounds.maxX(); x++) {
      for (int z = chunkBounds.minZ(); z <= chunkBounds.maxZ(); z++) {
        position.set(x, floor, z);
        if (!level.isEmptyBlock(position)
            && bounds.isInside(position)
            && pieces.isInsidePiece(position)
            && hasLiveBed(level, position, floor, minimum)) {
          // A raw-predicted bed can be absent at placement.
          // Keep native floors, but do not extend that unsupported column into the void.
          for (int y = floor - 1; y > minimum; y--) {
            position.setY(y);
            if (!level.isEmptyBlock(position) && !level.getBlockState(position).liquid()) break;
            level.setBlock(position, Blocks.COBBLESTONE.defaultBlockState(), 2);
          }
        }
      }
    }
  }

  @SuppressWarnings("deprecation") // Match the native first nonempty, non-liquid stopping block.
  private static boolean hasLiveBed(
      WorldGenLevel level, BlockPos.MutableBlockPos position, int floor, int minimum) {
    for (int y = floor - 1; y > minimum; y--) {
      position.setY(y);
      if (!level.isEmptyBlock(position) && !level.getBlockState(position).liquid()) return true;
    }
    return false;
  }

  @Override
  public StructureType<?> type() {
    return TYPE.get();
  }
}
