package dev.aeroscapes.worldgen.c2me;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.McToAst;
import com.ishland.c2me.opts.dfc.common.ast.misc.DelegateNode;
import com.ishland.c2me.opts.dfc.common.ast.opto.OptoPasses;
import com.ishland.c2me.opts.dfc.common.gen.dot.DotEmitter;
import com.ishland.c2me.opts.dfc.common.gen.dot.DotGenRegistry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGenRegistry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.emitters.misc.DelegateNodeBytecodeEmitter;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Optional C2ME API linkage; load only when the exact DFC module is present. */
public final class C2meIslandCompiler {
  private C2meIslandCompiler() {}

  public static void register() {
    McToAst.REGISTRY.registerExactMatch(IslandTerrain.class, C2meIslandCompiler::node);
    BytecodeGenRegistry.REGISTRY.registerExactMatch(
        IslandTerrainNode.class, DelegateNodeBytecodeEmitter.<IslandTerrainNode>instance());
    DotGenRegistry.REGISTRY.registerExactMatch(
        IslandTerrainNode.class,
        (DotEmitter<IslandTerrainNode>)
            (node, context, builder) ->
                DotGenRegistry.doDotGen(new DelegateNode(node.getDelegate()), context, builder));
  }

  public static IslandTerrainNode node(IslandTerrain terrain) {
    return new IslandTerrainNode(
        terrain,
        new AstNode[] {
          McToAst.toAst(terrain.original()),
          McToAst.toAst(terrain.lower()),
          McToAst.toAst(terrain.continents()),
          McToAst.toAst(terrain.form()),
          McToAst.toAst(terrain.caves())
        });
  }

  static IslandTerrain compile(IslandTerrain terrain, AstNode[] children) {
    DensityFunction[] sources = {
      terrain.original(), terrain.lower(), terrain.continents(), terrain.form(), terrain.caves()
    };
    String[] names = {
      "island_original", "island_lower", "island_continents", "island_form", "island_caves"
    };
    BytecodeGen.Context context = BytecodeGen.initContext();
    int[] roots = new int[sources.length];
    for (int index = 0; index < sources.length; index++)
      if (index != 2)
        roots[index] =
            context.registerRoot(names[index], OptoPasses.optimize(children[index]).optimized());
    CompiledEntry entry = BytecodeGen.finalizeCompilation(context);
    DensityFunction[] compiled = new DensityFunction[sources.length];
    for (int index = 0; index < sources.length; index++) {
      // Continentalness fingerprints the layout using exact double bits. Keep that
      // seeded source intact; a tiny compiler/native rounding change is a new seed.
      compiled[index] =
          index == 2
              ? sources[index]
              : new BoundedCompiledDensity(
                  sources[index], new CompiledAstDensity(sources[index], entry, roots[index]));
    }
    return new IslandTerrain(compiled[0], compiled[1], compiled[2], compiled[3], compiled[4]);
  }
}
