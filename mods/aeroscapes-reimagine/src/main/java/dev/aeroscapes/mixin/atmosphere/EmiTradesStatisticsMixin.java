package dev.aeroscapes.mixin.atmosphere;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "moe.prwk.emitrades.recipes.TradeEntry", remap = false)
public abstract class EmiTradesStatisticsMixin {
  @Shadow @Final private Optional<Integer> villagerXp;
  @Shadow @Final private Optional<Integer> maxUses;

  @Redirect(
      method = "addWidgets",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
              ordinal = 0))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact provider hook.
  private MutableComponent aeroscapes$villagerExperience(String source) {
    return Component.translatable(
        "aeroscapes_reimagine.emitrades.villager_xp", villagerXp.orElseThrow());
  }

  @Redirect(
      method = "addWidgets",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
              ordinal = 1))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact provider hook.
  private MutableComponent aeroscapes$maximumUses(String source) {
    return Component.translatable("aeroscapes_reimagine.emitrades.max_uses", maxUses.orElseThrow());
  }
}
