package dev.aeroscapes.sky.fauna;

import java.util.ArrayList;
import java.util.List;

/** Shared geometry in model units, with Y increasing from the crown towards the feet. */
public final class CloudJellyAnatomy {
  public static final int SIZE_UNITS = 16;

  public enum Form {
    FRINGE("fringe"),
    NETTLE("nettle"),
    MANE("mane"),
    LANTERN("lantern");

    private final String id;

    Form(String id) {
      this.id = id;
    }

    public String id() {
      return id;
    }

    public static Form byId(String id) {
      for (Form form : values()) {
        if (form.id.equals(id)) return form;
      }
      return FRINGE;
    }
  }

  public record ShellBox(float minX, float top, float minZ, float maxX, float bottom, float maxZ) {}

  public record Segment(float top, float bottom, float x, float z, float width, float depth) {}

  public record Strand(List<Segment> segments, boolean ribbon) {}

  public record Shape(float height, List<ShellBox> bell, List<Strand> strands) {}

  private static final Shape FRINGE = build(Form.FRINGE);
  private static final Shape NETTLE = build(Form.NETTLE);
  private static final Shape MANE = build(Form.MANE);
  private static final Shape LANTERN = build(Form.LANTERN);

  private CloudJellyAnatomy() {}

  public static Shape of(Form form) {
    return switch (form) {
      case FRINGE -> FRINGE;
      case NETTLE -> NETTLE;
      case MANE -> MANE;
      case LANTERN -> LANTERN;
    };
  }

  private static Shape build(Form form) {
    List<ShellBox> bell = new ArrayList<>();
    List<Strand> strands = new ArrayList<>();
    switch (form) {
      case FRINGE -> {
        dome(
            bell,
            new float[][] {{1, 3.1F}, {2.4F, 5}, {4, 6.4F}, {5.4F, 7.3F}, {6.65F, 7.9F}},
            false);
        scallops(bell, 16, 6.55F, 6.65F, 1.45F, 0.34F);
        ring(
            strands,
            form,
            6.15F,
            6.18F,
            new int[] {27, 10, 18, 23, 8, 15, 29, 11, 20, 14, 25, 9, 18, 22, 12, 26, 15, 19});
      }
      case NETTLE -> {
        dome(bell, new float[][] {{.9F, 3}, {1.9F, 5.2F}, {3.1F, 6.8F}, {4.3F, 8}}, true);
        scallops(bell, 16, 6.85F, 4.3F, 1.95F, .8F);
        ring(
            strands,
            form,
            3.75F,
            6.15F,
            new int[] {26, 13, 18, 28, 10, 20, 15, 22, 11, 24, 17, 13});
        int[] lengths = {30, 24, 19};
        for (int i = 0; i < lengths.length; i++) {
          double angle = Math.TAU * i / lengths.length + .28;
          strands.add(
              strand(
                  form,
                  12 + i,
                  (float) (2.05 * Math.cos(angle)),
                  (float) (2.05 * Math.sin(angle)),
                  3.75F,
                  lengths[i],
                  true));
        }
      }
      case MANE -> {
        dome(bell, new float[][] {{.65F, 4.1F}, {1.4F, 6.4F}, {2.3F, 7.65F}, {3.4F, 8}}, false);
        scallops(bell, 18, 6.9F, 3.4F, 1.75F, .75F);
        // Tufts have independent spacing and reach; their exposed spaces continue up to the rim.
        double[] centers = {.25, 1.48, 2.76, 3.94, 5.31};
        int[][] lengths = {
          {19, 29, 21, 11}, {15, 22, 9}, {27, 18, 31, 13}, {12, 23, 16}, {20, 10, 26, 17, 13}
        };
        for (int tuft = 0; tuft < centers.length; tuft++) {
          for (int j = 0; j < lengths[tuft].length; j++) {
            double a = centers[tuft] + (j - (lengths[tuft].length - 1) * .5) * .165;
            double radius = 5.4 + j % 2 * 1.15;
            strands.add(
                strand(
                    form,
                    strands.size(),
                    (float) (radius * Math.cos(a)),
                    (float) (radius * Math.sin(a)),
                    3,
                    lengths[tuft][j],
                    false));
          }
        }
        strands.add(strand(form, 19, -1.4F, -3.9F, 3, 13, false));
        strands.add(strand(form, 20, 2.0F, 2.7F, 3, 22, false));
        strands.add(strand(form, 21, -.4F, 1.1F, 3, 18, false));
      }
      case LANTERN -> {
        // A narrow crown and deep mantle flare above a pinched, hanging inner collar.
        dome(
            bell,
            new float[][] {
              {1.1F, 2.5F},
              {2.7F, 4.15F},
              {4.8F, 5.65F},
              {6.8F, 7},
              {8.2F, 8},
              {9.1F, 6.8F},
              {10, 5.25F}
            },
            false);
        scallops(bell, 12, 6.5F, 8.2F, 1.25F, .62F);
        ring(strands, form, 8, 5.45F, new int[] {12, 21, 14, 24, 10, 16, 22, 11, 19, 15});
        int[][] lengths = {{26, 19}, {17, 24}, {28, 20}};
        for (int i = 0; i < 3; i++)
          for (int j = 0; j < 2; j++) {
            double a = Math.TAU * i / 3 + .4;
            double side = (j - .5) * 1.65;
            strands.add(
                strand(
                    form,
                    10 + i * 2 + j,
                    (float) (3.5 * Math.cos(a) - side * Math.sin(a)),
                    (float) (3.5 * Math.sin(a) + side * Math.cos(a)),
                    9.55F,
                    lengths[i][j],
                    true));
          }
      }
    }
    float height = 0;
    for (Strand strand : strands) height = Math.max(height, strand.segments().getLast().bottom());
    return new Shape(height, List.copyOf(bell), List.copyOf(strands));
  }

