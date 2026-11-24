package dev.aeroscapes.mixin.enchanting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.alfie.immersiveenchanting.loot.InjectAncientBookLootModifier;
import me.alfie.immersiveenchanting.loot.RemoveEnchantedBooksModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {InjectAncientBookLootModifier.class, RemoveEnchantedBooksModifier.class},
    remap = false)
abstract class NativeBookLootMixin {
  @Inject(method = "doApply", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$usePackBookSources(
      ObjectArrayList<ItemStack> loot,
      LootContext context,
      CallbackInfoReturnable<ObjectArrayList<ItemStack>> callback) {
    callback.setReturnValue(loot);
  }
}
