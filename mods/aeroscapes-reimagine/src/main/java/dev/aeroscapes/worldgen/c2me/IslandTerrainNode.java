package dev.aeroscapes.worldgen.c2me;

import com.google.common.base.Suppliers;
import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.AstTransformer;
import com.ishland.c2me.opts.dfc.common.ast.misc.DelegateNode;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Island geometry shared by the CPU delegate and the independently registered GPU emitter. */
public final class IslandTerrainNode extends DelegateNode {
  private final IslandTerrain terrain;
  private final AstNode[] children;
  private final Supplier<DensityFunction> compiled;

  public IslandTerrainNode(IslandTerrain terrain, AstNode[] children) {
    super(terrain);
    if (children.length != 5)
      throw new IllegalArgumentException("Expected five island child graphs");
    this.terrain = terrain;
    this.children = children.clone();
    compiled = Suppliers.memoize(() -> C2meIslandCompiler.compile(terrain, this.children));
  }

  @Override
  public DensityFunction getDelegate() {
    return compiled.get();
  }

  public IslandTerrain terrain() {
    return terrain;
  }

  public AstNode original() {
    return children[0];
  }

  public AstNode lower() {
    return children[1];
  }

  public AstNode continents() {
    return children[2];
  }

  public AstNode form() {
    return children[3];
  }

  public AstNode caves() {
    return children[4];
  }

  @Override
  public AstNode[] getChildren() {
    return children.clone();
  }

  @Override
  public AstNode transform(AstTransformer transformer) {
    AstNode[] mapped = new AstNode[children.length];
    for (int index = 0; index < children.length; index++)
      mapped[index] = children[index].transform(transformer);
    return transformer.transform(
        Arrays.equals(children, mapped) ? this : new IslandTerrainNode(terrain, mapped));
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof IslandTerrainNode node
            && terrain == node.terrain
            && Arrays.equals(children, node.children);
  }

  @Override
  public int hashCode() {
    return 31 * System.identityHashCode(terrain) + Arrays.hashCode(children);
  }

  @Override
  public boolean relaxedEquals(AstNode other) {
    if (!(other instanceof IslandTerrainNode node) || terrain != node.terrain) return false;
    for (int index = 0; index < children.length; index++)
      if (!children[index].relaxedEquals(node.children[index])) return false;
    return true;
  }

  @Override
  public int relaxedHashCode() {
    int hash = System.identityHashCode(terrain);
    for (AstNode child : children) hash = 31 * hash + child.relaxedHashCode();
    return hash;
  }
}
