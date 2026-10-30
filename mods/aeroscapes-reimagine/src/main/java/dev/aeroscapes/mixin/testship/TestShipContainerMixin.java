package dev.aeroscapes.mixin.testship;

import dev.aeroscapes.testship.TestShipContainer;
import dev.aeroscapes.testship.TestShipController;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class TestShipContainerMixin implements TestShipContainer {
  @Unique private TestShipController aeroscapes$testShips;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Register before Sable appends its physics observer.
  private void aeroscapes$attach(CallbackInfo callback) {
    if ((Object) this instanceof ServerSubLevelContainer container) {
      aeroscapes$testShips = new TestShipController(container);
      container.addObserver(aeroscapes$testShips);
    }
  }

  @Override
  public TestShipController aeroscapes$testShips() {
    return aeroscapes$testShips;
  }
}
