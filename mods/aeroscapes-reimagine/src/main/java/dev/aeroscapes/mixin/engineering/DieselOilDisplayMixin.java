package dev.aeroscapes.mixin.engineering;

import com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity;
import com.jesz.createdieselgenerators.content.pumpjack.PumpjackOilAmountDisplaySource;
import com.jesz.createdieselgenerators.world.OilChunksSavedData;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PumpjackOilAmountDisplaySource.class, remap = false)
public abstract class DieselOilDisplayMixin {
  @Inject(method = "provideLine", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private void currentReserve(
      DisplayLinkContext context,
      DisplayTargetStats stats,
      CallbackInfoReturnable<MutableComponent> result) {
    if (context.getSourceBlockEntity() instanceof PumpjackHoleBlockEntity pump
        && pump.getLevel() instanceof ServerLevel level) {
      int amount = OilChunksSavedData.getChunkOilAmount(level, new ChunkPos(pump.getBlockPos()));
      result.setReturnValue(
          Component.translatable("createdieselgenerators.gui.oil_reserve", amount));
    }
  }
}
