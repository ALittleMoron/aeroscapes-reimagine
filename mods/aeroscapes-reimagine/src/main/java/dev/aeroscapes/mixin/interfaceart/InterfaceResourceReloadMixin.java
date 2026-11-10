package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.InterfaceArt;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public abstract class InterfaceResourceReloadMixin {
  @Shadow @Final private PackType type;

  @Inject(method = "createReload", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$refreshInterfaceArt(CallbackInfoReturnable<ReloadInstance> callback) {
    // The manager has opened the newly selected packs before the loading overlay is constructed.
    if (type == PackType.CLIENT_RESOURCES) {
      InterfaceArt.refresh((ResourceManager) this);
    }
  }
}
