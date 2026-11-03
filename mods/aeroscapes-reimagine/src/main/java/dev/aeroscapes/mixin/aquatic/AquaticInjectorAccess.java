package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.worldgen.BiomeSourceDelegate;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(
    targets = "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource",
    remap = false)
public interface AquaticInjectorAccess extends BiomeSourceDelegate {
  @Override
  @Accessor("directDelegate")
  BiomeSource aeroscapes$delegate();
}
