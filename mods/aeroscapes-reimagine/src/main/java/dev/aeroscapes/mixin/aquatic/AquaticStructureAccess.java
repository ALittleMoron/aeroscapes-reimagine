package dev.aeroscapes.mixin.aquatic;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Structure.class)
public interface AquaticStructureAccess {
  @Invoker("findGenerationPoint")
  Optional<Structure.GenerationStub> aeroscapes$assemble(Structure.GenerationContext context);
}
