package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.CarvingSupport;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import dev.aeroscapes.worldgen.IslandCaveHosts;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvingContext.class)
public abstract class IslandCarvingContextMixin implements IslandCarvingContext {
  @Unique private IslandCaveHosts.Range aeroscapes$layer;
  @Unique private net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator aeroscapes$generator;
  @Unique private CarvingSupport aeroscapes$support;
  @Unique private List<BoundingBox> aeroscapes$arenaShells;
  @Unique private List<BoundingBox> aeroscapes$dungeonFloors;
  @Unique private List<BoundingBox> aeroscapes$discoveryFootings;

  @Override
  public CarvingSupport aeroscapes$support(net.minecraft.world.level.chunk.ChunkAccess target) {
    if (aeroscapes$support == null) aeroscapes$support = new CarvingSupport(target);
    return aeroscapes$support;
  }

  @Override
  public void aeroscapes$prepareStructureProtection(
      StructureManager structures, ChunkAccess target) {
    if (aeroscapes$arenaShells != null) return;
    var arenas = new ArrayList<BoundingBox>();
    var floors = new ArrayList<BoundingBox>();
    var footings = new ArrayList<BoundingBox>();
    var chunk = target.getPos();
    for (var start :
        structures.startsForStructure(
            chunk,
            structure ->
                structure instanceof SupportedStructure supported
                    && (supported.protectsArena()
                        || supported.protectsRoomFloors()
                        || supported.protectsDiscoveryFootings()))) {
      var structure = (SupportedStructure) start.getStructure();
      if (structure.protectsDiscoveryFootings()
          && start.getPieces().getFirst() instanceof PoolElementStructurePiece root) {
        int grade = root.getBoundingBox().minY() + root.getGroundLevelDelta();
        int approach = SupportedStructure.DISCOVERY_APPROACH_LENGTH;
        var site = start.getBoundingBox();
        footings.add(
            new BoundingBox(
                site.minX() - approach,
                grade - approach - 1,
                site.minZ() - approach,
                site.maxX() + approach,
                grade + approach - 1,
                site.maxZ() + approach));
        for (var piece : start.getPieces())
          if (piece instanceof PoolElementStructurePiece pool) {
            var bounds = piece.getBoundingBox();
            int top =
                pool.getElement().getProjection()
                        == StructureTemplatePool.Projection.TERRAIN_MATCHING
                    ? grade + 1
                    : grade - 1;
            int bottom = Math.min(bounds.minY(), grade - 2) - 3;
            if (bottom <= top)
              footings.add(
                  new BoundingBox(
                      bounds.minX(), bottom, bounds.minZ(), bounds.maxX(), top, bounds.maxZ()));
          }
        continue;
      }
      for (StructurePiece piece : start.getPieces()) {
        var bounds = piece.getBoundingBox();
        if (structure.protectsArena() && SupportedStructure.isSaltArena(piece)) arenas.add(bounds);
        if (structure.protectsRoomFloors()
            && SupportedStructure.hasLargeRoomFloor(piece)
            && bounds.intersects(
                chunk.getMinBlockX(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX(),
                chunk.getMaxBlockZ())) floors.add(bounds);
      }
    }
    aeroscapes$arenaShells = List.copyOf(arenas);
    aeroscapes$dungeonFloors = List.copyOf(floors);
    aeroscapes$discoveryFootings = List.copyOf(footings);
  }

  @Override
  public boolean aeroscapes$protectsStructureTerrain(BlockPos point) {
    if (aeroscapes$arenaShells == null) return false;
    // Protect the natural material that passed the earlier terrain fit when carvers arrive.
    for (BoundingBox footing : aeroscapes$discoveryFootings)
      if (footing.isInside(point)) return true;
    int depth = SupportedStructure.ARENA_SHELL_DEPTH;
    for (BoundingBox room : aeroscapes$dungeonFloors) {
      if (point.getX() >= room.minX()
          && point.getX() <= room.maxX()
          && point.getY() >= room.minY() - depth
          && point.getY() < room.minY()
          && point.getZ() >= room.minZ()
          && point.getZ() <= room.maxZ()) return true;
    }
    for (BoundingBox room : aeroscapes$arenaShells) {
      if (point.getX() >= room.minX() - depth
          && point.getX() <= room.maxX() + depth
          && point.getY() >= room.minY() - depth
          && point.getY() <= room.maxY() + depth
          && point.getZ() >= room.minZ() - depth
          && point.getZ() <= room.maxZ() + depth
          && !room.isInside(point)) return true;
    }
    return false;
  }

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Retain the native generator for source columns.
  private void aeroscapes$generator(
      net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
      net.minecraft.core.RegistryAccess registries,
      net.minecraft.world.level.LevelHeightAccessor height,
      net.minecraft.world.level.levelgen.NoiseChunk noise,
      net.minecraft.world.level.levelgen.RandomState state,
      net.minecraft.world.level.levelgen.SurfaceRules.RuleSource surface,
      CallbackInfo callback) {
    aeroscapes$generator = generator;
  }

  @Override
  public net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator aeroscapes$generator() {
    return aeroscapes$generator;
  }

  @Override
  public IslandCaveHosts.Range aeroscapes$layer() {
    return aeroscapes$layer;
  }

  @Override
  public void aeroscapes$layer(IslandCaveHosts.Range layer) {
    aeroscapes$layer = layer;
  }
}
