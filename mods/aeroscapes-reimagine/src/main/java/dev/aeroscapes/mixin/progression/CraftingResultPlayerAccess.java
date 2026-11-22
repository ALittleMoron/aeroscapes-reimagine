package dev.aeroscapes.mixin.progression;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResultSlot.class)
public interface CraftingResultPlayerAccess {
  @Accessor("player")
  Player aeroscapes$craftingPlayer();
}
