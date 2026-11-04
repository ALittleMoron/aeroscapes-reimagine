package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.fauna.FaunaLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Pack.class)
public abstract class FaunaPackDescriptionMixin {
  @Shadow
  public abstract String getId();

  @ModifyReturnValue(method = "getDescription", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the exact optional provider packs have literal
  // descriptions.
  private Component aeroscapes$localizeDescription(Component original) {
    boolean known =
        switch (getId()) {
          case "naturalist:custom_spawn_eggs" ->
              original.getString().equals("Naturalist's Spawn Eggs in 1.21.5+ style");
          case "mod/crittersandcompanions:resourcepacks/friendlyart" ->
              original
                  .getString()
                  .equals("An arachnophobia-friendly design for the Jumping Spider");
          default -> false;
        };
    return known
        ? FaunaLanguage.literal(original.getString()).withStyle(original.getStyle())
        : original;
  }
}
