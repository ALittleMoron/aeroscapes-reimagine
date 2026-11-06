package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionMessages;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
    targets = "team.creative.littletiles.common.packet.action.BlockPacket$BlockPacketAction$1",
    remap = false)
public abstract class LittleGridMessageMixin {
  @Redirect(
      method =
          "action(Lnet/minecraft/world/level/Level;Lteam/creative/littletiles/common/block/entity/BETiles;Lteam/creative/littletiles/common/block/little/tile/LittleTileContext;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native enum-constant action sends the grid to players.
  private static MutableComponent aeroscapes$translateGrid(String source) {
    return ConstructionMessages.literal(source);
  }
}
