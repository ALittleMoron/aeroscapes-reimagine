package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "net.clayborn.accurateblockplacement.config.AccurateBlockPlacementConfigScreen",
    remap = false)
@SuppressWarnings("PMD.UnusedPrivateMethod")
public abstract class AccuratePlacementEnumLanguageMixin {
  @ModifyExpressionValue(
      method = "createConfigScreen",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lme/shedaniel/clothconfig2/api/ConfigEntryBuilder;startEnumSelector(Lnet/minecraft/network/chat/Component;Ljava/lang/Class;Ljava/lang/Enum;)Lme/shedaniel/clothconfig2/impl/builders/EnumSelectorBuilder;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned placement injection.
  private static <T extends Enum<?>> EnumSelectorBuilder<T> aeroscapes$translatedLabels(
      EnumSelectorBuilder<T> builder) {
    return builder.setEnumNameProvider(
        value ->
            Component.translatable(
                "text.autoconfig.accurateblockplacement.option.confirmationType." + value.name()));
  }
}
