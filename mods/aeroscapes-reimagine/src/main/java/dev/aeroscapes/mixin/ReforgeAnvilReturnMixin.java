package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.herobrot.tieredneo.network.TieredServerPacketHandler", remap = false)
abstract class ReforgeAnvilReturnMixin {
  @Inject(method = "lambda$handleReforgeRequest$4", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$rejectStaleMenu(IPayloadContext context, CallbackInfo callback) {
    var player = context.player();
    if (!player.containerMenu.stillValid(player)) callback.cancel();
  }

  @WrapOperation(
      method = "lambda$handleReforgeScreen$3",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerPlayer;openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;"))
  @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
  private static OptionalInt aeroscapes$openBlockAnvil(
      ServerPlayer player,
      MenuProvider vanillaProvider,
      Operation<OptionalInt> original,
      @Local BlockPos position) {
    if (!player.canInteractWithBlock(position, 4.0)) return OptionalInt.empty();
    var level = player.level();
    var state = level.getBlockState(position);
    if (!state.is(BlockTags.ANVIL)) return OptionalInt.empty();
    // Easy Anvils keeps its inputs in the block's menu provider.
    var provider = state.getMenuProvider(level, position);
    return provider == null ? OptionalInt.empty() : original.call(player, provider);
  }
}
