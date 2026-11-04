package dev.aeroscapes.mixin.ecology;

import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.ninni.spawn.server.block.SpawnFeastBlock")
public abstract class SpawnFeastCompatibilityMixin {
  @Redirect(
      method = "takeServing",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lvectorwing/farmersdelight/common/registry/ModSounds;BLOCK_FOOD_TAKE_PORTION:Ljava/util/function/Supplier;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Match the installed Farmer's Delight feast sound.
  private static Supplier<SoundEvent> aeroscapes$portionSound() {
    return () -> SoundEvents.ARMOR_EQUIP_GENERIC.value();
  }
}
