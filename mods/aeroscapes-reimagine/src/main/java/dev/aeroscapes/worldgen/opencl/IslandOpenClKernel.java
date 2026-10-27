package dev.aeroscapes.worldgen.opencl;

/** Independent kernel extensions inserted into the original module's in-memory program. */
public final class IslandOpenClKernel {
  private static final String AQUIFER_DECLARATION =
      "static aquifer_result_t aquifer_sample(const sample_int32_ctx_t ctx, const double density)";
  private static final String NOISE_KERNEL = "#ifdef DF_COMPILE_NOISE_KERNEL";

  private IslandOpenClKernel() {}

  public static String adapt(String source, int offset) {
    String renamed =
        replaceOnce(
            source,
            AQUIFER_DECLARATION,
            geometry()
                + AQUIFER_DECLARATION.replace("aquifer_sample(", "aeroscapes_original_aquifer("));
    return "#pragma OPENCL FP_CONTRACT OFF\n"
        + "#define AEROSCAPES_CITY_POST(value, ctx) ((value) += aeroscapes_city((ctx), "
        + offset
        + "))\n"
        + replaceOnce(renamed, NOISE_KERNEL, aquifer(offset) + NOISE_KERNEL);
  }

  private static String replaceOnce(String source, String target, String replacement) {
    int position = source.indexOf(target);
    if (position < 0 || source.indexOf(target, position + target.length()) >= 0)
      throw new IllegalStateException("Pinned C2ME OpenCL kernel interface changed: " + target);
    return source.substring(0, position)
        + replacement
        + source.substring(position + target.length());
  }

  public static String geometry() {
    return """

        static global const double *aeroscapes_column(const sample_int32_ctx_t ctx, const int index) {
            if (!ctx.rw_data) { __builtin_trap(); return NULL; }
            global const int *header = df_data_offset_global(ctx.rw_data, index);
            if (!header) { __builtin_trap(); return NULL; }
            int x = ctx.x - header[0];
            int z = ctx.z - header[1];
            if (x < 0 || z < 0 || x >= header[2] || z >= header[3]) {
                __builtin_trap(); return NULL;
            }
            return (global const double *)((global const char *)header + 16
                + ((size_t)z * header[2] + x) * 240);
        }

        static double aeroscapes_shape(global const double *shape, const int y) {
            if (shape[0] == -1.0 && shape[1] == 0.0 && shape[2] == 0.0 && shape[3] == 1.0)
                return -1.0;
            return fmin(shape[0], fmin((shape[1] - y) / shape[3], (y - shape[2]) / shape[3]));
        }

        static double aeroscapes_detailed_shape(global const double *shape, const int y,
                const double detail, const bool detailed) {
            if (!detailed) return aeroscapes_shape(shape, y);
            if (shape[0] == -1.0 && shape[1] == 0.0 && shape[2] == 0.0 && shape[3] == 1.0)
                return -1.0;
            return fmin(shape[0], aeroscapes_shape(shape, y) + detail);
        }

        static double aeroscapes_islands(global const double *column, const int y,
                const double detail, const bool detailed) {
            return fmax(aeroscapes_detailed_shape(column + 16, y, detail, detailed),
                fmax(fmax(aeroscapes_detailed_shape(column, y, detail, detailed),
                          aeroscapes_detailed_shape(column + 4, y, detail, detailed)),
                     fmax(aeroscapes_detailed_shape(column + 8, y, detail, detailed),
                          aeroscapes_detailed_shape(column + 12, y, detail, detailed))));
        }

        static double aeroscapes_city(const sample_int32_ctx_t ctx, const int index) {
            if (!ctx.rw_data) return 0.0;
            global const int *header = df_data_offset_global(ctx.rw_data, index);
            global const double *column = aeroscapes_column(ctx, index);
            global const int *cities = (global const int *)(column + 29);
            global const double *bounds =
                (global const double *)((global const char *)header + cities[0]);
            double contribution = 0.0;
            for (int city = 0; city < cities[1]; city++) {
                double opening = fmin((ctx.y - bounds[city * 2]) / 2.0,
                    (bounds[city * 2 + 1] - ctx.y) / 4.0);
                double value = opening > 0.0 ? -fmin(6.0, opening * 6.0) : 0.0;
                contribution = fmin(contribution, value);
            }
            return contribution;
        }
        """;
  }

  private static String aquifer(int offset) {
    return """

        static aquifer_result_t aquifer_sample(const sample_int32_ctx_t ctx, const double density) {
            global const double *column = aeroscapes_column(ctx, %d);
            if (column[27] != 0.0) {
                if (column[24] < 1.0 && ctx.y > column[25] && ctx.y <= column[26])
                    return (aquifer_result_t){ .blockState = density > 0.0 ? BLOCK_NULL : BLOCK_WATER,
                        .needsFluidTick = false };
                if (column[24] < 1.05 && ctx.y <= column[25] && ctx.y >= column[25] - 12.0)
                    return (aquifer_result_t){ .blockState = density > 0.0 ? BLOCK_NULL : BLOCK_DEFAULT_BLOCK,
                        .needsFluidTick = false };
            }
            return aeroscapes_original_aquifer(ctx, density);
        }
        """
        .formatted(offset);
  }
}
