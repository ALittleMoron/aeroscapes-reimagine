package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "me.pieking1215.invmove.module.config.ConfigEnum", remap = false)
public abstract class InvMoveEnumLanguageMixin {
  @ModifyExpressionValue(
      method = {
        "addTo(Lme/shedaniel/clothconfig2/api/ConfigCategory;Lme/shedaniel/clothconfig2/api/ConfigEntryBuilder;Ljava/lang/String;)V",
        "addTo(Lme/shedaniel/clothconfig2/impl/builders/SubCategoryBuilder;Lme/shedaniel/clothconfig2/api/ConfigEntryBuilder;Ljava/lang/String;)V"
      },
      require = 2,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lme/shedaniel/clothconfig2/api/ConfigEntryBuilder;startEnumSelector(Lnet/minecraft/network/chat/Component;Ljava/lang/Class;Ljava/lang/Enum;)Lme/shedaniel/clothconfig2/impl/builders/EnumSelectorBuilder;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native enum serialization and reset identities remain intact.
  private static <T extends Enum<?>> EnumSelectorBuilder<T> aeroscapes$translateEnum(
      EnumSelectorBuilder<T> builder) {
    return builder.setEnumNameProvider(
        value ->
            Component.literal(
                ConfigLiteralTranslations.translate(
                    value.name(), Language.getInstance()::getOrDefault)));
  }
}
