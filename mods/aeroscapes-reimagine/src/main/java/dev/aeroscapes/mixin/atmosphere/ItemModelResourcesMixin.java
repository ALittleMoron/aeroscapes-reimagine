package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.ItemModelResources;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiPackResourceManager.class)
public abstract class ItemModelResourcesMixin {
  @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep all packs owned by the manager's close lifecycle.
  private static List<PackResources> aeroscapes$nativeItems(
      List<PackResources> packs, @Local(argsOnly = true) PackType type) {
    return ItemModelResources.prepare(type, packs);
  }
}
