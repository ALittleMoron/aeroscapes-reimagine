package dev.aeroscapes.mixin.workshop;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.block.entity.BaseSmithingAnvilBlockEntity", remap = false)
public interface ForgePlayerAccess {
  @Invoker("getPlayer")
  Player aeroscapes$forgePlayer();

  @Invoker("setPlayer")
  void aeroscapes$forgePlayer(Player player);
}
