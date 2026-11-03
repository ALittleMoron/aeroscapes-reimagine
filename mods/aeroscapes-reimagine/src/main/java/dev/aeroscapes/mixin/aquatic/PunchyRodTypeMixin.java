package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "punchy.client.render.FishingLineAttachment", remap = false)
public abstract class PunchyRodTypeMixin {
  @ModifyExpressionValue(
      method = {"begin", "capture", "getWorldAnchor"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"),
      require = 3)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The same native-rod guard appears at all three boundaries.
  private static boolean aeroscapes$rodFamily(boolean original, @Local ItemStack stack) {
    return original || stack.getItem() instanceof FishingRodItem;
  }
}
