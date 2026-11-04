package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.fauna.FaunaHabitats;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.world.entity.animal.fish.Blobfish", remap = false)
public abstract class NaturalistBlobfishPressureMixin {
  @ModifyExpressionValue(
      method = "getDeepY",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The appearance/pressure boundary must agree with natural
  // spawning.
  private int aeroscapes$localPressureDepth(int nativeSea) {
    Entity fish = (Entity) (Object) this;
    return FaunaHabitats.waterline(nativeSea, fish.level(), fish.blockPosition());
  }
}
