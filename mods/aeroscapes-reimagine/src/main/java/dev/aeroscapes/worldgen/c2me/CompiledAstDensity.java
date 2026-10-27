package dev.aeroscapes.worldgen.c2me;

import com.google.common.base.Suppliers;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ducks.IBlendingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.ducks.ICompiledCachingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.gen.jvm.AbstractCompiledDensityFunction;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.SubCompiledDensityFunction;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.IMultiMethod;
import com.ishland.c2me.opts.dfc.common.gen.jvm.internalapi.ISingleMethod;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.EachApplierVanillaInterface;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Public C2ME root handles with native per-NoiseChunk argument mapping and blending fallback. */
final class CompiledAstDensity extends AbstractCompiledDensityFunction {
  private final CompiledEntry entry;
  private final int rootIndex;
  private final ISingleMethod single;
  private final IMultiMethod multi;

  CompiledAstDensity(DensityFunction source, CompiledEntry entry, int rootIndex) {
    this(entry, rootIndex, Suppliers.ofInstance(source));
  }

  private CompiledAstDensity(
      CompiledEntry entry, int rootIndex, Supplier<DensityFunction> fallback) {
    super(fallback);
    this.entry = entry;
    this.rootIndex = rootIndex;
    SubCompiledDensityFunction root = entry.getRootsUnsafe()[rootIndex];
    single = root.getSingleMethod();
    multi = root.getMultiMethod();
  }

  @Override
  public ISingleMethod getSingleMethod() {
    return single;
  }

  @Override
  public IMultiMethod getMultiMethod() {
    return multi;
  }

  @Override
  public double compute(FunctionContext context) {
    // An outer C2ME DelegateNode wraps NoiseChunk in NoisePosVanillaInterface.
    // The inherited EvalType.from(context) only recognizes NoiseChunk, so a
    // nested compiled child would silently switch INTERPOLATION to NORMAL.
    // Keep the wrapper's mode; its borrowed position has no public cache handle,
    // and Noop avoids borrowing the outer call's singleton a second time.
    if (context instanceof NoisePosVanillaInterface position)
      return single.evalSingle(
          position.blockX(),
          position.blockY(),
          position.blockZ(),
          position.getType(),
          DfcObjectCache.Noop.INSTANCE);
    return super.compute(context);
  }

  @Override
  public void fillArray(double[] values, ContextProvider provider) {
    // Native interpolator multi-cache lookup only serves cell prefill. An
    // EachApplier can also represent ordinary INTERPOLATION samples; its native
    // multi miss would evaluate un-interpolated noise instead of the live value.
    // Keep those synthetic batches scalar. Native NoiseChunk prefill and NORMAL
    // batches still use the compiled multi method below.
    if (provider instanceof EachApplierVanillaInterface applier
        && applier.getType() == EvalType.INTERPOLATION) {
      provider.fillAllDirectly(values, this);
      return;
    }
    super.fillArray(values, provider);
  }

  @Override
  public DensityFunction mapAll(Visitor visitor) {
    if (visitor instanceof IBlendingAwareVisitor blending && blending.c2me$isBlendingEnabled())
      return Objects.requireNonNull(getFallback()).mapAll(visitor);
    var argumentVisitor = ICompiledCachingAwareVisitor.c2me$getArgumentVisitor(visitor);
    CompiledEntry mapped =
        visitor instanceof ICompiledCachingAwareVisitor caching
            ? caching.c2me$visitIfAbsent(entry, argumentVisitor)
            : entry.newInstance(entry.getArgs(), argumentVisitor);
    return new CompiledAstDensity(
        mapped,
        rootIndex,
        Suppliers.memoize(() -> Objects.requireNonNull(getFallback()).mapAll(visitor)));
  }
}
