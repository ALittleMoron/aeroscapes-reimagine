package dev.aeroscapes.mixin.engineering;

import dev.aeroscapes.engineering.OilFields;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.world.OilChunksSavedData", remap = false)
public abstract class FiniteOilMixin {
  @Shadow Map<ChunkPos, Integer> chunks;
  @Shadow ServerLevel level;

  @Shadow
  public abstract void setChunkAmount(ChunkPos chunk, int amount);

  @Inject(method = "getBaseOilAmount", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private static void initialAmount(
      ServerLevel level, ChunkPos chunk, CallbackInfoReturnable<Integer> result) {
    result.setReturnValue(OilFields.initialAmount(level, chunk));
  }

  @Inject(
      method = "getChunkOilAmount(Lnet/minecraft/world/level/ChunkPos;)I",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private void remainingAmount(ChunkPos chunk, CallbackInfoReturnable<Integer> result) {
    Integer amount = chunks.get(chunk);
    if (amount == null) {
      amount = OilFields.initialAmount(level, chunk);
      setChunkAmount(chunk, amount);
    } else if (amount < 0 || amount > OilFields.MAXIMUM_AMOUNT) {
      amount = Math.clamp(amount, 0, OilFields.MAXIMUM_AMOUNT);
      setChunkAmount(chunk, amount);
    }
    result.setReturnValue(amount);
  }
}
