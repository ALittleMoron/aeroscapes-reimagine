package dev.aeroscapes.construction;

import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import team.creative.littletiles.common.block.mc.BlockTile;

/** The native tile payload supplies geometry and the provider's material-volume mass weights. */
public final class LittleSableGeometry {
  private LittleSableGeometry() {}

  public static VoxelShape collision(BlockGetter level, BlockPos position, BlockState state) {
    return state.getCollisionShape(level, position, CollisionContext.empty());
  }

  public static VoxelShape occupied(VoxelShape collision) {
    return collision.toAabbs().stream().map(Shapes::create).reduce(Shapes.empty(), Shapes::or);
  }

  public static Vector3dc center(BlockGetter level, BlockPos position) {
    var tiles = BlockTile.loadBE(level, position);
    if (tiles == null) return new Vector3d(0.5);
    Vector3d weighted = new Vector3d();
    double total = 0;
    var grid = tiles.getGrid();
    for (var pair : tiles.allTiles()) {
      double density =
          ((BlockStateExtension) pair.value.getState())
              .sable$getProperty(PhysicsBlockPropertyTypes.MASS.get());
      for (var box : pair.value) {
        double mass = density * box.getPercentVolume(grid);
        weighted.add(
            (box.minX + box.maxX) * 0.5 / grid.count * mass,
            (box.minY + box.maxY) * 0.5 / grid.count * mass,
            (box.minZ + box.maxZ) * 0.5 / grid.count * mass);
        total += mass;
      }
    }
    return total > 0 ? weighted.div(total) : new Vector3d(0.5);
  }
}
