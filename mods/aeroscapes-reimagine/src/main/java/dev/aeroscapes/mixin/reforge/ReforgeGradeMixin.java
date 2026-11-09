package dev.aeroscapes.mixin.reforge;

import com.herobrot.tieredneo.api.ModifierUtils;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModifierUtils.class, remap = false)
abstract class ReforgeGradeMixin {
  @Inject(method = "getRandomAttributeIDFor", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$chooseQualityBeforeProfile(
      Player player, Item item, boolean paid, CallbackInfoReturnable<ResourceLocation> result) {
    var profile = ReforgeProfiles.roll(item, paid);
    if (profile != null) result.setReturnValue(profile);
  }
}
