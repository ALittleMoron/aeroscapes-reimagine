package dev.aeroscapes.mixin.atmosphere;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay", remap = false)
public abstract class PuzzleLoadingOverlayMixin {}
