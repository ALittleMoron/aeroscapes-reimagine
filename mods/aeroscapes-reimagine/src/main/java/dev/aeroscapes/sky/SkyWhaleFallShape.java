package dev.aeroscapes.sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * Block layout of a long-dead sky whale, after the native carcass: a spine carried on rib arches, a
 * skull with two jaw bones, flippers and a tail. Local x is across the body, y is up from the
 * ground (or down into the rock for an underside find) and z runs from jaw to tail.
 */
public final class SkyWhaleFallShape {
  public static final int MAXIMUM_EXTENT = 21;
  public static final int MINIMUM_INTACT_BONE = 1;
  public static final int MAXIMUM_INTACT_BONE = 2;
  public static final int MINIMUM_CRACKED_BONE = 3;
  public static final int MAXIMUM_CRACKED_BONE = 6;
  private static final int SPINE_HEIGHT = 4;
  private static final double RIB_HALF_WIDTH = 4.5;
  private static final double RIB_SWEEP = 1.7278759594743864;
  private static final float MISSING_RIB_CHANCE = 0.2F;
  private static final float TAIL_GAP_CHANCE = 0.3F;

  private SkyWhaleFallShape() {}

  /** Which material a bone uses; most are ordinary bone blocks. */
  public enum Material {
    BONE,
    CRACKED_WHALE_BONE,
    WHALE_BONE
  }

  /**
   * One bone block. {@code grounded} bones lie on (or in) the host surface and follow its local
   * height; the others keep the rigid arch above it.
   */
  public record Bone(
      int x, int y, int z, Direction.Axis axis, boolean grounded, Material material) {}

  public static List<Bone> create(RandomSource random) {
    Map<Long, Bone> bones = new LinkedHashMap<>();
    // Spine carried at the top of the arches, sagging toward head and tail.
    for (int z = -9; z <= 8; z++) {
      int height = SPINE_HEIGHT - (Math.abs(z) > 6 ? 1 : 0);
      put(bones, new Bone(0, height, z, Direction.Axis.Z, false, Material.BONE));
    }
    // Rib arches from the spine down to the ground on both sides.
    for (int z = -8; z <= 6; z += 2) {
      double taper = 1.0 - Math.pow(Math.abs(z + 1) / 9.0, 2.0);
      double width = Math.max(2.0, RIB_HALF_WIDTH * taper + 0.5);
      int top = SPINE_HEIGHT - (Math.abs(z) > 6 ? 1 : 0);
      for (int side = -1; side <= 1; side += 2) {
        if (random.nextFloat() < MISSING_RIB_CHANCE) continue;
        boolean broken = random.nextFloat() < 0.25F;
        int steps = (int) Math.ceil((width + top) * 2.0);
        for (int step = 1; step <= steps; step++) {
          double angle = RIB_SWEEP * step / steps;
          if (broken && angle > RIB_SWEEP * 0.6) break;
          int x = (int) Math.round(side * width * Math.sin(angle));
          int y = Math.max(0, (int) Math.round(top * Math.cos(angle) + 0.4));
          put(bones, new Bone(x, y, z, Direction.Axis.Y, y == 0, Material.BONE));
        }
      }
    }
    // Skull resting on the ground, half sunk into it.
    for (int x = -3; x <= 3; x++) {
      for (int y = 0; y <= 3; y++) {
        for (int z = -15; z <= -10; z++) {
          double nx = x / 3.2;
          double ny = (y - 0.5) / 2.6;
          double nz = (z + 12.5) / 3.2;
          double distance = nx * nx + ny * ny + nz * nz;
          if (distance <= 1.0 && distance >= 0.45) {
            put(bones, new Bone(x, y, z, Direction.Axis.Y, y == 0, Material.BONE));
          }
        }
      }
    }
    // Two jaw bones converging toward the front.
    for (int z = -21; z <= -15; z++) {
      int half = z <= -19 ? 1 : 2;
      put(bones, new Bone(-half, 0, z, Direction.Axis.Z, true, Material.BONE));
      put(bones, new Bone(half, 0, z, Direction.Axis.Z, true, Material.BONE));
    }
    // Flipper bones splayed on the ground below the chest.
    for (int side = -1; side <= 1; side += 2) {
      for (int step = 0; step < 4; step++) {
        put(
            bones,
            new Bone(side * (5 + step), 0, -5 + step, Direction.Axis.X, true, Material.BONE));
      }
    }
    // Tail vertebrae trailing on the ground, with gaps where they were scattered.
    for (int z = 9; z <= 18; z++) {
      if (z > 11 && random.nextFloat() < TAIL_GAP_CHANCE) continue;
      put(bones, new Bone(0, 0, z, Direction.Axis.Z, true, Material.BONE));
    }
    put(bones, new Bone(-1, 0, 18, Direction.Axis.X, true, Material.BONE));
    put(bones, new Bone(1, 0, 18, Direction.Axis.X, true, Material.BONE));
    return new ArrayList<>(bones.values());
  }

  /** Local z points along {@code facing}; local x is to its right. */
  public static BlockPos rotate(
      int originX, int originZ, int localX, int localZ, Direction facing) {
    return switch (facing) {
      case SOUTH -> new BlockPos(originX - localX, 0, originZ + localZ);
      case WEST -> new BlockPos(originX - localZ, 0, originZ - localX);
      case EAST -> new BlockPos(originX + localZ, 0, originZ + localX);
      default -> new BlockPos(originX + localX, 0, originZ - localZ);
    };
  }

  /**
   * Chooses the whale-bone blocks among bones that will actually be placed, so a partly blocked
   * site still holds its modest share of whale bone.
   */
  public static List<Bone> withMaterials(List<Bone> bones, RandomSource random) {
    int intact =
        MINIMUM_INTACT_BONE + random.nextInt(MAXIMUM_INTACT_BONE - MINIMUM_INTACT_BONE + 1);
    int cracked =
        MINIMUM_CRACKED_BONE + random.nextInt(MAXIMUM_CRACKED_BONE - MINIMUM_CRACKED_BONE + 1);
    var result = new ArrayList<>(bones);
    // Intact whale bone survives in the heavy skull and spine; cracked bone anywhere.
    List<Integer> heavy = new ArrayList<>();
    List<Integer> any = new ArrayList<>();
    for (int index = 0; index < result.size(); index++) {
      var bone = result.get(index);
      if (bone.x() == 0 && !bone.grounded() || bone.z() <= -10 && bone.z() >= -15) heavy.add(index);
      any.add(index);
    }
    for (int placed = 0; placed < intact && !heavy.isEmpty(); placed++) {
      int index = heavy.remove(random.nextInt(heavy.size()));
      any.remove(Integer.valueOf(index));
      result.set(index, with(result.get(index), Material.WHALE_BONE));
    }
    for (int placed = 0; placed < cracked && !any.isEmpty(); placed++) {
      int index = any.remove(random.nextInt(any.size()));
      result.set(index, with(result.get(index), Material.CRACKED_WHALE_BONE));
    }
    return result;
  }

  private static Bone with(Bone bone, Material material) {
    return new Bone(bone.x(), bone.y(), bone.z(), bone.axis(), bone.grounded(), material);
  }

  private static void put(Map<Long, Bone> bones, Bone bone) {
    long key =
        ((long) bone.x() & 0xFFFFL) << 32 | ((long) bone.y() & 0xFFFFL) << 16 | bone.z() & 0xFFFFL;
    bones.putIfAbsent(key, bone);
  }
}
