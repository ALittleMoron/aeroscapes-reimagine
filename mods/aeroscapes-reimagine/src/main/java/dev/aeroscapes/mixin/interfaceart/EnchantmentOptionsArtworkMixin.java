package dev.aeroscapes.mixin.interfaceart;

import dev.aeroscapes.interfaceart.ContainerArtwork;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantmentScreen.class)
abstract class EnchantmentOptionsArtworkMixin {
  @Unique
  private static final ResourceLocation aeroscapes$enchantmentSlot =
      ResourceLocation.withDefaultNamespace(
          "textures/gui/sprites/container/enchanting_table/enchantment_slot.png");

  @Unique
  private static final ResourceLocation aeroscapes$disabledEnchantmentSlot =
      ResourceLocation.withDefaultNamespace(
          "textures/gui/sprites/container/enchanting_table/enchantment_slot_disabled.png");

  @ModifyArg(
      method = "renderBg",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V"),
      index = 5,
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$enchantmentRunes(int original) {
    if (original == 6839882 && ContainerArtwork.usesPanel(aeroscapes$enchantmentSlot)) {
      return ContainerArtwork.LABEL_COLOR;
    }
    if (original == ((6839882 & 16711422) >> 1)
        && ContainerArtwork.usesPanel(aeroscapes$disabledEnchantmentSlot)) {
      return 0xa6b6c5;
    }
    return original;
  }

  @ModifyArg(
      method = "renderBg",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$unavailableLevel(int original) {
    return original == 4226832 && ContainerArtwork.usesPanel(aeroscapes$disabledEnchantmentSlot)
        ? 0x98ac79
        : original;
  }
}
