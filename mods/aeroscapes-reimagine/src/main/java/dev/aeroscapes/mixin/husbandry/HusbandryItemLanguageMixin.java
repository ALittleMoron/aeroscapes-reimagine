package dev.aeroscapes.mixin.husbandry;

import dev.aeroscapes.husbandry.HusbandryLanguage;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
    targets = {
      "org.athisworkshop.animalhusbandry.item.BrushItem",
      "org.athisworkshop.animalhusbandry.item.AnimalMedicineItem",
      "org.athisworkshop.animalhusbandry.item.FertilityPotionItem",
      "org.athisworkshop.animalhusbandry.item.MagnifyingGlassItem",
      "org.athisworkshop.animalhusbandry.item.FarmLedgerItem"
    },
    remap = false)
abstract class HusbandryItemLanguageMixin {
  @Redirect(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private static MutableComponent aeroscapes$localizeMessage(String source) {
    return HusbandryLanguage.literal(source);
  }
}
