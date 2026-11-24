package dev.aeroscapes.mixin.enchanting;

import dev.aeroscapes.enchanting.LibraryBooks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
abstract class LibraryVillagerTradesMixin {
  @Inject(method = "getOffers", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$limitBookPrices(CallbackInfoReturnable<MerchantOffers> callback) {
    callback.getReturnValue().removeIf(offer -> aeroscapes$invalidBookTrade(offer.getResult()));
  }

  @Unique
  private static boolean aeroscapes$invalidBookTrade(ItemStack stack) {
    if (LibraryBooks.isAncient(stack)) return true;
    if (!stack.is(Items.ENCHANTED_BOOK)) return false;
    var prior = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    var capped = new ItemEnchantments.Mutable(prior);
    for (var entry : prior.entrySet())
      capped.set(entry.getKey(), LibraryBooks.ordinaryCeiling(entry.getKey(), entry.getIntValue()));
    var result = capped.toImmutable();
    if (result.isEmpty()) return true;
    if (!result.equals(prior)) stack.set(DataComponents.STORED_ENCHANTMENTS, result);
    return false;
  }
}
