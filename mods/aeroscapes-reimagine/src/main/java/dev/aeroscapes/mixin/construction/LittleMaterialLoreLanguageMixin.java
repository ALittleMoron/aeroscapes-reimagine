package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "team.creative.littletiles.common.gui.LittleGuiUtils$LittleBlockCollector",
    remap = false)
public abstract class LittleMaterialLoreLanguageMixin {
  @ModifyArg(
      method =
          "collect(Lnet/minecraft/world/entity/player/Player;)Lteam/creative/creativecore/common/util/type/map/HashMapList;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0,
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native material-volume lore bypasses language keys.
  private static String aeroscapes$translateVolume(String text) {
    return ConstructionLanguage.literal(text);
  }
}
