package dev.aeroscapes.mixin.engineering.fuel;

import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.CDGRegistries;
import com.jesz.createdieselgenerators.content.tools.FueledToolItem;
import com.jesz.createdieselgenerators.content.tools.lighter.LighterItem;
import com.jesz.createdieselgenerators.content.tools.lighter.LighterState;
import com.jesz.createdieselgenerators.fuel_type.FuelType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LighterItem.class, remap = false)
public abstract class LighterFuelMixin {
  @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$checkFuelBeforeIgnition(
      UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
    var stack = context.getItemInHand();
    if (stack.get(CDGDataComponents.LIGHTER_STATE) != LighterState.OPEN_IGNITED) return;
    var fluid = ((FueledToolItem) this).readFluid(stack);
    if (fluid.isEmpty()
        || FuelType.getTypeFor(
                    context.getLevel().registryAccess().lookupOrThrow(CDGRegistries.FUEL_TYPE),
                    fluid.getFluid())
                .normal()
                .speed()
            == 0) {
      if (!context.getLevel().isClientSide)
        stack.set(CDGDataComponents.LIGHTER_STATE, LighterState.OPEN);
      callback.setReturnValue(InteractionResult.FAIL);
    }
  }
}
