package dev.aeroscapes.mixin.reforge.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.BuilderChippedReforges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "earth.terrarium.chipped.client.screens.WorkbenchScreen", remap = false)
abstract class BuilderChippedScreenMixin {
  @Shadow protected EditBox searchBox;

  @Shadow
  public abstract BlockState state();

  @Unique private ItemStack aeroscapes$chisel = ItemStack.EMPTY;
  @Unique private boolean aeroscapes$opened;
  @Unique private int aeroscapes$nextCut;
  @Unique private BlockState aeroscapes$pinnedState;
  @Unique private boolean aeroscapes$pinWholeStack;

  @Inject(method = "init", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberSetup(CallbackInfo callback) {
    var player = Minecraft.getInstance().player;
    if (player == null) return;
    if (!aeroscapes$opened) {
      aeroscapes$opened = true;
      aeroscapes$chisel = BuilderChippedReforges.opened(player);
    }
    switch (ReforgeProfiles.path(aeroscapes$chisel)) {
      case "builder_chisel_fine_focus" -> ((Screen) (Object) this).setFocused(searchBox);
      case "builder_chisel_exceptional_filter" ->
          searchBox.setValue(BuilderChippedReforges.filter(aeroscapes$chisel));
      case "builder_chisel_epic_setup", "builder_chisel_legendary_preview" -> {
        searchBox.setValue(BuilderChippedReforges.filter(aeroscapes$chisel));
        ((Screen) (Object) this).setFocused(searchBox);
      }
      default -> {}
    }
  }

  @Inject(method = "onSearchBarChanged", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberFilter(String query, CallbackInfo callback) {
    switch (ReforgeProfiles.path(aeroscapes$chisel)) {
      case "builder_chisel_exceptional_filter",
          "builder_chisel_epic_setup",
          "builder_chisel_legendary_preview" ->
          BuilderChippedReforges.setFilter(aeroscapes$chisel, query);
      default -> {}
    }
    aeroscapes$pinnedState = null;
  }

  @Inject(method = "slotClicked", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$clearPinOnNewInput(CallbackInfo callback) {
    aeroscapes$pinnedState = null;
  }

  @Inject(
      method = "craft",
      at =
          @At(
              value = "FIELD",
              target =
                  "Learth/terrarium/chipped/common/network/NetworkHandler;CHANNEL:Lcom/teamresourceful/resourcefullib/common/network/Network;"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$confirmPreview(CallbackInfo callback) {
    var player = Minecraft.getInstance().player;
    if (player == null) return;
    String rank = ReforgeProfiles.path(aeroscapes$chisel);
    if (rank.equals("builder_chisel_poor_stiff")) {
      if (player.tickCount < aeroscapes$nextCut) callback.cancel();
      else aeroscapes$nextCut = player.tickCount + 8;
      return;
    }
    if (!rank.equals("builder_chisel_legendary_preview")) return;
    BlockState state = state();
    boolean wholeStack = Screen.hasShiftDown();
    if (state == aeroscapes$pinnedState && wholeStack == aeroscapes$pinWholeStack) {
      aeroscapes$pinnedState = null;
      return;
    }
    aeroscapes$pinnedState = state;
    aeroscapes$pinWholeStack = wholeStack;
    callback.cancel();
  }

  @Inject(method = "render", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$previewHint(
      GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo callback) {
    if (aeroscapes$pinnedState == null) return;
    var screen = (Screen) (Object) this;
    graphics.drawCenteredString(
        Minecraft.getInstance().font,
        Component.translatable("aeroscapes_reimagine.equipment.builder.chisel_pinned"),
        screen.width / 2,
        Math.max(2, screen.height / 2 - 140),
        0xFFFFFFFF);
  }
}
