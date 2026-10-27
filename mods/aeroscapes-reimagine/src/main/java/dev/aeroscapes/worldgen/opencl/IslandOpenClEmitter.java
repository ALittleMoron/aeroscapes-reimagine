package dev.aeroscapes.worldgen.opencl;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.gen.opencl.OpenCLCGenContext;
import com.ishland.c2me.opts.dfc.common.gen.opencl.OpenCLCGenFunctionContext;
import dev.aeroscapes.worldgen.c2me.IslandTerrainNode;

/** Emits only owned vertical composition; C2ME emits the five seeded noise graphs. */
public final class IslandOpenClEmitter {
  private IslandOpenClEmitter() {}

  public static String emit(
      IslandTerrainNode node, OpenCLCGenFunctionContext context, String storeTo) {
    OpenCLCGenContext global = context.getGlobalContext();
    int offset = global.allocGlobalDynamicData(node.terrain());
    String original = call(node.original(), context);
    String lower = call(node.lower(), context);
    String form = call(node.form(), context);
    String caves = call(node.caves(), context);
    String suffix = context.nextVarName();
    String column = "aero_column_" + suffix;
    String retained = "aero_retained_" + suffix;
    String added = "aero_added_" + suffix;
    String detail = "aero_detail_" + suffix;
    String liner = "aero_liner_" + suffix;
    return """
        global const double *%1$s = aeroscapes_column(ctx, %2$d);
        double %3$s = (ctx.y >= -64 && ctx.y < 304) ? %4$s : -1.0;
        if (ctx.y < 32) %3$s = fmax(%3$s, %5$s);
        double %6$s = aeroscapes_islands(%1$s, ctx.y, 0.0, false);
        if (%1$s[27] != 0.0 && %1$s[24] < 1.22)
            %6$s = fmax(%6$s, aeroscapes_shape(%1$s + 20, ctx.y));
        if (%1$s[28] != 0.0 && %6$s > %7$s) {
            double %8$s = %9$s;
            %6$s = aeroscapes_islands(%1$s, ctx.y, %8$s, true);
            if (%1$s[27] != 0.0) {
                double weight = fmax(0.0, fmin(1.0, %1$s[20] / 0.4));
                %6$s = fmax(%6$s, aeroscapes_shape(%1$s + 20, ctx.y)
                    + %8$s * weight * weight * (3.0 - 2.0 * weight));
            }
            bool %10$s = %1$s[27] != 0.0 && %1$s[24] < 1.08
                && ctx.y >= %1$s[25] - 12.0 && ctx.y <= %1$s[25] + 5.0;
            if (!%10$s) %6$s = fmin(%6$s, %11$s);
        }
        if (ctx.y < -116) %6$s = fmin(%6$s, (ctx.y + 125) / 12.0);
        %12$s = fmax(%3$s, %6$s);
        """
        .formatted(
            column,
            offset,
            retained,
            original,
            lower,
            added,
            Double.toHexString(-node.terrain().form().maxValue()),
            detail,
            form,
            liner,
            caves,
            storeTo);
  }

  private static String call(AstNode child, OpenCLCGenFunctionContext context) {
    OpenCLCGenContext global = context.getGlobalContext();
    return global.callDelegate(
        global.newMethod(child, context.getVariant(), child.getReturnType()),
        child.getReturnType());
  }
}
