package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.DieselReforges;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.content.tools.OilScannerItem", remap = false)
abstract class DieselScannerReforgeMixin {
  @ModifyConstant(method = "use", constant = @Constant(intValue = 20), allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$scanPreparation(
      int duration, Level level, Player player, InteractionHand hand) {
    return DieselReforges.scanDuration(player.getItemInHand(hand), duration);
  }

  @ModifyArg(
      method = "inventoryTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
      index = 0,
      require = 4,
      allow = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Packet<?> aeroscapes$clearNativeReading(
      Packet<?> packet,
      @Local(argsOnly = true) ItemStack scanner,
      @Local(argsOnly = true) Entity user,
      @Local ChunkPos chunk,
      @Local(name = "amount") int amount) {
    var message = DieselReforges.scanMessage(scanner, amount, user, chunk);
    return message == null ? packet : new ClientboundSetActionBarTextPacket(message);
  }
}
