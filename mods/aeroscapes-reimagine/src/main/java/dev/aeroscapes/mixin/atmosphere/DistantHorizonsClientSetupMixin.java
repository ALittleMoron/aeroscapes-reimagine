package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.neoforge.NeoforgeMain", remap = false)
public abstract class DistantHorizonsClientSetupMixin {
  @WrapOperation(
      method =
          "lambda$new$0(Lnet/neoforged/bus/api/IEventBus;Lnet/neoforged/fml/event/lifecycle/FMLClientSetupEvent;)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/seibel/distanthorizons/neoforge/NeoforgeMain;onInitializeClient()V"))
  @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
  private void aeroscapes$initializeOnRenderThread(
      @Coerce Object initializer,
      Operation<Void> original,
      IEventBus modBus,
      FMLClientSetupEvent event) {
    // Better Clouds registers DH listeners on this same thread during client construction.
    event.enqueueWork(
        () -> {
          original.call(initializer);
        });
  }
}
