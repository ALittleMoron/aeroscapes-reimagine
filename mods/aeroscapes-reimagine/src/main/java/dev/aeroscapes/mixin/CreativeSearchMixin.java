package dev.aeroscapes.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeSearchMixin {
  @Shadow private static CreativeModeTab selectedTab;
  @Shadow private EditBox searchBox;
  @Unique private boolean aeroscapes$searchPending;

  @Shadow
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Bound to the native private refresh method.
  private void refreshSearchResults() {
    throw new AssertionError();
  }

  @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native search otherwise joins on the render thread.
  private void aeroscapes$deferUnfinishedIndex(CallbackInfo callback) {
    aeroscapes$searchPending = false;
    if (!selectedTab.hasSearchBar() || searchBox.getValue().isEmpty()) return;
    var index =
        searchBox.getValue().startsWith("#")
            ? CreativeModeTabSearchRegistry.getTagSearchTree(
                CreativeModeTabSearchRegistry.getTagSearchKey(selectedTab))
            : CreativeModeTabSearchRegistry.getNameSearchTree(
                CreativeModeTabSearchRegistry.getNameSearchKey(selectedTab));
    if (!index.isDone()) {
      aeroscapes$searchPending = true;
      callback.cancel();
    }
  }

  @Inject(method = "containerTick", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retry the current query when its native index is ready.
  private void aeroscapes$retrySearch(CallbackInfo callback) {
    if (aeroscapes$searchPending) refreshSearchResults();
  }
}
