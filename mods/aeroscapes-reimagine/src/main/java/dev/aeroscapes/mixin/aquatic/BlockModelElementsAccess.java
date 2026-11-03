package dev.aeroscapes.mixin.aquatic;

import java.util.List;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockModel.class)
public interface BlockModelElementsAccess {
  @Accessor("elements")
  List<BlockElement> aeroscapes$elements();
}