  private static void dome(List<ShellBox> boxes, float[][] tiers, boolean rippled) {
    float top = 0;
    for (int i = 0; i < tiers.length; i++) {
      float bottom = tiers[i][0], radius = tiers[i][1];
      int chords = i == 0 ? 3 : i == 1 ? 5 : 7;
      for (int j = 0; j < chords; j++) {
        float x = radius * (2F * j / chords - 1), end = radius * (2F * (j + 1) / chords - 1);
        float mid = (x + end) * .5F;
        float halfDepth = Math.round(Math.sqrt(radius * radius - mid * mid) * 2) * .5F;
        if (rippled && i == tiers.length - 1)
          halfDepth = Math.min(8, halfDepth + (float) (.25 * Math.cos(j * 2)));
        boxes.add(new ShellBox(x, top, -halfDepth, end, bottom, halfDepth));
      }
      top = bottom;
    }
  }

  private static void scallops(
      List<ShellBox> boxes, int count, float radius, float top, float width, float drop) {
    for (int i = 0; i < count; i++) {
      double a = Math.TAU * (i + .5) / count;
      float x = (float) (radius * Math.cos(a)), z = (float) (radius * Math.sin(a));
      float d = (float) (.95 + .25 * Math.abs(Math.sin(a)));
      boxes.add(
          new ShellBox(
              x - width / 2,
              top - .32F,
              z - d / 2,
              x + width / 2,
              top + drop + (float) (.34 * Math.cos(i * 1.41)),
              z + d / 2));
    }
  }

  private static void ring(
      List<Strand> strands, Form form, float rootHeight, float radius, int[] lengths) {
    for (int i = 0; i < lengths.length; i++) {
      double a = Math.TAU * (i + .16 * Math.sin(4 * i + 1)) / lengths.length;
      strands.add(
          strand(
              form,
              i,
              (float) (radius * Math.cos(a)),
              (float) (radius * Math.sin(a)),
              rootHeight,
              lengths[i],
              false));
    }
  }

  private static Strand strand(
      Form form, int i, float rootX, float rootZ, float top, int length, boolean ribbon) {
    int count = Math.round(length / (ribbon ? 2.65F : 3.45F));
    List<Segment> segments = new ArrayList<>(count);
    double phase = i * 1.3;
    for (int j = 0; j < count; j++) {
      double t = (double) j / (count - 1), x, z, w, d;
      if (ribbon) {
        x = rootX + 1.10 * Math.sin(t * 6 + phase) - .7 * Math.sin(phase) + .42 * t;
        z = rootZ + .78 * Math.sin(t * 5.5 + 1 + phase) - .45 * Math.sin(1 + phase);
        double fold = .5 + .5 * Math.cos(j * 1.65 + i);
        w = (1.7 + .85 * fold) * (1 - .51 * t);
        d = (1.05 + .6 * (1 - fold)) * (1 - .56 * t);
        if (form == Form.LANTERN) {
          w *= .65;
          d *= .72;
        }
      } else {
        x = rootX + .47 * (Math.sin(t * 5.6 + phase) - Math.sin(phase)) + .8 * t;
        z = rootZ + .42 * (Math.sin(t * 5 + .69 * phase) - Math.sin(.69 * phase)) - .28 * t;
        double base =
            switch (form) {
              case FRINGE -> .84;
              case NETTLE -> .69;
              case MANE -> .72;
              case LANTERN -> .64;
            };
        w = (base + .07 * Math.cos(phase)) * (1 - .32 * t);
        d = w * .88;
      }
      if (j > 0) {
        Segment prev = segments.getLast();
        // Cap the displacement between sections so even a thin tip meets the preceding section.
        double dx = (w + prev.width()) * .37, dz = (d + prev.depth()) * .37;
        x = Math.clamp(x, prev.x() - dx, prev.x() + dx);
        z = Math.clamp(z, prev.z() - dz, prev.z() + dz);
      }
      x = Math.clamp(x, -8 + w / 2, 8 - w / 2);
      z = Math.clamp(z, -8 + d / 2, 8 - d / 2);
      // The lower chain bends at its first section. Seat it in the upper section throughout a
      // pulse.
      float joint = j == count / 2 ? .16F : 0;
      segments.add(
          new Segment(
              top + (float) length * j / count - joint,
              top + (float) length * (j + 1) / count,
              (float) x,
              (float) z,
              (float) w,
              (float) d));
    }
    return new Strand(List.copyOf(segments), ribbon);
  }
}
