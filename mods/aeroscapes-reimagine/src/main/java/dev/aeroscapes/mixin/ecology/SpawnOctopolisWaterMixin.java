package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.SpawnWaterline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "com.ninni.spawn.server.level.structure.OctopolisProcessor", remap = false)
public abstract class SpawnOctopolisWaterMixin {
  @ModifyConstant(method = "process", constant = @Constant(intValue = 63), require = 6)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain all six native strict water/coral/waterlogging
  // comparisons.
  private int aeroscapes$localTemplateWater(
      int original,
      LevelReader level,
      BlockPos origin,
      BlockPos pivot,
      StructureTemplate.StructureBlockInfo template,
      StructureTemplate.StructureBlockInfo transformed,
      StructurePlaceSettings settings,
      StructureTemplate structureTemplate) {
    return level instanceof ServerLevelAccessor server
        ? SpawnWaterline.at(server, transformed.pos())
        : original;
  }
}
