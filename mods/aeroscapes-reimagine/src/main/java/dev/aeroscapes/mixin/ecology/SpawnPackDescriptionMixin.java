package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Pack.class)
public abstract class SpawnPackDescriptionMixin {
  @Shadow
  public abstract String getId();

  @ModifyReturnValue(method = "getDescription", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // These two provider metadata literals bypass language resources.
  private Component aeroscapes$description(Component original) {
    String key =
        switch (getId()) {
          case "builtin/spawn/respawned" ->
              original.getString().equals("§7A subjective update for some Minecraft mobs")
                  ? "aeroscapes_reimagine.pack.spawn.respawned"
                  : null;
          case "builtin/spawn/respawned_eggs" ->
              original.getString().equals("§71.21.5 styled Spawn Eggs for Spawn")
                  ? "aeroscapes_reimagine.pack.spawn.respawned_eggs"
                  : null;
          default -> null;
        };
    return key == null ? original : Component.translatable(key).withStyle(ChatFormatting.GRAY);
  }
}
