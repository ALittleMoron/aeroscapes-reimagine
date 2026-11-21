package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.AeroscapesReimagine;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SwitchGrid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds the per-world starting island choice to the vanilla World tab. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldCreationIslandMixin {
  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$Builder;build(Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Adds a switch before native layout and narration.
  private SwitchGrid aeroscapesReimagine$addMountainIslandSwitch(
      SwitchGrid.Builder builder,
      Consumer<LayoutElement> consumer,
      Operation<SwitchGrid> original,
      CreateWorldScreen screen) {
    var state = screen.getUiState();
    var rule = AeroscapesReimagine.LARGE_STARTING_ISLAND;
    BooleanSupplier supportsIslands =
        () -> {
          var preset = state.getWorldType().preset();
          return (preset == null || !preset.is(WorldPresets.FLAT))
              && state.getSettings().selectedDimensions().overworld()
                  instanceof NoiseBasedChunkGenerator;
        };
    state.addListener(
        changed -> {
          if (!supportsIslands.getAsBoolean())
            changed.getGameRules().getRule(rule).set(false, null);
        });
    builder
        .addSwitch(
            Component.translatable(rule.getDescriptionId()),
            () -> state.getGameRules().getBoolean(rule),
            enabled -> state.getGameRules().getRule(rule).set(enabled, null))
        .withIsActiveCondition(supportsIslands)
        .withInfo(Component.translatable(rule.getDescriptionId() + ".description"));
    return original.call(builder, consumer);
  }
}
