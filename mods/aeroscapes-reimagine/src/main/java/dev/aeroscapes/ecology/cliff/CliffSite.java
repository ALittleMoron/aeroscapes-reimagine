package dev.aeroscapes.ecology.cliff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public record CliffSite(
    Kind kind, BlockPos anchor, Direction facing, long stockedDay, long flightDay, boolean lit) {
  public static final Codec<CliffSite> CODEC =
      RecordCodecBuilder.create(
          fields ->
              fields
                  .group(
                      Kind.CODEC.fieldOf("kind").forGetter(CliffSite::kind),
                      BlockPos.CODEC.fieldOf("anchor").forGetter(CliffSite::anchor),
                      Direction.CODEC
                          .validate(
                              direction ->
                                  direction.getAxis().isHorizontal()
                                      ? DataResult.success(direction)
                                      : DataResult.error(() -> "Cliff site must face horizontally"))
                          .fieldOf("facing")
                          .forGetter(CliffSite::facing),
                      Codec.LONG
                          .optionalFieldOf("stocked_day", -1L)
                          .forGetter(CliffSite::stockedDay),
                      Codec.LONG.optionalFieldOf("flight_day", -1L).forGetter(CliffSite::flightDay),
                      Codec.BOOL.optionalFieldOf("lit", false).forGetter(CliffSite::lit))
                  .apply(fields, CliffSite::new));

  CliffSite(Kind kind, BlockPos anchor, Direction facing) {
    this(kind, anchor, facing, -1L, -1L, false);
  }

  CliffSite withLight() {
    return new CliffSite(kind, anchor, facing, stockedDay, flightDay, true);
  }

  CliffSite staffedOn(long day) {
    return new CliffSite(kind, anchor, facing, day, flightDay, lit);
  }

  CliffSite flownOn(long day) {
    return new CliffSite(kind, anchor, facing, stockedDay, day, lit);
  }

  public BlockPos ledge(int offset) {
    return anchor.relative(facing.getClockWise(), offset);
  }

  public enum Kind implements StringRepresentable {
    ROOTS("roots"),
    GULLS("gulls"),
    BATS("bats");

    static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
    private final String name;

    Kind(String name) {
      this.name = name;
    }

    @Override
    public String getSerializedName() {
      return name;
    }
  }
}
