package dev.aeroscapes.mixin;

import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import dev.aeroscapes.qualityoflife.CompassSearch;
import dev.aeroscapes.qualityoflife.CompassSearchNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExplorersCompassItem.class, remap = false)
public abstract class CompassExplorerItemMixin {
  @Inject(method = "searchForStructure", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$legacyFreeSearch(
      Level level,
      Player player,
      ResourceLocation key,
      List<ResourceLocation> ignoredMembers,
      BlockPos ignoredPosition,
      ItemStack ignoredStack,
      boolean group,
      CallbackInfo callback) {
    if (player instanceof ServerPlayer serverPlayer) {
      CompassSearch.request(
          serverPlayer, new CompassSearchNetwork.Request(false, key, group, false, false));
      callback.cancel();
    }
  }

  @Inject(method = "searchForNextStructure", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$legacyFreeNext(
      ServerLevel level,
      Player player,
      BlockPos ignoredPosition,
      ItemStack ignoredStack,
      CallbackInfo callback) {
    if (player instanceof ServerPlayer serverPlayer) {
      CompassSearch.request(
          serverPlayer,
          new CompassSearchNetwork.Request(
              false, ResourceLocation.withDefaultNamespace("empty"), false, true, false));
      callback.cancel();
    }
  }

  @Inject(method = "use", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$stopOnReset(
      Level level,
      Player player,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
    if (player instanceof ServerPlayer serverPlayer && player.isCrouching())
      CompassSearch.cancel(serverPlayer);
  }

  @Inject(method = "setSearchRadius", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned compass injection.
  private void aeroscapes$displayLimit(
      ItemStack stack, int radius, Player player, CallbackInfo callback) {
    int clamped = CompassSearch.displayRadius(player, stack, radius);
    if (clamped != radius) {
      stack.set(
          com.chaosthedude.explorerscompass.ExplorersCompass.SEARCH_RADIUS_COMPONENT, clamped);
      callback.cancel();
    }
  }
}
