package dev.aeroscapes.worldgen.c2me;

import com.ishland.c2me.opts.dfc.common.ducks.IBlendingAwareVisitor;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Runtime-only compiled child with the semantic bounds of its seeded source graph. */
public final class BoundedCompiledDensity implements DensityFunction {
  private final DensityFunction source;
  private final DensityFunction compiled;
  private final double minimum;
  private final double maximum;

  public BoundedCompiledDensity(DensityFunction source, DensityFunction compiled) {
    this.source = source;
    this.compiled = compiled;
    minimum = source.minValue();
    maximum = source.maxValue();
  }

  @Override
  public double compute(FunctionContext context) {
    return compiled.compute(context);
  }

  @Override
  public void fillArray(double[] values, ContextProvider provider) {
    compiled.fillArray(values, provider);
  }

  @Override
  public DensityFunction mapAll(Visitor visitor) {
    // Blending maps back to the raw graph, including its visited root and new
    // bounds (BlendAlpha/BlendOffset widen inside NoiseChunk). Do not rewrap it.
    if (visitor instanceof IBlendingAwareVisitor blending && blending.c2me$isBlendingEnabled())
      return compiled.mapAll(visitor);
    return visitor.apply(new BoundedCompiledDensity(source, compiled.mapAll(visitor)));
  }

  @Override
  public double minValue() {
    return minimum;
  }

  @Override
  public double maxValue() {
    return maximum;
  }

  @Override
  public KeyDispatchDataCodec<? extends DensityFunction> codec() {
    // Like C2ME's compiled functions, this is a runtime cache. The retained source
    // graph and registered generator remain the serialization boundary.
    throw new UnsupportedOperationException("Compiled island children are runtime-only");
  }
}
