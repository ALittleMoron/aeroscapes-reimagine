package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.hybridlabs.aquatic.item.ArgonautItem", remap = false)
public abstract class ArgonautColorMixin {
  @WrapOperation(
      method = "appendHoverText",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native enum names are serialized identities, not display labels.
  private MutableComponent aeroscapes$colorName(
      String key, Object[] arguments, Operation<MutableComponent> original) {
    if (("tooltip.hybrid_aquatic.argonaut.shell".equals(key)
            || "tooltip.hybrid_aquatic.argonaut.sail".equals(key))
        && arguments.length == 1
        && arguments[0] instanceof String color) {
      arguments =
          new Object[] {
            Component.translatable(
                "aeroscapes_reimagine.aquatic.color." + color.toLowerCase(Locale.ROOT))
          };
    }
    return original.call(key, arguments);
  }
}
