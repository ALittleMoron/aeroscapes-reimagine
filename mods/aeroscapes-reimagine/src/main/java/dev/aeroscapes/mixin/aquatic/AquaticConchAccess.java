package dev.aeroscapes.mixin.aquatic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "dev.hybridlabs.aquatic.item.OminousConchItem", remap = false)
public interface AquaticConchAccess {
  @Invoker("spawnAfterCooldown")
  void aeroscapes$spawnAfterCooldown(ServerLevel level, BlockPos position, int startTick);
}
